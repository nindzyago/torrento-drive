// Runs byedpi's proxy inside the app's process: ByeDpi.run() blocks on its own thread for as long as the app lives.
#include <jni.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>

#include "params.h"
#include "proxy.h"

// defined in byedpi/main.c, not declared in its headers
int parse_args(int argc, char **argv);
int init(void);

JNIEXPORT jint JNICALL
Java_io_github_nindzyago_torrentodrive_connection_ByeDpi_run(JNIEnv *env, jclass cls, jobjectArray args)
{
    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (*env)->GetObjectArrayElement(env, args, i);
        const char *c = (*env)->GetStringUTFChars(env, s, 0);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
    }
    // byedpi keeps pointers into argv, so it is never freed
    if (parse_args(argc, argv)) {
        return -1;
    }
    if (init() < 0) {
        return -2;
    }
    // not byedpi's run(): that one also takes over SIGINT/SIGTERM, which belong to the app
    signal(SIGPIPE, SIG_IGN);
    int fd = listen_socket(&params.laddr);
    if (fd < 0) {
        return -3;
    }
    return start_event_loop(fd);
}
