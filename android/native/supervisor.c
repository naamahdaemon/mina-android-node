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
#include <stdint.h>

static void wipe(unsigned char *bytes, size_t len) {
    volatile unsigned char *p = bytes;
    while (len--) *p++ = 0;
}

static int transfer_all(int fd, unsigned char *bytes, size_t len, int writing) {
    while (len) {
        ssize_t n = writing ? write(fd, bytes, len) : read(fd, bytes, len);
        if (n < 0 && errno == EINTR) continue;
        if (n <= 0) return -1;
        bytes += n; len -= (size_t)n;
    }
    return 0;
}

static long long now_ms(void) {
    struct timespec ts;
    if (clock_gettime(CLOCK_MONOTONIC, &ts) != 0) return 0;
    return (long long)ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}

int main(int argc, char **argv) {
    if (argc < 2) { fprintf(stderr, "Missing node executable\n"); return 64; }
    // One bounded frame is forwarded to a separate stdin pipe for Rust. The
    // supervisor alone consumes subsequent Stop/EOF on the application's pipe.
    unsigned char frame[16388] = {0};
    if (transfer_all(STDIN_FILENO, frame, 4, 0) != 0) return 65;
    uint32_t len = (uint32_t)frame[0] | ((uint32_t)frame[1] << 8)
        | ((uint32_t)frame[2] << 16) | ((uint32_t)frame[3] << 24);
    if (len > 16384 || transfer_all(STDIN_FILENO, frame + 4, len, 0) != 0) {
        wipe(frame, sizeof(frame)); return 65;
    }
    int secret_pipe[2];
    if (pipe(secret_pipe) != 0) { wipe(frame, sizeof(frame)); return 71; }
    const pid_t owner = getpid();
    const pid_t child = fork();
    if (child < 0) { wipe(frame, sizeof(frame)); close(secret_pipe[0]); close(secret_pipe[1]); perror("fork"); return 71; }
    if (child == 0) {
        // If Android kills the supervisor, the native node cannot stay orphaned.
        if (prctl(PR_SET_PDEATHSIG, SIGKILL) != 0 || getppid() != owner) _exit(71);
        setpgid(0, 0);
        close(secret_pipe[1]);
        if (dup2(secret_pipe[0], STDIN_FILENO) < 0) _exit(71);
        close(secret_pipe[0]);
        wipe(frame, sizeof(frame));
        execv(argv[1], &argv[1]);
        perror("execv mina");
        _exit(126);
    }
    setpgid(child, child);
    fprintf(stderr, "[app] node-pid=%ld\n", (long)child);
    close(secret_pipe[0]);
    signal(SIGPIPE, SIG_IGN);
    int sent = transfer_all(secret_pipe[1], frame, len + 4, 1);
    close(secret_pipe[1]);
    wipe(frame, sizeof(frame));
    if (sent != 0) kill(-child, SIGKILL);
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
                kill(-child, SIGINT);
                deadline = now_ms() + 90000;
            }
        } else {
            if (now_ms() >= deadline) {
                fprintf(stderr, "[app] Arrêt forcé après 90 secondes\n");
                kill(-child, SIGKILL);
                while (waitpid(child, &status, 0) < 0 && errno == EINTR) {}
                break;
            }
            struct timespec pause = { .tv_sec = 0, .tv_nsec = 100000000 };
            nanosleep(&pause, NULL);
        }
    }
    // Clean up a worker that has not yet noticed its parent's closed pipe.
    kill(-child, SIGTERM);
    return WIFEXITED(status) ? WEXITSTATUS(status) : 128 + WTERMSIG(status);
}
