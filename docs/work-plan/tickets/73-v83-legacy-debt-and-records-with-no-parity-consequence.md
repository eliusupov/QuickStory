# 73 - v83 legacy: engineering debt and two records with no parity consequence

**Class:** v83 legacy - NOT a v84 parity gap
**Work rows:** R32, R33, R36, R51 - `docs/work-plan/V84-WORK-ROWS.tsv`
**Blocked by:** OWNER decision - outside the standing "v84 parity only" scope

All four rows here are **v83 legacy or pure engineering debt, not v84 parity gaps**, and every one
has zero `add-list` rows for the thing that is actually broken - v84 added nothing that any of them
would close. None of them misbehaves against a v84 client, which is the operative test. Two are
harness debt, one is an unimplemented v83 event NPC, and one is a tracker sentence that is wrong on
all three of its assertions and needs restating rather than working.

## R32 - The packet validator covers 33 of 307 sendops

`src/main/resources/opcodes/sendops-84.properties` has **307 keys**. Verified coverage is **29 rows
in `tools/v84/decode-models-v84.tsv` plus 6 in `-binary.tsv`**, which is **33 distinct opcodes**
once the three per-mode variants collapse. The figure has been re-counted and is exact.

The candidate-row filter is `PacketStructureModels.java:75`. The old tracker's `:46-50` is wrong -
that is the `loadAll` javadoc at `:45-49`.

This is a **test-harness property, not a parity defect**. A low coverage number does not misbehave
against a client; it only means fewer packets are checked offline. Closing it means writing 274 more
models. The named next step, if it is ever taken, is per-mode models for the 90 mode-byte opcodes.

## R33 - CLIENT_START_ERROR de-dup is in-memory

`tools/packetvalidator/ClientStartErrorHandler.java`: `SEEN_LIMIT=512` at `:30`, the seen set at
`:35` carrying an existing `ponytail:` comment at `:32-34`, and the bounded LRU at `:38-46`.

Steady state **within one boot is already correct**. The only symptom is that the first reconnect
after every restart re-warns about 12 entries.

The cheapest real fix is a flat file beside the log, about 15 lines. It has no precedent in this
repo - everything else here is DB or WZ - and a table is not warranted for 12 strings. No
v84-parity consequence either way.

## R36 - NPC 9000054 Ranch Owner is dead

Placed on **109090000** "Sheep Ranch Lobby", **910040000** "Ranch Entrance" and **910040002**
"Fenced Street". Zero references in `src/`, `scripts/`, `database/` or `wz/Quest.wz`.

The role is established: pristine v84 `Npc.wz/9000054.img/info/script/0/script` = **`"BF_master"`**,
the host of the Sheep Ranch event mini-game. But `add-list/Npc.txt:80` lists only `info/reg/event`,
an animation sub-node - **the NPC is v83 legacy and v84 added nothing to it but a reg**. This is an
unimplemented v83 event, not a parity gap, and building the mini-game is large.

## R51 - The "18 endgame weapons" claim is refuted on all three assertions

v84 adds **39** new weapon images. This row is a restatement, not code.

- **The count is 17, not 18.** The `013029xx` block is **01302900-01302916**, seventeen ids. The
  "18" was most likely those 17 counted together with **01302132** "Pig Herding Stick".
- **They are not endgame weapons.** They are GM test swords - "One Shot Kill Sword", "Boss Kill
  Sword", "Slow Sword" - at `reqLevel 10`, `incPAD 10`, `only=1`, `tradeBlock=1`, `price=1`,
  referenced by nothing but their own `Character.wz` image and `String.wz/Eqp.img`.
- **The container mechanism is real but describes a different, smaller set.** Seven weapons -
  **1332100** Nageling, **1402073** Askaron, **1432066** Bellum Spear, **1442090** Machlear,
  **1462076** Inferna, **1482051** Crusio, all `reqLevel 105`, plus **1382092** Circle-Winded Staff
  from cash coupon **5530027** - and one cape, **1102231** Sirius Cape, come from the five Dragon
  Rider's `<job>` Box items **2022652-2022656**.

The set that actually fits the words "endgame weapons" is the **6 Neo weapons**: **1372059**,
**1402074**, **1442091**, **1452090**, **1472101**, **1482052**, `reqLevel 83-85`. **Nothing in the
tree references them at all.**

## Precedent

**R32.** The 33 existing verified rows in `decode-models-v84.tsv` and `-binary.tsv` are the shape
any further model copies; ticket 41-binary-derived-packet-models is how they were produced.

**R33.** UNKNOWN - no flat-file persistence precedent exists in this repo. That is itself the
argument for leaving it.

**R36.** The v84 `info/script/0/script` = `"BF_master"` leaf establishes the role and nothing more.
There is no Sheep Ranch mini-game implementation anywhere in this tree to copy.

**R51.** No precedent needed - the corrected numbers above are the deliverable.

## Acceptance criteria

- [ ] The owner records a yes/no per row, in writing, in `V84-OPEN-ITEMS.md`.
- [ ] The tracker prose states the validator coverage as **33 of 307** and cites the filter as
      `PacketStructureModels.java:75`, not `:46-50`.
- [ ] The tracker prose states that the `013029xx` block is **17** ids, that they are GM test swords
      at `reqLevel 10`, and that the container set is the 7 weapons + 1 cape listed above from
      2022652-2022656.
- [ ] The tracker prose names the 6 Neo weapons (1372059, 1402074, 1442091, 1452090, 1472101,
      1482052) as the set with no reference anywhere in the tree.
- [ ] `V84-OPEN-ITEMS.md` is the only tracker file this ticket's diff touches. `STATUS.md`,
      `V84-COVERAGE.md`/`.tsv` and `V84-WORK-ROWS.tsv` are owned by other agents and must not appear
      in the diff.
- [ ] If R33 is approved: after a restart, a CLIENT_START_ERROR already seen in the previous boot
      produces no second warning, asserted by a test that writes the persistence file and
      re-instantiates the handler.
- [ ] If R32 is approved: the verified-model count in `decode-models-v84.tsv` plus `-binary.tsv`
      rises above 33 and the new rows pass `PacketStructureValidator` against captured bytes.

Run any test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** - they
collide on `target/`.

## Do not

- Do not edit `STATUS.md`, `V84-COVERAGE.md`, `V84-COVERAGE.tsv` or `V84-WORK-ROWS.tsv`. Other
  agents own those files.
- Do not build the Sheep Ranch mini-game from the `BF_master` name. The name establishes the role;
  it does not describe the game.
- Do not add a database table for the CLIENT_START_ERROR de-dup. Twelve strings do not warrant one,
  and the existing `ponytail:` comment at `:32-34` already records the ceiling.
- Do not count the validator coverage number as a parity gap. It is a harness property and the
  client never sees it.
