// Test-only shim: this sandbox kernel lacks IPv6, hev uses dual-stack AF_INET6 sockets.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <netinet/in.h>
#include <string.h>
#include <sys/socket.h>
int socket(int d, int t, int p) {
    int (*real)(int,int,int) = dlsym(RTLD_NEXT, "socket");
    return real(d == AF_INET6 ? AF_INET : d, t, p);
}
int setsockopt(int fd, int lvl, int opt, const void *v, socklen_t l) {
    int (*real)(int,int,int,const void*,socklen_t) = dlsym(RTLD_NEXT, "setsockopt");
    if (lvl == IPPROTO_IPV6) return 0;
    return real(fd, lvl, opt, v, l);
}
int connect(int fd, const struct sockaddr *a, socklen_t l) {
    int (*real)(int,const struct sockaddr*,socklen_t) = dlsym(RTLD_NEXT, "connect");
    if (a && a->sa_family == AF_INET6) {
        const struct sockaddr_in6 *s6 = (const void *)a;
        struct sockaddr_in s4 = { .sin_family = AF_INET, .sin_port = s6->sin6_port };
        memcpy(&s4.sin_addr, &s6->sin6_addr.s6_addr[12], 4);
        return real(fd, (struct sockaddr *)&s4, sizeof s4);
    }
    return real(fd, a, l);
}
