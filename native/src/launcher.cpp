#ifdef WINDOWS
#include <windows.h>
#endif

#include <launcher.h>
#include <stdio.h>
#include <jni.h>
#include <string>
#include <iostream>
#include <fstream>
#include <picojson.h>

extern std::string getExecutableDir();
extern int g_argc;
extern char** g_argv;

typedef jint (JNICALL *PtrCreateJavaVM)(JavaVM **, void **, void *);

void* launchVM(void* params) {
    std::string execDir = getExecutableDir();
    std::ifstream configFile;
    configFile.open((execDir + std::string("/config.json")).c_str());
    printf("config file: %s\n", (execDir + std::string("/config.json")).c_str());
    
    picojson::value json;
    configFile >> json;
    std::string err = picojson::get_last_error();
    if(!err.empty()) {
        printf("Couldn't parse json: %s\n", err.c_str());
    }
    
    std::string jarFile = execDir + std::string("/") + json.get<picojson::object>()["jar"].to_str();
    std::string main = json.get<picojson::object>()["mainClass"].to_str();
    std::string classPath = std::string("-Djava.class.path=") + jarFile;
    picojson::array vmArgs = json.get<picojson::object>()["vmArgs"].get<picojson::array>();
    printf("jar: %s\n", jarFile.c_str());
    printf("mainClass: %s\n", main.c_str());
    
    JavaVMOption* options = (JavaVMOption*)malloc(sizeof(JavaVMOption) * (1 + vmArgs.size()));
    options[0].optionString = (char*)classPath.c_str();
    for(unsigned i = 0; i < vmArgs.size(); i++) {
        options[i+1].optionString = (char*)vmArgs[i].to_str().c_str();
        printf("vmArg %d: %s\n", i, options[i+1].optionString);
    }
    
    JavaVMInitArgs args;
    args.version = JNI_VERSION_1_6;
    args.nOptions = 1 + vmArgs.size();
    args.options = options;
    args.ignoreUnrecognized = JNI_FALSE;
    
    JavaVM* jvm = 0;
    JNIEnv* env = 0;

#ifndef WINDOWS
    JNI_CreateJavaVM(&jvm, (void**)&env, &args);
#else
	HINSTANCE hinstLib = LoadLibrary(TEXT("jre\\bin\\server\\jvm.dll"));
	PtrCreateJavaVM ptrCreateJavaVM = (PtrCreateJavaVM)GetProcAddress(hinstLib,"JNI_CreateJavaVM");
	jint res = ptrCreateJavaVM(&jvm, (void**)&env, &args);
#endif

    jobjectArray appArgs = env->NewObjectArray(g_argc, env->FindClass("java/lang/String"), NULL);
    for(int i = 0; i < g_argc; i++) {
        jstring arg = env->NewStringUTF(g_argv[i]);
        env->SetObjectArrayElement(appArgs, i, arg);
    }
    
    jclass mainClass = env->FindClass(main.c_str());
    jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);
    jvm->DestroyJavaVM();
    return 0;
}
