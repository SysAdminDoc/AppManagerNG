// SPDX-License-Identifier: GPL-3.0-or-later
//
// Native launcher for the local privileged server, run by root in place of run_server.sh.
// Based on App Manager's run_server.c by Muntashir Al-Islam (upstream commits 07c7199 and b42efbb).
//
// USAGE: librun_server.so <port> <token> <jar_path> <nice_name> <server_args>
//
// It starts app_process on the server jar where the app keeps it, in a new session, and reports
// whether exec worked. Nothing here goes through a shell. Upstream copies the jar into
// /data/local/tmp first, but the shell user owns that folder and can rename a file of its own over
// the copy before app_process opens it, so root would run the shell user's code. The app's own
// storage has no such gap.

// For pipe2
#define _GNU_SOURCE

#include <errno.h>
#include <fcntl.h>
#include <limits.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <unistd.h>

#define JAR_MAIN_CLASS "io.github.muntashirakon.AppManager.server.ServerRunner"
#define APP_PROCESS "/system/bin/app_process"
#define MAX_PATH_LEN 512
#define MAX_TOKEN_LEN 256
#define MAX_NAME_LEN 64
#define MAX_ARGS_LEN 512

static int is_name_char(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
           || c == '.' || c == '_' || c == '-';
}

static int is_alnum(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
}

// 1 to 65535, digits only
static int is_port(const char *s) {
    size_t len = strlen(s);
    if (len == 0 || len > 5) {
        return 0;
    }
    long value = 0;
    for (size_t i = 0; i < len; i++) {
        if (s[i] < '0' || s[i] > '9') {
            return 0;
        }
        value = value * 10 + (s[i] - '0');
    }
    return value >= 1 && value <= 65535;
}

static int is_token(const char *s) {
    size_t len = strlen(s);
    if (len == 0 || len > MAX_TOKEN_LEN) {
        return 0;
    }
    for (size_t i = 0; i < len; i++) {
        if (!is_alnum(s[i])) {
            return 0;
        }
    }
    return 1;
}

static int is_name(const char *s) {
    size_t len = strlen(s);
    if (len == 0 || len > MAX_NAME_LEN || strcmp(s, ".") == 0 || strcmp(s, "..") == 0) {
        return 0;
    }
    for (size_t i = 0; i < len; i++) {
        if (!is_name_char(s[i])) {
            return 0;
        }
    }
    return 1;
}

// An absolute path of plain names: no empty, "." or ".." parts
static int is_path(const char *s) {
    size_t len = strlen(s);
    if (len < 2 || len >= MAX_PATH_LEN || s[0] != '/') {
        return 0;
    }
    const char *part = s + 1;
    while (1) {
        const char *end = strchr(part, '/');
        size_t part_len = end ? (size_t) (end - part) : strlen(part);
        if (part_len == 0 || part_len > NAME_MAX) {
            return 0;
        }
        if ((part_len == 1 && part[0] == '.') || (part_len == 2 && part[0] == '.' && part[1] == '.')) {
            return 0;
        }
        for (size_t i = 0; i < part_len; i++) {
            if (!is_name_char(part[i])) {
                return 0;
            }
        }
        if (!end) {
            return 1;
        }
        part = end + 1;
    }
}

// A plain file, not a link to one. O_NONBLOCK keeps a FIFO from holding the launcher up.
static int is_regular_file(const char *path) {
    int fd = open(path, O_RDONLY | O_CLOEXEC | O_NOFOLLOW | O_NONBLOCK);
    if (fd < 0) {
        return 0;
    }
    struct stat st;
    int result = fstat(fd, &st) == 0 && S_ISREG(st.st_mode);
    close(fd);
    return result;
}

// The server's config after the port: empty, or ",key:value" pairs
static int is_server_args(const char *s) {
    size_t len = strlen(s);
    if (len == 0) {
        return 1;
    }
    if (len > MAX_ARGS_LEN || s[0] != ',') {
        return 0;
    }
    for (size_t i = 0; i < len; i++) {
        if (!is_name_char(s[i]) && s[i] != ':' && s[i] != ',') {
            return 0;
        }
    }
    // The token goes last, from its own argument
    return strstr(s, ",token:") == NULL;
}

