// Runs byedpi's proxy inside the app's process: ByeDpi.run() blocks on its own thread for as long as the app lives.
#include <jni.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <unistd.h>

#include "params.h"
#include "proxy.h"

// defined in byedpi/main.c and proxy.c, not declared in their headers
int parse_args(int argc, char **argv);
int init(void);
void clear_params(char *line, char **argv);
extern int server_fd;

// byedpi keeps its settings in the global `params`; a copy of the untouched defaults lets it start again
// with other arguments (the connection diagnostics runs one strategy at a time)
static struct params defaults;
static int have_defaults;

JNIEXPORT jint JNICALL
Java_io_github_nindzyago_torrentodrive_connection_ByeDpi_run(JNIEnv *env, jclass cls, jobjectArray args)
{
    if (!have_defaults) {
        defaults = params;
        have_defaults = 1;
    }
    params = defaults;
    // start reading the arguments from the beginning again (0 makes getopt reset itself, in bionic and glibc alike)
    optind = 0;

    int argc = (*env)->GetArrayLength(env, args);
    char **argv = calloc(argc + 1, sizeof(char *));
    for (int i = 0; i < argc; i++) {
        jstring s = (*env)->GetObjectArrayElement(env, args, i);
        const char *c = (*env)->GetStringUTFChars(env, s, 0);
        argv[i] = strdup(c);
        (*env)->ReleaseStringUTFChars(env, s, c);
    }
    // byedpi keeps pointers into argv, so it is never freed (a few hundred bytes per start)
    int result;
    if (parse_args(argc, argv)) {
        result = -1;
    }
    else if (init() < 0) {
        result = -2;
    }
    else {
        // not byedpi's run(): that one also takes over SIGINT/SIGTERM, which belong to the app
        signal(SIGPIPE, SIG_IGN);
        int fd = listen_socket(&params.laddr);
        result = fd < 0 ? -3 : start_event_loop(fd);
    }
    clear_params(0, 0);
    return result;
}

// Makes run() return: the event loop ends when accept() on the listening socket fails.
JNIEXPORT void JNICALL
Java_io_github_nindzyago_torrentodrive_connection_ByeDpi_stopLoop(JNIEnv *env, jclass cls)
{
    shutdown(server_fd, SHUT_RDWR);
}
