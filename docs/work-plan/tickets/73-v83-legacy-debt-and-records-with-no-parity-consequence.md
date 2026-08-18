> **REFUSED by the owner, 2026-08-18.** The whole ticket is v83-legacy debt and record-keeping with no
> parity consequence.

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
in `tools/v84/decode-models-v84.tsv` plus 6 in `tools/v84/decode-models-v84-binary.tsv`** = 35 rows,
which is **33 distinct opcodes**.

The collapse is **-2, not -3**: the three `DROP_ITEM_FROM_MAPOBJECT/{spawn-item, spawn-meso,
update-item}` rows are one opcode. The other two slashed names - `KILL_MONSTER/normal` and
`SKILL_LEARN_ITEM_RESULT/result` - are single rows each and collapse with nothing. 35 - 2 = 33. The
old "once the three per-mode variants collapse" reached the right number by the wrong arithmetic.

The candidate-row filter is `PacketStructureModels.java:75`
(`src/main/java/tools/packetvalidator/PacketStructureModels.java`; note the `src/main/java/` prefix -
`tools/` also exists at repo root and is a different directory).

**The `loadAll` javadoc is `:46-50`, and the old tracker was RIGHT.** `/**` is on line 46, `*/` on
line 50, the signature on 51; line 45 is blank. An earlier revision of this ticket "corrected" it to
`:45-49`, which is off by one, and that error was propagated into `V84-WORK-ROWS.tsv:33` - now fixed
there too. Nothing about `:46-50` needed correcting in the first place.

This is a **test-harness property, not a parity defect**. A low coverage number does not misbehave
against a client; it only means fewer packets are checked offline. Closing it means writing 274 more
models.

The named next step, if it is ever taken, is per-mode models for the mode-dispatched opcodes.
**The "90 mode-byte opcodes" figure is unsourced** - it appears twice in the tree, as a bare bullet
at `41-binary-derived-packet-models.md:237` and as a restatement in `V84-OPEN-ITEMS.md:168`, and
neither `derive-decode-models.py` nor `derive-binary-models.py` counts or emits it. Do not quote it
as a target until someone derives it.

## R33 - CLIENT_START_ERROR de-dup is in-memory

`src/main/java/tools/packetvalidator/ClientStartErrorHandler.java`: `SEEN_LIMIT=512` at `:30`, the
seen set at `:35` carrying an existing `ponytail:` comment at `:32-34`, and the bounded LRU at
`:38-46`. (All four line numbers verified. Note the `src/main/java/` prefix.)

Steady state **within one boot is already correct**. The only symptom is that the first reconnect
after every restart re-warns.

**The size of that symptom is 3, not 12.** The one capture on record
(`40-packet-error-detection.md:46`, `:56`) is "12 entries, only **3 distinct**: 5 x v83/11001 (DNS
noise from before the cutover) and 7 x v84/error 38". De-dup keys on the exact line text, so the set
that would need persisting is the **3 distinct** strings. An earlier revision of this row quoted 12
in both places and overstated the problem 4x. It is also a **single capture**, not a steady-state
measurement - do not treat 3 as a stable number either.

The cheapest real fix is a flat file beside the log, about 15 lines. It has no precedent in this
repo - everything else here is DB or WZ - and a table is not warranted for three strings. No
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
- **The container mechanism is real but describes a different, smaller set.** **Six** weapons and one
  cape, all `reqLevel 105`, come from the five Dragon Rider's `<job>` Box items **2022652-2022656**
  (`Dragon Rider's Warrior / Magician / Thief / Bowman / Pirate Box`):

  | item | name | from box |
  |---|---|---|
  | 1402073 | Askaron | 2022652 Warrior |
  | 1432066 | Bellum Spear | 2022652 Warrior |
  | 1442090 | Machlear | 2022652 Warrior |
  | 1332100 | Nageling | 2022654 Thief |
  | 1462076 | Inferna | 2022655 Bowman |
  | 1482051 | Crusio | 2022656 Pirate |
  | 1102231 | Sirius Cape | 2022652 / 653 / 654 / 655 |

  **1382092 Circle-Winded Staff is NOT in this set.** It is `reqLevel 30`, not 105, and it comes from
  cash coupon **5530027** (`Item.wz/Cash/0553.img.xml`, `05530027/reward/0/item = 1382092`,
  `prob 100`) - no box contains it; the Magician box 2022653 carries 1382035 and 1382058 instead. An
  earlier revision counted it as a seventh box weapon at `reqLevel 105` and was wrong twice over.

