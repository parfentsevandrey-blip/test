/*
 ============================================================================
 Name        : hev-socks5-client.h
 Author      : Heiher <r@hev.cc>
 Copyright   : Copyright (c) 2021 - 2024 hev
 Description : Socks5 Client
 ============================================================================
 */

#ifndef __HEV_SOCKS5_CLIENT_H__
#define __HEV_SOCKS5_CLIENT_H__

#include "hev-socks5.h"
#include "hev-socks5-proto.h"

#ifdef __cplusplus
extern "C" {
#endif

#define HEV_SOCKS5_CLIENT(p) ((HevSocks5Client *)p)
#define HEV_SOCKS5_CLIENT_CLASS(p) ((HevSocks5ClientClass *)p)
#define HEV_SOCKS5_CLIENT_TYPE (hev_socks5_client_class ())

typedef struct _HevSocks5Client HevSocks5Client;
typedef struct _HevSocks5ClientClass HevSocks5ClientClass;

struct _HevSocks5Client
{
    HevSocks5 base;

    struct
    {
        const char *user;
        const char *pass;
    } auth;
};

struct _HevSocks5ClientClass
{
    HevSocks5Class base;

    HevSocks5Addr *(*get_upstream_addr) (HevSocks5Client *self);
    int (*set_upstream_addr) (HevSocks5Client *self, HevSocks5Addr *addr);
};

HevObjectClass *hev_socks5_client_class (void);

int hev_socks5_client_construct (HevSocks5Client *self, HevSocks5Type type);

int hev_socks5_client_connect (HevSocks5Client *self, const char *addr,
                               int port);

int hev_socks5_client_handshake (HevSocks5Client *self, int pipeline);

/*
 * Opal: optimistic data. hev_socks5_client_handshake_request () sends the
 * whole handshake at once (auth methods, credentials, request) and returns
 * without waiting for replies, so the caller can pass the application's first
 * bytes on right away; Tor forwards them with the stream's BEGIN cell.
 * hev_socks5_client_handshake_reply () then consumes exactly the replies, never
 * blocking: 1 once connected, 0 while they are incomplete, -1 on failure.
 */
int hev_socks5_client_handshake_request (HevSocks5Client *self);
int hev_socks5_client_handshake_reply (HevSocks5Client *self);

void hev_socks5_client_set_auth (HevSocks5Client *self, const char *user,
                                 const char *pass);

#ifdef __cplusplus
}
#endif

#endif /* __HEV_SOCKS5_CLIENT_H__ */
