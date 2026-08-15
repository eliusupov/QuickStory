# Ambiguous collision resolution — source evidence (ticket 03d)

Read-only analysis. No game data changed, no merge decision made here.
Both questions from `COLLISION-TRIAGE.md` §"ambiguous" (10 rows, 2 groups).

Every claim below is backed by `file:line` in this worktree
(`D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade`).

---

## Question 1 — `Character.wz/Dragon/019{4,5,6,7}2002.img/info/level` (4 rows)

### Answer

**Yes, the server reads it — but not as "dragon levelling". It reads it as generic
equip-levelling data, via the whole-of-`Character.wz` directory scan, and only for an
item that is actually in some character's EQUIPPED inventory.**

Second, and more decisive for the merge: **the local flat `exp=10000` table is not
tuning and not the client's original — it is a generated artifact of Cosmic's own
`EquipmentOmniLeveller` tool**, stamped across the entire `Character.wz` tree.

### Evidence chain

**1. `Character.wz` is loaded at runtime and `Dragon/` exists in the server's tree.**

- `src/main/java/provider/wz/WZFiles.java:10` — `CHARACTER("Character")` is a real
  provider entry; `WZFiles.java:38-45` resolves it to `wz/Character.wz` (or `-Dwz-path`).
- `src/main/java/server/ItemInformationProvider.java:147` —
  `equipData = DataProviderFactory.getDataProvider(WZFiles.CHARACTER);`
  This is the only runtime consumer. The other `WZFiles.CHARACTER` hits
  (`tools/mapletools/CashDropFetcher.java:276`, `EmptyItemWzChecker.java:243`,
  `EquipmentOmniLeveller.java:26`, `NoItemIdFetcher.java:216`, `NoItemNameFetcher.java:209`)
  are all offline `tools.mapletools` utilities, not server startup.
- `wz/Character.wz/Dragon/` exists in the runtime tree and contains
  `01942000..01972002.img.xml`.

**2. Nothing resolves a literal `Dragon` path — the directory is reached by a blind scan.**

- `src/main/java/server/ItemInformationProvider.java:315-322`:
  ```java
  root = equipData.getRoot();
  for (DataDirectoryEntry topDir : root.getSubdirectories()) {
      for (DataFileEntry iFile : topDir.getFiles()) {
          if (iFile.getName().equals(idStr + ".img")) {
              return equipData.getData(topDir.getName() + "/" + iFile.getName());
  ```
  `idStr = "0" + itemId`. So `getItemData(1942002)` matches `Dragon/01942002.img`
  incidentally — `Dragon` is never named in Java. Grep for `Dragon` across `src/` returns
  only `server.maps.Dragon` (the Evan summon map object) and `constants.skills.DragonKnight`;
  neither touches WZ.
- `src/main/java/server/maps/Dragon.java` (whole file, 63 lines) — the Evan dragon summon
  is packet-only: `PacketCreator.spawnDragon` / `moveDragon` / `removeDragon`. Zero WZ
  access, zero level/exp state. Dragon *summon* levelling is not server-computed at all.

**3. `info/level` is read by the equip-levelling system, and the node shape matches exactly.**

- `src/main/java/server/ItemInformationProvider.java:1908-1913`:
  ```java
  Data iData = getItemData(itemId);
  if (iData != null) {
      Data data = iData.getChildByPath("info/level");
      if (data != null) {
          equipLevelData = data.getChildByPath("info");
  ```
- `wz/Character.wz/Dragon/01942002.img.xml:23-27` — the node is
  `info` → `level` → `info` → `"1"` → `exp`. Exactly the path above. This is a live read,
  not a near-miss.
- `01942002.img.xml:12-15` — `islot="Tm"`, `tuc=3`, `incPDD=20`, `incMDD=30`, `incINT=6`,
  `cash=0`. It is a real Taming-Mob-slot equip, so it can occupy `InventoryType.EQUIPPED`.

**4. What the value actually controls.**

- `src/main/java/server/ItemInformationProvider.java:1922-1952` `getEquipLevel`:
  `data2.getChildren().size() <= 1` ends the level scan. With the local data, level `1`
  has exactly one child (`exp`) → **`getEquipLevel(id, true) == 1`** and
  **`getEquipLevel(id, false) == 1`**.
