# 81 - Paginate `@commands` NPC output safely

**Class:** owner-requested
**Slice:** `docs/work-plan/COMMANDS-NPC-OUTPUT-SPEC.md`
**Blocked by:** None.
**Startable now:** YES.
**Implementation agent:** `gp-opus-medium`.
**Review agent:** `gp-opus-high`.

The real v84 client crashes after the player opens `@commands`, selects a rank, and receives that
rank's current one-dialogue command list. Paginate that rank's output in
`scripts/npc/commands.js`; add a script-level regression test in
`src/test/java/scripting/CommandsNpcScriptTest.java`.

## What to do

1. Keep the current rank chooser and permission rule: it shows exactly Common through the
   player's GM level, never a higher rank.
2. Split a selected rank's command records into pages of at most **10 command rows**. This is a
   product pagination bound only; do not name it as, calculate it from, or document it as a v84
   client/packet/text maximum.
3. Preserve the selected-rank heading, existing `@` prefix for Common/Donator and `!` prefix for
   staff ranks, and the current command order. Provide working back-to-rank, previous-page, and
   next-page navigation where applicable. The first page has no previous page; the last has no next
   page. Returning to the rank chooser must not expose a higher rank.
4. Add `CommandsNpcScriptTest`, using the real `CommandsExecutor` command lists through the same
   injected `ce` binding as production and a public `cm`/player stub. Drive the script's actual
   NPC state transitions; do not test a copied pagination helper in isolation.

## Precedent

- **Commit `785f74ed2`** (`Fix HelpCommand not working without static CommandsExecutor`) is the
  binding precedent: `commands.js` obtains its list from the injected `ce` executor, not from a
  new static executor or a duplicate command registry.
- **`src/test/java/scripting/SlumberingDragonFerryScriptTest.java`** is the script-test precedent:
  load the real JS through `AbstractScriptManager`, bind a public stub, invoke `start`/`action`,
  and assert the recorded NPC dialogue.

## Acceptance criteria

- [ ] Selecting every rank available to a GM6 lists exactly the commands supplied by the real
      `CommandsExecutor` for that rank, in their existing order; across that rank's pages each
      rendered command record appears exactly once, with no omission or duplicate.
- [ ] The regression test forces at least one multi-page rank and asserts every output page has at
      most 10 command records. It does not assert a character, byte, or packet-size threshold.
- [ ] The test drives forward then backward across a multi-page rank, returns to the rank chooser,
      and shows that the selected rank and the first/last-page boundaries remain correct.
- [ ] A GM2 fixture sees only Common, Donator, and JrGM in the rank chooser; the list headings and
      command prefixes remain `@` for Common/Donator and `!` for JrGM.
- [ ] `CommandsNpcScriptTest` evaluates the real script and fails if the production `cm` methods it
      calls are no longer exposed by `NPCConversationManager`, following the surface-pin pattern in
      `SlumberingDragonFerryScriptTest`.
- [ ] `./mvnw -o -Dtest=CommandsNpcScriptTest test` is green when this ticket is run alone. Do not
      run Maven while another agent owns `target/`.
- [ ] Owner verification after the server is refreshed: on the real v84 client, `@commands` opens
      rank pages without a crash and every expected command remains browsable.

## Do not

- Do not touch command registration, rank permissions, descriptions, ordering, command execution,
  client files, WZ data, packets, or the database.
- Do not modify unrelated NPC scripts or introduce a generic NPC-dialogue truncation rule.
- Do not invent a native v84 client limit; the only requirement is the explicit ten-row product
  page bound above.
- Do not launch a client, restart/stop the running server, or run Maven concurrently with another
  agent.
