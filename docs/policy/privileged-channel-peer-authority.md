<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Who the privileged command channel trusts

The local server (`server/`, launched into a root or ADB-shell process by
`run_server.sh`) exposes one operation that matters: `TYPE_SHELL`, which runs an
arbitrary command string with the server's privileges. Anything that reaches the
listening socket and is accepted therefore has root, or shell.

This note records the channel's trust model: where it listens, which property of a
connecting peer is authoritative, why the session has no TLS, and what it can't
defend against.

## Which transport is actually used

`Server` has two implementations:

- `LocalServerImpl`, an abstract-namespace `LocalServerSocket`.
- `NetSocketServerImpl`, a `ServerSocket` bound to `127.0.0.1`.

`ServerHandler` picks between them by whether the `path` parameter parses as an
integer. The launcher always supplies a port: `ServerConfig.getServerRunnerCommand`
passes `getLocalServerPort()` as `$1` of `run_server.sh`, which becomes
`path:<port>`. There is no `LocalSocket` client anywhere in the app. Every client
connection is made by `LocalServerManager` to
`ServerConfig.getLocalServerHost()`:`getLocalServerPort()`.

**The loopback TCP path is the shipped path.** The `LocalSocket` path is
unreachable in shipped configurations but is still gated, so it does not become a
weaker door if a future change starts using it.

## Loopback only

The listener binds `127.0.0.1`, never the wildcard address the code used
originally. That's a reachability limit, not an authenticator: it keeps the channel
off the LAN while the device is in ADB-over-TCP or root port mode, and leaves only
processes on the device able to connect. `ServerLoopbackBindTest` in the `server`
module fails if the listener binds anything but `127.0.0.1`, or if any other IPv4
address of the test machine can reach it.

## Authoritative properties

Two properties are checked, in this order:

1. **The peer's owning uid.** Checked before the handshake is even read. The peer
   does not choose it.
   - `LocalSocket`: `getPeerCredentials()` (`SO_PEERCRED`).
   - Loopback TCP: TCP carries no peer credentials, so the connection is looked
     up in `/proc/net/tcp` and `/proc/net/tcp6` by its port pair and the owning
     uid is read from the table. This is what `PeerAuthority.findPeerUid` parses.

   The peer is accepted when its **app id** (`uid % 100000`, so a client in a
   secondary user or work profile still matches) equals the app id of the package
   named by the `app` launch parameter, or when it is the server's own uid.

2. **Proof of the session token** (`DataTransmission.shakeHands`, protocol 1.4.0).
   The token is generated per session and kept in `server_secrets` preferences.
   Neither side ever sends it. Each side sends a fresh random nonce, and each
   proves it holds the token with an HMAC-SHA256, keyed with the token, over both
   nonces and a label for its role. The client checks the server's proof before it
   sends its own, so a process squatting on the port learns nothing it could relay.
   The labels keep one side's proof from being replayed as the other's, and the
   nonces keep an old exchange from being replayed at all. Proofs are compared in
   constant time and never written to the log. `DataTransmissionHandshakeTest`
   covers a wrong token, a squatting server, a replayed nonce and older protocol
   versions.

## Why there's no TLS

Both ends of the channel are on the same device, and the traffic never leaves the
loopback interface. TLS protects a session from someone on the path between two
machines, and loopback has no such path. On Android, an app without root can't
capture loopback traffic, and a process that can (root, or the shell uid with the
right tools) already holds the privileges the channel grants and can read the token
from `server_secrets` directly.

TLS wouldn't add a secret either. Its key would have to live in the same app
storage as the token, so anyone able to steal one could steal the other. What TLS
would bring is a certificate to generate, store and rotate, plus handshake cost on
every connect, with no attacker it stops that the loopback bind and the mutual HMAC
don't already stop.

## The case that cannot be decided

If the peer uid cannot be established (`/proc/net/tcp` is unreadable, no row
matches, or the `app` parameter's package can't be resolved to a uid in the
privileged process), the connection proceeds on the token proof alone. That outcome
is logged explicitly by `Server.authorizePeer` and `ServerHandler.resolveExpectedAppId`,
naming which half was missing, so it can never be mistaken for a check that ran
and passed. A peer that *is* identified and does not match is refused outright.

## What this does not cover

- Another process running as the **same app id** is indistinguishable from the
  app itself. Nothing on this channel can separate them.
- A peer running as **root or the shell uid** can read the token from
  `server_secrets` directly, and can bypass this channel entirely. The channel
  does not defend against a peer that already has the privileges it grants.