static int write_all(int fd, const char *buf, size_t len) {
    while (len > 0) {
        ssize_t n = write(fd, buf, len);
        if (n < 0) {
            if (errno == EINTR) {
                continue;
            }
            return -1;
        }
        buf += n;
        len -= (size_t) n;
    }
    return 0;
}

int main(int argc, char *argv[]) {
    if (argc != 6) {
        fprintf(stderr, "USAGE: %s <port> <token> <jar_path> <nice_name> <server_args>\n",
                argc > 0 ? argv[0] : "librun_server.so");
        return 1;
    }
    const char *port = argv[1];
    const char *token = argv[2];
    const char *jar_path = argv[3];
    const char *nice_name = argv[4];
    const char *server_args = argv[5];

    if (!is_port(port)) {
        fprintf(stderr, "Error! Invalid port.\n");
        return 1;
    }
    if (!is_token(token)) {
        fprintf(stderr, "Error! Invalid token.\n");
        return 1;
    }
    if (!is_path(jar_path)) {
        fprintf(stderr, "Error! Invalid jar path.\n");
        return 1;
    }
    if (!is_regular_file(jar_path)) {
        fprintf(stderr, "Error! The jar isn't a file: %s\n", jar_path);
        return 1;
    }
    if (!is_name(nice_name)) {
        fprintf(stderr, "Error! Invalid process name.\n");
        return 1;
    }
    if (!is_server_args(server_args)) {
        fprintf(stderr, "Error! Invalid server arguments.\n");
        return 1;
    }

    char config[16 + MAX_ARGS_LEN + MAX_TOKEN_LEN];
    if (snprintf(config, sizeof(config), "path:%s%s,token:%s", port, server_args, token) >= (int) sizeof(config)) {
        fprintf(stderr, "Error! Server arguments are too long.\n");
        return 1;
    }
    char nice_name_arg[16 + MAX_NAME_LEN];
    snprintf(nice_name_arg, sizeof(nice_name_arg), "--nice-name=%s", nice_name);

    printf("Starting %s as %d:%d...\n", nice_name, (int) getuid(), (int) getgid());
    printf("Jar path: %s\n", jar_path);
    fflush(stdout);

    if (setenv("CLASSPATH", jar_path, 1) != 0) {
        fprintf(stderr, "Error! Could not set CLASSPATH: %s\n", strerror(errno));
        return 1;
    }

    // The child writes errno here if exec fails. On success, exec closes it and the read sees EOF.
    int status_pipe[2];
    if (pipe2(status_pipe, O_CLOEXEC) != 0) {
        fprintf(stderr, "Error! Could not start local server: %s\n", strerror(errno));
        return 1;
    }
    pid_t pid = fork();
    if (pid < 0) {
        fprintf(stderr, "Error! Could not start local server: %s\n", strerror(errno));
        return 1;
    }
    if (pid == 0) {
        close(status_pipe[0]);
        setsid();
        signal(SIGHUP, SIG_IGN);
        // The caller reads this process's output to its end, so the server can't keep it open
        int null_fd = open("/dev/null", O_RDWR | O_CLOEXEC);
        if (null_fd >= 0) {
            dup2(null_fd, STDIN_FILENO);
            dup2(null_fd, STDOUT_FILENO);
            dup2(null_fd, STDERR_FILENO);
        }
        char *exec_argv[] = {"app_process", "/system/bin", nice_name_arg, JAR_MAIN_CLASS, config, NULL};
        execv(APP_PROCESS, exec_argv);
        int err = errno;
        write_all(status_pipe[1], (const char *) &err, sizeof(err));
        _exit(127);
    }
    close(status_pipe[1]);
    int child_errno = 0;
    ssize_t n;
    do {
        n = read(status_pipe[0], &child_errno, sizeof(child_errno));
    } while (n < 0 && errno == EINTR);
    close(status_pipe[0]);
    if (n > 0) {
        fprintf(stderr, "Error! Could not start local server: %s\n", strerror(child_errno));
        return 1;
    }
    printf("Local server has started.\n");
    return 0;
}
