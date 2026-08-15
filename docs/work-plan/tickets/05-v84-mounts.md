# 05 — v84 mounts rideable

**Blocked by:** 03

**Status:** ready-for-agent

## What to build

The eight mounts v84 added — Soaring Hawk, Soaring Eagle, Soaring Red/Blue/Black Wyvern, Soaring Griffey, Dragonica, Dragon Rider (`8300000`–`8300007`) — can be obtained, mounted, ridden and dismounted.

A mount is a vertical slice in its own right: it needs the TamingMob data, the mount item, the riding skill, and the server-side monster-riding buff to line up. Cosmic already handles monster riding for every existing mount, so the server work should be configuration rather than new code — confirm that early, because if it is not true this ticket is larger than it looks.

## Acceptance criteria

- [ ] All eight mounts present in client WZ and server XML
- [ ] Each can be obtained, mounted, ridden across a map, and dismounted
- [ ] Riding buff applies and expires correctly
- [ ] Existing mounts still work — no regression to the shared riding path
