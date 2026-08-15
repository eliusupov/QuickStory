# Ticket 03 — proof the additive-only gate actually fires

Re-runs the tracer merge with the ALREADY-MERGED tree as its target. Every path now
exists, so a working gate must refuse all of them and write no node. Run:

    WzMerge merge <v84>/Item.wz <post-merge>/Item.wz <throwaway>/Item.wz \
        docs/wz-baseline/merge-lists/03-tracer-Item.txt <conflicts>

## binary (.wz) side
```
    source D:/games/MapleStory/Server/porting-resources/wz-data/v84/Item.wz  iv=GMS patchVersion=84
    target D:/games/MapleStory/Server/wz-merge/post/Item.wz  iv=GMS patchVersion=83
    1 paths requested
      SKIP  Item.wz/Consume/0200.img/02001500  (already exists in target)
    saving D:/games/MapleStory/Server/wz-merge/gate-check/Item.wz at iv=GMS patchVersion=83 (inherited from target)
    conflicts: 1 -> D:/games/MapleStory/Server/wz-merge/gate-check/binary.conflicts.txt
    added 0, refused 1
```

conflicts.txt it produced:
```
# Item.wz: additive-only merge, D:/games/MapleStory/Server/porting-resources/wz-data/v84/Item.wz -> D:/games/MapleStory/Server/wz-merge/post/Item.wz, 0 nodes added, 1 refused
# Every row is a v84 node this merge REFUSED to write because the path already
# existed in the target. Additive-only is enforced in the write path, so this file
# is the exhaustive list of v84 changes that were dropped. Read it before shipping:
# a v84 EDIT to an existing node (renamed mob, portal added to an existing map)
# looks exactly like this and is silently lost unless someone decides otherwise.
# Columns: path, reason.

Item.wz/Consume/0200.img/02001500	already exists in target
```

## XML side

Same shape, against the already-spliced server tree. Nothing is written: verified with
`git diff HEAD --stat wz/` immediately afterwards, which is empty (the working tree still
matches the committed merged state, so the second run added nothing).
```
    source D:/games/MapleStory/Server/porting-resources/wz-data/v84/Item.wz iv=GMS; xml root D:/games/MapleStory/Server/Cosmic/.claude/worktrees/evan-dualblade/wz
      SKIP  Item.wz/Consume/0200.img/02001500  (already exists in D:/games/MapleStory/Server/Cosmic/.claude/worktrees/evan-dualblade/wz\Item.wz\Consume\0200.img.xml)
    conflicts: 1 -> D:/games/MapleStory/Server/wz-merge/gate-check/xml.conflicts.txt
    added 0, refused 1
```

## SHA-256 of the merge inputs and outputs

pre/ is the untouched copy of the live client; the backup and the live files still match
it, which is how 'the client was never modified' is checked rather than asserted.
```
33d7e2d8416a6523935e9fc933107ca3b66f6dde869667ffb0551746a36c5e44 *D:/games/MapleStory/Item.wz
9437deb8ce481dae4909097ebfb366d24baccd73d55d3ed00fa3198603cae499 *D:/games/MapleStory/String.wz
33d7e2d8416a6523935e9fc933107ca3b66f6dde869667ffb0551746a36c5e44 *D:/games/MapleStory/Server/_backup/client-v83-EzorsiaV2-2026-08-15/Item.wz
9437deb8ce481dae4909097ebfb366d24baccd73d55d3ed00fa3198603cae499 *D:/games/MapleStory/Server/_backup/client-v83-EzorsiaV2-2026-08-15/String.wz
33d7e2d8416a6523935e9fc933107ca3b66f6dde869667ffb0551746a36c5e44 *D:/games/MapleStory/Server/wz-merge/pre/Item.wz
9437deb8ce481dae4909097ebfb366d24baccd73d55d3ed00fa3198603cae499 *D:/games/MapleStory/Server/wz-merge/pre/String.wz
115feac165175297ce947015d951395522528baa97a2121a2d87db1a587eabde *D:/games/MapleStory/Server/wz-merge/post/Item.wz
d5721de27f6c4742aaf2216eec16e10f68d3e1e798c1d8126ce6eb0bc1aa8fbd *D:/games/MapleStory/Server/wz-merge/post/String.wz
```
