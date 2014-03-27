#include <stdio.h>
#include <jni.h>
#include <string>

#ifdef MACOSX
extern "C" { int _NSGetExecutablePath(char* buf, uint32_t* bufsize); }
#include <sys/param.h>
#endif

std::string getExecutableDir() {
#ifdef MACOSX
    char buf[MAXPATHLEN];
    uint32_t size = sizeof(buf);
    _NSGetExecutablePath(buf, &size);
    std::string path = std::string(buf);
    return path.substr(0, path.find_last_of('/'));
#endif
}


int main(int argc, char** argv) {
    JavaVM* jvm = 0;
    JNIEnv* env = 0;
    JavaVMInitArgs args;
    JavaVMOption options[1];
    
    std::string execDir = getExecutableDir();
    std::string classPath = std::string("-Djava.class.path=") + execDir;
    char* mainClassName = "Test";
    printf("starting in %s\n", execDir.c_str());
    
    args.version = JNI_VERSION_1_6;
    args.nOptions = 1;
    options[0].optionString = (char*)classPath.c_str();
    args.options = options;
    args.ignoreUnrecognized = JNI_FALSE;
    
    JNI_CreateJavaVM(&jvm, (void**)&env, &args);
    
    jobjectArray appArgs = env->NewObjectArray(argc, env->FindClass("java/lang/String"), NULL);
    for(int i = 0; i < argc; i++) {
        jstring arg = env->NewStringUTF(argv[i]);
        env->SetObjectArrayElement(appArgs, i, arg);
    }
    
    jclass mainClass = env->FindClass(mainClassName);
    jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);
}