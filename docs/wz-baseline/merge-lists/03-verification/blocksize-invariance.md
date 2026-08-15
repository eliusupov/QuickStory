# Ticket 03 — BlockSize-invariance verification, verbatim tool output

Produced by re-running ticket 02's diff tool with the PRE-merge tree as its v83 root
and the POST-merge tree as its v84 root:

    dotnet run -c Release --project docs/wz-baseline/tool -- <out> \
        D:/games/MapleStory/Server/wz-merge/pre D:/games/MapleStory/Server/wz-merge/post \
        D:/games/MapleStory/Server/wz-merge/post

pre/ is a byte-identical copy of the live client's Item.wz and String.wz; post/ is what
WzMerge wrote. Read the three sections below as: what the merge added, what changed size,
and what disappeared. The third must be empty.

The check this is NOT: a presence diff. A destructive overwrite preserves paths, so only
the BlockSize columns below carry the proof. Known limit: a replacement that compresses to
the identical length is invisible to it.

## SUMMARY.md
```
# WZ baseline diff — machine-generated summary

Generated 2026-08-15 23:35:31
Roots: v83=`D:/games/MapleStory/Server/wz-merge/pre` v84=`D:/games/MapleStory/Server/wz-merge/post` live=`D:/games/MapleStory/Server/wz-merge/post`

`—` = not measurable (a required tree lacks this file). It never means zero.
Node counts are paths (directories + images + one level of sub-properties).

| wz | v83-stock | v84 | live client | add (v84−v83) | removed (v83−v84) | protect (live − (v83 ∪ v84)) | modified v83→v84 | modified v83→live | add bytes | protect bytes |
|---|---|---|---|---|---|---|---|---|---|---|
| Item.wz | 7,361 | 7,362 | 7,362 | 1 | 0 | 0 | 1 | 1 | 0 | 0 |
| String.wz | 12,859 | 12,860 | 12,860 | 1 | 0 | 0 | 1 | 1 | 0 | 0 |

## image parse status

525 images parsed, **0 parse failures**.
```

## add-list

### Item.txt
```
# Item.wz: nodes present in v84 and absent from v83-stock (1 copy roots, 1 paths)
# each path is a copy root: no listed path is an ancestor of another, so copying
# a listed path already covers everything under it. Do not re-copy children.

Item.wz/Consume/0200.img/02001500
```

### String.txt
```
# String.wz: nodes present in v84 and absent from v83-stock (1 copy roots, 1 paths)
# each path is a copy root: no listed path is an ancestor of another, so copying
# a listed path already covers everything under it. Do not re-copy children.

String.wz/Consume.img/2001500
```

## modified-list

### Item.live.txt
```
# Item.wz: images present in BOTH v83-stock and the live client whose BlockSize differs (live-side edits — treat as custom content, do not overwrite). Columns: path, v83 bytes, live bytes.

Item.wz/Consume/0200.img	53447	54270
```

### Item.txt
```
# Item.wz: images present in BOTH v83-stock and v84 whose WzImage.BlockSize differs (edited, not added). Columns: path, v83 bytes, v84 bytes.

Item.wz/Consume/0200.img	53447	54270
```

### String.live.txt
```
# String.wz: images present in BOTH v83-stock and the live client whose BlockSize differs (live-side edits — treat as custom content, do not overwrite). Columns: path, v83 bytes, live bytes.

String.wz/Consume.img	329281	329327
```

### String.txt
```
# String.wz: images present in BOTH v83-stock and v84 whose WzImage.BlockSize differs (edited, not added). Columns: path, v83 bytes, v84 bytes.

String.wz/Consume.img	329281	329327
```

## removed-list

### Item.txt
```
# Item.wz: nodes present in v83-stock and absent from v84 — deleted by the patch (0 roots, 0 paths). A wholesale file swap destroys any of these the live client still has. Each row is a root: everything under it went too.

```

### String.txt
```
# String.wz: nodes present in v83-stock and absent from v84 — deleted by the patch (0 roots, 0 paths). A wholesale file swap destroys any of these the live client still has. Each row is a root: everything under it went too.

```