- `src/main/java/client/inventory/Equip.java:89` —
  `isElemental = (getEquipLevel(id, false) > 1)` → currently `false`.
- `src/main/java/client/inventory/Equip.java:637` —
  `equipMaxLevel = min(30, max(getEquipLevel(id, true), USE_EQUIPMNT_LVLUP))`
  → `min(30, max(1, 1)) = 1` with `config.yaml:393 USE_EQUIPMNT_LVLUP: 1`
  → `Equip.java:638` returns immediately, no exp, no levels.
- `src/main/java/server/ItemInformationProvider.java:1954-1959` `getItemLevelupStats` is the
  consumer of the per-level `incSTR/DEX/...` children (`Equip.java:541`).

  **So today these four imgs are inert in effect** (read, but yield "not levellable").
  With v84's data — level `1` gains `incSTR/DEX/INT/LUK/PDD/MDD Min/Max` + `case/0/prob`,
  i.e. `getChildren().size() > 1` — the same code flips to
  `isElemental = true` and `equipMaxLevel = 30`. The item becomes a levelling equip that
  gains exp on every kill and grants stats.

**5. Reachability of the exp-gain path.**

- `src/main/java/client/Character.java:9990-10005` `getUpgradeableEquipList()` — source is
  `getInventory(InventoryType.EQUIPPED).list()`, filtered to non-cash
  (`config.yaml:390 USE_EQUIPMNT_LVLUP_CASH: false`; the dragon img has `cash=0`, so it passes).
- `src/main/java/client/Character.java:10007-10022` `increaseEquipExp` → `Equip.gainItemExp`.
- `src/main/java/client/inventory/Equip.java:633` `isUpgradeable` →
  `ItemInformationProvider.java:1728-1735` returns true if any stat > 0 or `tuc > 0`.
  `01942002` has `tuc=3` and three inc stats → **true**.
- `Character.java:10016-10019` skips items whose name is null;
  `wz/String.wz/Eqp.img.xml:22295` has `<imgdir name="1942002">` → name is non-null.

  Chain is complete: a worn `194x2002` with v84 level data *would* gain equip exp and levels.

**6. The local flat table is tool-generated, not tuning.**

- `src/main/java/tools/mapletools/EquipmentOmniLeveller.java:18-21` (javadoc):
  *"parses the Character.wz folder inputted and adds/updates the `info/level` node on
  every known equipment id."*
- `EquipmentOmniLeveller.java:35-36` — `FIXED_EXP = 10000`, `MAX_EQP_LEVEL = 30`.
- The four dragon imgs contain **exactly 30** `value="10000"` entries each
  (`01942002`, `01952002`, `01962002`, `01972002` — 30 / 30 / 30 / 30).
- The same signature appears on unrelated equips, e.g.
  `wz/Character.wz/Cap/01002357.img.xml` — 30 × `value="10000"`.
  (`Cap/01000000.img.xml` has 0, i.e. the tool skipped non-upgradeable/cash items.)

  A tool constant reproduced on 4 dragon imgs *and* on ordinary hats is a sweep, not a
  deliberate Evan decision.

### Implications for the merge (decision is the owner's)

- Adopting v84 here is **not** display-only. It changes `isElemental` and `equipMaxLevel`
  for `194x2002` from 1 → 30 and turns them into levelling equips.
- It is **not** overriding intentional Evan tuning either — the flat table is
  `EquipmentOmniLeveller` output applied tree-wide.
- The real consistency question: adopting v84 for these 4 imgs makes them the only equips
  in the tree with a genuine GMS level curve, while everything else keeps the tool's flat
  `10000`. Re-running `EquipmentOmniLeveller` after the merge would overwrite the v84 curve
  again ("adds/**updates**", `EquipmentOmniLeveller.java:19`) — so adopting v84 only sticks
  if that tool is not re-run over `Character.wz`.
- Blast radius on existing characters is bounded by "who currently wears a `194x2002`".

### Not determined

- **Whether any live character currently has `1942002/1952002/1962002/1972002` equipped.**
  Not answerable from source; it is a DB fact. Settle with:
  `SELECT COUNT(*) FROM inventoryequipment ie JOIN inventoryitems ii ON ii.inventoryitemid = ie.inventoryitemid WHERE ii.itemid IN (1942002,1952002,1962002,1972002);`
  If zero, adopting v84 changes nothing retroactively and only affects future Evan content.
