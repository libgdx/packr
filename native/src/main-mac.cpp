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

#include <stdio.h>
#include <jni.h>
#include <string>
#include <pthread.h>
#include <CoreFoundation/CoreFoundation.h>
#include <sys/param.h>
#include <launcher.h>
#include <unistd.h>

extern "C" { int _NSGetExecutablePath(char* buf, uint32_t* bufsize); }


std::string getExecutableDir() {
    char buf[MAXPATHLEN];
    uint32_t size = sizeof(buf);
    _NSGetExecutablePath(buf, &size);
    std::string path = std::string(buf);
    return path.substr(0, path.find_last_of('/'));
}

bool changeWorkingDir(std::string dir) {
    return chdir(dir.c_str()) == 0;
}

int g_argc;
char** g_argv;


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