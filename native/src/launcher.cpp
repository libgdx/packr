/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

#ifdef _WIN32
#include <windows.h>
#else
#include <dlfcn.h>
#endif

#include <launcher.h>
#include <stdio.h>
#include <jni.h>
#include <string>
#include <iostream>
#include <fstream>
#include <picojson.h>

const char kPathSeparator =
#ifdef _WIN32
'\\';
#else
'/';
#endif

extern std::string getExecutableDir();
extern bool changeWorkingDir(std::string dir);
extern int g_argc;
extern char** g_argv;

typedef jint (JNICALL *PtrCreateJavaVM)(JavaVM **, void **, void *);

void* launchVM(void* params) {
    std::string execDir = getExecutableDir();
    std::string configfilePath = execDir + kPathSeparator + "config.json";
    printf("config file: %s\n", configfilePath.c_str());

    std::ifstream configFile;
    configFile.open(configfilePath.c_str());
    
    picojson::value json;
    configFile >> json;
    std::string err = picojson::get_last_error();
    if(!err.empty()) {
        printf("Couldn't parse json: %s\n", err.c_str());
        exit(EXIT_FAILURE);
    }
    
    picojson::object rootObj = json.get<picojson::object>();


    std::string jarFile = execDir + kPathSeparator + rootObj["jar"].to_str();
    printf("jar: %s\n", jarFile.c_str());

    std::string main = rootObj["mainClass"].to_str();
    printf("mainClass: %s\n", main.c_str());

    picojson::array vmArgs = rootObj["vmArgs"].get<picojson::array>();
    
    std::string classPath = std::string("-Djava.class.path=") + jarFile;
    
    JavaVMOption* options = new JavaVMOption[vmArgs.size() + 1];
    options[0].optionString = strdup(classPath.c_str());
    for(unsigned i = 0; i < vmArgs.size(); i++) {
        options[i+1].optionString = strdup(vmArgs[i].to_str().c_str());
        printf("vmArg %d: %s\n", i, options[i+1].optionString);
    }
    
    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_6;
    args.nOptions = 1 + vmArgs.size();
    args.options = options;
    args.ignoreUnrecognized = JNI_FALSE;
    
    JavaVM* jvm = 0;
    JNIEnv* env = 0;

#ifndef _WIN32
    #ifdef MACOSX
        std::string jre = execDir + std::string("/jre/lib/server/libjvm.dylib");
    #elif defined(__LP64__)
        std::string jre = execDir + std::string("/jre/lib/amd64/server/libjvm.so");
    #else
        std::string jre = execDir + std::string("/jre/lib/i386/server/libjvm.so");
    #endif

    printf("jre: %s\n", jre.c_str());
    
    void* handle = dlopen(jre.c_str(), RTLD_LAZY);
    if(handle == NULL) {
        fprintf(stderr, "%s\n", dlerror());
        exit(EXIT_FAILURE);
    }
    PtrCreateJavaVM ptrCreateJavaVM = (PtrCreateJavaVM)dlsym(handle, "JNI_CreateJavaVM");
    if(ptrCreateJavaVM == NULL) {
        fprintf(stderr, "%s\n", dlerror());
        exit(EXIT_FAILURE);
    }
#else
    HINSTANCE hinstLib = LoadLibrary(TEXT("jre\\bin\\server\\jvm.dll"));
    PtrCreateJavaVM ptrCreateJavaVM = (PtrCreateJavaVM)GetProcAddress(hinstLib,"JNI_CreateJavaVM");
#endif
    
    if(!changeWorkingDir(execDir)) {
        fprintf(stderr, "Couldn't change working directory to: %s\n", execDir.c_str());
    }

    jint res = ptrCreateJavaVM(&jvm, (void**)&env, &args);
    if(res != JNI_OK) {
        fprintf(stderr, "Failed to create Java VM. Error: %d\n", res);
        exit(EXIT_FAILURE);
    }

    jobjectArray appArgs = env->NewObjectArray(g_argc, env->FindClass("java/lang/String"), NULL);
    for(int i = 0; i < g_argc; i++) {
        jstring arg = env->NewStringUTF(g_argv[i]);
        env->SetObjectArrayElement(appArgs, i, arg);
    }
    
    jclass mainClass = env->FindClass(main.c_str());
    if(mainClass == 0) {
        fprintf(stderr, "Failed to find class: %s:\n", main.c_str());
        exit(EXIT_FAILURE);
    }

    jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    if(mainMethod == 0) {
        fprintf(stderr, "Failed to aquire main() method of class: %s:\n", main.c_str());
        exit(EXIT_FAILURE);
    }
    env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);
    jvm->DestroyJavaVM();

    delete[] options;
    
    return 0;
}