The set that actually fits the words "endgame weapons" is the **6 Neo weapons**: **1372059**
(`reqLevel 83`), **1402074**, **1442091**, **1452090**, **1472101**, **1482052** (all `reqLevel 85`).

**They are not unreferenced.** `wz/String.wz/MonsterBook.img.xml:15182-15187` lists all six
consecutively, as entries 30-35 of mob **8220013 Nibelung** (`String.wz/Mob.img.xml:4851-4852`) -
i.e. the client's own Monster Book advertises them to the player as Nibelung drops.

The correct statement is narrower and worse: **the client advertises them and the server implements
nothing.** Zero references across `src/`, `scripts/` and `database/`; zero `drop_data` rows for any
of the six (`SELECT * FROM drop_data WHERE itemid IN (1372059,1402074,1442091,1452090,1472101,1482052)`
returns empty, while `dropperid = 8220013` has exactly one row, for something else). An earlier
revision said "nothing in the tree references them at all", which is false and which would have hidden
the visible-in-client half of the discrepancy.

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
- [ ] The tracker prose states the validator coverage as **33 of 307** (35 verified rows, minus 2 for
      the three `DROP_ITEM_FROM_MAPOBJECT` rows sharing one opcode) and cites the filter as
      `PacketStructureModels.java:75`. It must **not** claim the `loadAll` javadoc is `:45-49` - it is
      `:46-50`, which is what the tracker said originally. `V84-WORK-ROWS.tsv:33` is corrected as part
      of this ticket; no other row of that file is touched.
- [ ] The tracker prose states that the `013029xx` block is **17** ids, that they are GM test swords
      at `reqLevel 10`, and that the container set from 2022652-2022656 is **6 weapons + 1 cape**, all
      `reqLevel 105`. It must **not** list 1382092 among them: that is `reqLevel 30` and comes from
      coupon 5530027.
- [ ] The tracker prose names the 6 Neo weapons (1372059, 1402074, 1442091, 1452090, 1472101,
      1482052) as **advertised by the client's Monster Book as drops of mob 8220013 Nibelung**
      (`MonsterBook.img.xml:15182-15187`) while having **zero** server-side implementation - no
      reference in `src/`, `scripts/` or `database/`, and no `drop_data` row. It must not say they are
      referenced nowhere.
- [ ] `V84-OPEN-ITEMS.md` is the only tracker file this ticket's diff touches. `STATUS.md`,
      `V84-COVERAGE.md`/`.tsv` and `V84-WORK-ROWS.tsv` are owned by other agents and must not appear
      in the diff.
- [ ] If R33 is approved: after a restart, a CLIENT_START_ERROR already seen in the previous boot
      produces no second warning, asserted by a test that writes the persistence file and
      re-instantiates the handler. The tracker prose says **3 distinct** strings, not 12.
- [ ] If R32 is approved: the verified-model count in `decode-models-v84.tsv` plus `-binary.tsv`
      rises above 33 and the new rows pass `PacketStructureValidator` against captured bytes.

Run any test class with `-Dtest=<Name>`. **Do not run maven while sibling agents are active** - they
collide on `target/`.

## Do not

- Do not edit `STATUS.md`, `V84-COVERAGE.md`, `V84-COVERAGE.tsv` or `V84-WORK-ROWS.tsv`. Other
  agents own those files.
- Do not build the Sheep Ranch mini-game from the `BF_master` name. The name establishes the role;
  it does not describe the game.
- Do not add a database table for the CLIENT_START_ERROR de-dup. Three distinct strings do not
  warrant one, and the existing `ponytail:` comment at `:32-34` already records the ceiling.
- Do not count the validator coverage number as a parity gap. It is a harness property and the
  client never sees it.
