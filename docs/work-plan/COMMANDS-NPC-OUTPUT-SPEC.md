# Safe `@commands` NPC output

**Class:** owner-requested

## Problem

On the real v84 client, opening `@commands`, selecting a command rank, and receiving that rank's
complete command list crashes the client. The server currently emits every row as one NPC dialogue;
the list is unbounded as commands are added.

## Solution

Show a selected rank's command list as pages of no more than **10 command rows**. Ten is a product
pagination chunk, not a claimed v84 client text, byte, or packet limit. The rank chooser, rank
headings, command prefix, command order, and back/forward navigation remain usable. Every command
visible before the change remains visible once, on exactly one page.

## User story

As a player or GM using `@commands`, I can browse every command available to my rank without the
v84 client crashing or a command disappearing from the list.

## Decisions

- This is an **owner-requested server-script safety fix**, not a v84-data parity claim and not a
  `v83 legacy` classification.
- The per-page bound is 10 command rows. It deliberately does not assert or infer a native-client
  maximum message size.
- Rank visibility remains permission-based: a character sees rank choices from Common through its
  own GM level, and no higher rank.
- The existing Common/Donator `@` and staff `!` prefixes remain tied to their current rank groups.
- Navigation must let the player return to the rank chooser and move between adjacent pages without
  losing the chosen rank.

## Out of scope

- Changing command permissions, registration, descriptions, ordering, or command execution.
- Applying a global NPC-dialogue length cap or altering unrelated NPC scripts.
- Client binaries, WZ data, opcodes, packet formats, database data, or a claim of a native v84
  dialogue-size limit.
