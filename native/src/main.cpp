#include <stdio.h>
#include <jni.h>
#include <string>
#include <pthread.h>
#include <CoreFoundation/CoreFoundation.h>
#include <sys/param.h>

extern "C" { int _NSGetExecutablePath(char* buf, uint32_t* bufsize); }


std::string getExecutableDir() {
    char buf[MAXPATHLEN];
    uint32_t size = sizeof(buf);
    _NSGetExecutablePath(buf, &size);
    std::string path = std::string(buf);
    return path.substr(0, path.find_last_of('/'));
}

int g_argc;
char** g_argv;

static void* launchVM(void* params) {
    JavaVM* jvm = 0;
    JNIEnv* env = 0;
    JavaVMInitArgs args;
    JavaVMOption options[1];
    
    std::string execDir = getExecutableDir();
    std::string classPath = std::string("-Djava.class.path=") + execDir + std::string("/cuboc.jar");
    char* mainClassName = "com/badlogic/cubocy/CubocDesktop";
    printf("starting in %s\n", execDir.c_str());
    
    args.version = JNI_VERSION_1_6;
    args.nOptions = 1;
    options[0].optionString = (char*)classPath.c_str();
    args.options = options;
    args.ignoreUnrecognized = JNI_FALSE;
    
    JNI_CreateJavaVM(&jvm, (void**)&env, &args);
    
    jobjectArray appArgs = env->NewObjectArray(g_argc, env->FindClass("java/lang/String"), NULL);
    for(int i = 0; i < g_argc; i++) {
        jstring arg = env->NewStringUTF(g_argv[i]);
        env->SetObjectArrayElement(appArgs, i, arg);
    }
    
    jclass mainClass = env->FindClass(mainClassName);
    jmethodID mainMethod = env->GetStaticMethodID(mainClass, "main", "([Ljava/lang/String;)V");
    env->CallStaticVoidMethod(mainClass, mainMethod, appArgs);
    jvm->DestroyJavaVM();
}

void sourceCallBack (  void *info  ) {}

int main(int argc, char** argv) {
    CFRunLoopSourceContext sourceContext;
    pthread_t vmthread;
    struct rlimit limit;
    size_t stack_size = 0;
    int rc = getrlimit(RLIMIT_STACK, &limit);
    if (rc == 0) {
        if (limit.rlim_cur != 0LL) {
            stack_size = (size_t)limit.rlim_cur;
        }
    }
    
    pthread_attr_t thread_attr;
    pthread_attr_init(&thread_attr);
    pthread_attr_setscope(&thread_attr, PTHREAD_SCOPE_SYSTEM);
    pthread_attr_setdetachstate(&thread_attr, PTHREAD_CREATE_DETACHED);
    if (stack_size > 0) {
        pthread_attr_setstacksize(&thread_attr, stack_size);
    }
    pthread_create(&vmthread, &thread_attr, launchVM, 0);
    pthread_attr_destroy(&thread_attr);
    
    /* Create a a sourceContext to be used by our source that makes */
    /* sure the CFRunLoop doesn't exit right away */
    sourceContext.version = 0;
    sourceContext.info = NULL;
    sourceContext.retain = NULL;
    sourceContext.release = NULL;
    sourceContext.copyDescription = NULL;
    sourceContext.equal = NULL;
    sourceContext.hash = NULL;
    sourceContext.schedule = NULL;
    sourceContext.cancel = NULL;
    sourceContext.perform = &sourceCallBack;
    
    CFRunLoopSourceRef sourceRef = CFRunLoopSourceCreate (NULL, 0, &sourceContext);
    CFRunLoopAddSource (CFRunLoopGetCurrent(),sourceRef,kCFRunLoopCommonModes);
    CFRunLoopRun();
    
    return 0;
}