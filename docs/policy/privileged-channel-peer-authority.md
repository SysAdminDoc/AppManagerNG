<!-- SPDX-License-Identifier: GPL-3.0-or-later OR CC-BY-SA-4.0 -->
# Who the privileged command channel trusts

The local server (`server/`, launched into a root or ADB-shell process by
`run_server.sh`) exposes one operation that matters: `TYPE_SHELL`, which runs an
arbitrary command string with the server's privileges. Anything that reaches the
listening socket and is accepted therefore has root, or shell.

This note records which property of a connecting peer is authoritative, and why.

## Which transport is actually used

`Server` has two implementations:

- `LocalServerImpl` — an abstract-namespace `LocalServerSocket`.
- `NetSocketServerImpl` — a `ServerSocket` bound to `127.0.0.1`.

`ServerHandler` picks between them by whether the `path` parameter parses as an
integer. The launcher always supplies a port: `ServerConfig.getServerRunnerCommand`
passes `getLocalServerPort()` as `$1` of `run_server.sh`, which becomes
`path:<port>`. There is no `LocalSocket` client anywhere in the app; every client
connection is made by `LocalServerManager` to
`ServerConfig.getLocalServerHost()`:`getLocalServerPort()`.

**The loopback TCP path is the shipped path.** The `LocalSocket` path is
unreachable in shipped configurations but is still gated, so it does not become a
weaker door if a future change starts using it.

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

2. **The handshake token** (`DataTransmission.shakeHands`). A device-local bearer
   credential generated per session and kept in `server_secrets` preferences. It
   is compared in constant time, and is never written to the log.

Binding to `127.0.0.1` — rather than the wildcard address the code used
originally — is a reachability limit, not an authenticator: it keeps the channel
off the LAN while the device is in ADB-over-TCP or root port mode.

## The case that cannot be decided

If the peer uid cannot be established — `/proc/net/tcp` unreadable, no matching
row, or the `app` parameter's package cannot be resolved to a uid in the
privileged process — the connection proceeds on the token alone. That outcome is
logged explicitly by `Server.authorizePeer` and `ServerHandler.resolveExpectedAppId`,
naming which half was missing, so it can never be mistaken for a check that ran
and passed. A peer that *is* identified and does not match is refused outright.

## What this does not cover

- Another process running as the **same app id** is indistinguishable from the
  app itself. Nothing on this channel can separate them.
- A peer running as **root or the shell uid** can read the token from
  `server_secrets` directly, and can bypass this channel entirely. The channel
  does not defend against a peer that already has the privileges it grants.