- **Whether the v84 curve was itself produced by a similar sweep upstream.** Would need the
  v84 source tree, which is not present in this worktree
  (`porting-resources/wz-data` does not exist here).

---

## Question 2 — `Etc.wz/Commodity.img/894{1..6}` (6 rows)

### Answer

**The cash shop is NOT DB-driven for its catalogue — Cosmic reads `Commodity.img` from WZ at
startup, and `SN` is the live primary key, in Java and in SQL.**

And the specific finding that settles this group: **the v84 rows already exist verbatim in
the local `Commodity.img`, under different node names (`8848`–`8853`).** v84 vs. local is a
node *renumbering*, not a data disagreement. Adopting v84's `894{1..6}` would inject
duplicate `SN`s and drop six pet SNs.

### Evidence chain

**1. `Commodity.img` is read from WZ at server startup.**

- `src/main/java/server/CashShop.java:239-251` (`CashItemFactory.loadAllCashItems`):
  ```java
  DataProvider etc = DataProviderFactory.getDataProvider(WZFiles.ETC);
  for (Data item : etc.getData("Commodity.img").getChildren()) {
      int sn = DataTool.getIntConvert("SN", item);
      ...
      loadedItems.put(sn, new CashItem(sn, itemId, price, period, count, onSale));
  }
  ```
- `wz/Etc.wz/Commodity.img.xml` exists in the runtime tree.
- Only `specialcashitems` comes from SQL (`CashShop.java:266-275`), and it stores an `sn`
  that is looked up against the WZ-loaded map. There is **no** commodity/cash-item table:
  `src/main/resources/db/tables/` and `db/data/` contain no commodity or cashitem schema.

**2. The map key is `SN`, not the node name.**

- `CashShop.java:249` — `loadedItems.put(sn, ...)`. The node names `8941`, `8848`, … are
  never used. Two nodes with the same `SN` silently collide in the `HashMap`; last one
  parsed wins.
- `CashShop.java:294-296` — `getItem(int sn)` is the only lookup.

**3. `SN` is a persisted key in SQL — a missing SN is a live failure, not inert data.**

- `src/main/resources/db/tables/014-gift.sql:7` — `sn INT UNSIGNED NOT NULL`.
- `src/main/java/server/CashShop.java:440-441`:
  ```java
  CashItem cItem = CashItemFactory.getItem(rs.getInt("sn"));
  Item item = cItem.toItem();
  ```
  Unguarded. A `gifts` row whose SN no longer exists in `Commodity.img` NPEs on gift load.
- `src/main/java/server/CashShop.java:117` — `SELECT \`sn\` FROM \`wishlists\` WHERE \`charid\` = ?`
  — wishlists persist SNs too.
- `src/main/resources/db/data/141-specialcashitems-data.sql` — one row, `sn = 10000617`.
  Not in either contested range.

**4. Nothing in Java, SQL, or scripts pins SNs `60001000`–`60001005`.**

- Grep for each of `60001000`–`60001005` across `src/` and `scripts/`: **zero hits.**
  (All apparent matches are map ids `600010xx` in `scripts/event/Subway.js`,
  `scripts/npc/9201057.js`, etc. — different numbers, `6000100xx` vs `600010xxx`.)
- Grep for `60001000` across `src/main/resources/db/`: **zero hits.**
- The pet ids are not pinned by SN either; the only one referenced in code is
  `src/main/java/constants/id/ItemId.java:199` `PERMA_PINK_BEAN = 5000060`, used via
  `getPermaPets()` (`ItemId.java:204`, `constants/inventory/ItemConstants.java:260`) —
  keyed on **itemId**, never on SN.

  So no *static* pin exists. The exposure is entirely dynamic: `gifts` / `wishlists` rows
  written while those SNs were purchasable.

**5. The decisive finding — the v84 rows are already present locally.**

`wz/Etc.wz/Commodity.img.xml:91011-91070` contains nodes `8848`–`8853`:

