// Small single-threaded parent for mina. No shell, JNI or hidden Android APIs.
// stdin is held by the app: Stop or app death (EOF) sends SIGINT to mina.
#define _POSIX_C_SOURCE 200809L
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/prctl.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static long long now_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return 0;
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "Missing node executable\n"); return 64; }
    const pid_t owner = getpid();
    const pid_t child = fork();
    if (child < 0) { perror("fork"); return 71; }
    if (child == 0) {
        // If Android kills the supervisor, the native node cannot stay orphaned.
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != owner) _exit(71);
        // The node does not consume the control pipe; only the supervisor does.
        close(STDIN_FILENO);
        execv(argv[1], &argv[1]);
        perror("execv mina");
        _exit(126);
    }
    int status = 0, stopping = 0;
    long long deadline = 0;
    for (;;) {
        const pid_t result = waitpid(child, &status, WNOHANG);
        if (result == child) break;
        if (result < 0 && errno != EINTR) { perror("waitpid"); return 71; }
        if (!stopping) {
            struct pollfd control = { .fd = STDIN_FILENO, .events = POLLIN | POLLHUP };
            int ready = poll(&control, 1, 100);
            if (ready < 0 && errno != EINTR) { perror("control poll"); stopping = 1; }
            if (ready > 0 && (control.revents & (POLLIN | POLLHUP | POLLERR | POLLNVAL))) {
                char command[32];
                // Any command or closed pipe is an explicit request to stop.
                (void)read(STDIN_FILENO, command, sizeof(command));
                stopping = 1;
            }
            if (stopping) {
                fprintf(stderr, "[app] Arrêt propre du nœud (SIGINT)\n");
                kill(child, SIGINT);
                deadline = now_ms() + 90000;
            }
        } else {
            if (now_ms() >= deadline) {
                fprintf(stderr, "[app] Arrêt forcé après 90 secondes\n");
                kill(child, SIGKILL);
                while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
                break;
            }
            struct timespec pause = { .tv_sec = 0, .tv_nsec = 100000000 };
            nanosleep(&pause, NULL);
        }
    }
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}