| node | SN | ItemId | Price | OnSale |
|---|---|---|---|---|
| 8848 | 70000365 | 9102234 | 11000 | 0 |
| 8849 | 70000366 | 9102235 | 11400 | 0 |
| 8850 | 70000367 | 9102236 | 11000 | 0 |
| 8851 | 70000368 | 9102237 | 11400 | 0 |
| 8852 | 70000369 | 9102238 | 11000 | 0 |
| 8853 | 70000370 | 9102190 | 7000  | 1 |

That is byte-for-byte the payload the triage lists on the v84 side of `894{1..6}`
(`SN=70000365..70000370`, items `9102234-38` + `9102190`, five of six `OnSale=0`).
A grep for `value="7000036[5-9]"|value="70000370"` in the local file returns exactly these
six lines and no others (`Commodity.img.xml:91012, 91022, 91032, 91042, 91052, 91062`).

Meanwhile `Commodity.img.xml:91608-91617` holds node `8941`:
`SN=60001000 ItemId=5000013 Price=5900 Period=90 OnSale=1` — the pet row.

**Interpretation (inference, clearly labelled):** the live client's `Commodity.img` is a
later revision in which those six cash entries were moved from slot `894{1..6}` to
`884{8..53}`, and `894{1..6}` was reused for the six 90-day pets. v84 predates the move.

### Implications for the merge (decision is the owner's)

Adopting v84 for `894{1..6}` would, at `CashShop.java:249`:

1. Insert `SN=70000365..70000370` **a second time**, colliding with nodes `8848`–`8853`
   in the same `HashMap`. Values are identical, so the collision is currently harmless —
   but it is a silent duplicate key, and it makes the two node groups diverge-prone.
2. **Remove `SN=60001000..60001005` from the map entirely** — the pets would no longer
   resolve. The client renders the cash shop from its *own* `Etc.wz`, which still lists
   them, so a player clicking one hits `CashItemFactory.getItem(sn) → null`
   (`CashOperationHandler.java:80, 116, 149, 178, 222, 247, 318, 354, 381, 407, 436`), and
   any existing `gifts` row on those SNs NPEs at `CashShop.java:441`.

Keep-local loses nothing: the v84 payload is already in the tree under `8848`–`8853`.

### Not determined

- **Whether any `gifts` or `wishlists` row currently references `60001000`–`60001005`.**
  DB fact, not a source fact. Settle with:
  `SELECT sn, COUNT(*) FROM gifts WHERE sn BETWEEN 60001000 AND 60001005 GROUP BY sn;`
  and the same against `wishlists`. Non-empty result upgrades "adopting v84 breaks the shop
  listing" to "adopting v84 throws on character load".
- **Whether the live client's `Etc.wz` and this repo's `wz/Etc.wz` are in sync.** Assumed
  per the triage's framing ("live value"), not re-verified here; the client copy is
  read-only and out of scope for this ticket.

---

## Summary

| Group | Question | Answer | Confidence |
|---|---|---|---|
| Q1 (4 rows) | Does the server read `Character.wz/Dragon/*/info/level`? | **Yes**, via `ItemInformationProvider.getItemData` → `getEquipLevelInfo`, as generic equip-levelling. Not display-only. | Proven from call sites |
| Q1 | Is the local flat `exp=10000` deliberate tuning? | **No** — `EquipmentOmniLeveller` output (`FIXED_EXP=10000`, `MAX_EQP_LEVEL=30`, 30 entries, same signature on unrelated equips). | Proven |
| Q1 | Does adopting v84 change existing characters? | Only for characters wearing `194x2002`. Count is a DB fact, unverified. | Mechanism proven, blast radius open |
| Q2 (6 rows) | Is the cash shop DB-driven? | **No** — `Commodity.img` is read from WZ at startup; `SN` is the `HashMap` key and a persisted SQL key in `gifts`/`wishlists`. | Proven from call sites + schema |
| Q2 | Is anything pinned to SN `60001000`–`60001005`? | Nothing static in Java, SQL, or scripts. Dynamic exposure via `gifts`/`wishlists` only. | Proven for static; DB unverified |
| Q2 | What is the actual v84-vs-local delta? | A node renumbering — v84's payload already exists locally as nodes `8848`–`8853`. Adopting duplicates six SNs and drops six pet SNs. | Proven |
