# 02g — deep manifest rows land end to end (verbatim)

The diff tool now expands every image 3 levels, so manifests carry rows like
`String.wz/Eqp.img/Eqp/Hair/31991`. `WzMerge xml` used to refuse anything more than one level
below the `.img`, which would have made those rows import **nothing**. Both sides moved together;
this is the proof that they agree, run against a scratch copy of `wz/String.wz`, never the repo tree.

## Recipe

```
cp -r wz/String.wz $SCRATCH/xmlproof/
WzMerge.exe xml <v84>/String.wz $SCRATCH/xmlproof $SCRATCH/deep-proof.txt $SCRATCH/deep-proof.conflicts.txt
diff -u wz/String.wz/<img>.xml $SCRATCH/xmlproof/String.wz/<img>.xml
```

`deep-proof.txt` — one row per newly-visible shape, plus rows that MUST be refused:

```
String.wz/Eqp.img/Eqp/Hair/31991          # 4 segments below the .wz, 3 below the .img
String.wz/Etc.img/Etc/4000566             # 2 below the .img
String.wz/Map.img/etc/900010000           # 2 below the .img
String.wz/Eqp.img/Eqp/Hair/30000          # exists in the target -> must be refused
String.wz/Eqp.img/Eqp/Hair/31660          # v84 add, but already in the live-derived tree
String.wz/Eqp.img/Eqp/NoSuchCategory/12345 # not in the source -> must be refused
```

## Output

```
source <v84>/String.wz iv=GMS; xml root $SCRATCH/xmlproof
  ADD   String.wz/Eqp.img/Eqp/Hair/31991 -> $SCRATCH/xmlproof\String.wz\Eqp.img.xml:12904 (3 lines)
  ADD   String.wz/Etc.img/Etc/4000566 -> $SCRATCH/xmlproof\String.wz\Etc.img.xml:4 (4 lines)
  ADD   String.wz/Map.img/etc/900010000 -> $SCRATCH/xmlproof\String.wz\Map.img.xml:7571 (4 lines)
  SKIP  String.wz/Eqp.img/Eqp/Hair/30000  (already exists in ...\Eqp.img.xml)
  SKIP  String.wz/Eqp.img/Eqp/Hair/31660  (already exists in ...\Eqp.img.xml)
  SKIP  String.wz/Eqp.img/Eqp/NoSuchCategory/12345  (MISSING IN SOURCE - manifest is stale)
added 3, refused 3
```

The gate fires **at depth**, not just at the root: `Hair/30000` and `Hair/31660` are refused because
those ids already exist inside `Eqp/Hair`, three levels down. `31660` is one of the nine v84 hair ids
the live client already occupies, so it also confirms the server XML tree is live-derived, not stock v83.

## The resulting diffs — 3 files, 11 insertions, 0 deletions

```diff
--- wz/String.wz/Eqp.img.xml
+++ $SCRATCH/xmlproof/String.wz/Eqp.img.xml
@@ -12901,6 +12901,9 @@
       <imgdir name="30997">
         <string name="name" value="Brown Tentacle Hair"/>
       </imgdir>
+      <imgdir name="31991">
+        <string name="name" value="Red Evan Hair (F)"/>
+      </imgdir>
       <imgdir name="33000">

--- wz/String.wz/Etc.img.xml
+++ $SCRATCH/xmlproof/String.wz/Etc.img.xml
@@ -1,6 +1,10 @@
 <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
 <imgdir name="Etc.img">
   <imgdir name="Etc">
+    <imgdir name="4000566">
+      <string name="name" value="Onyx Dragon Footprint"/>
+      <string name="desc" value="A footprint left behind by an Onyx Dragon."/>
+    </imgdir>
     <imgdir name="4001257">

--- wz/String.wz/Map.img.xml
+++ $SCRATCH/xmlproof/String.wz/Map.img.xml
@@ -7568,6 +7568,10 @@
   <imgdir name="etc">
+    <imgdir name="900010000">
+      <string name="streetName" value="Dream World"/>
+      <string name="mapName" value="Dream Forest Entrance"/>
+    </imgdir>
     <imgdir name="926100000">
```

Each node lands **inside its real parent** at the right indent, in sorted position, with no
reformatting anywhere else in the file. `Eqp.img.xml` is 835 KB and one hunk changed.

## Binary side

No change was needed: `WzMerge merge` resolves paths segment by segment and adds through
`IPropertyContainer.AddProperty`, so depth was never the constraint there. The full add-list dry-run
sweep exercises it — `String.wz` reports `added 868, refused 711` over a manifest whose rows are
now up to 4 segments deep, with zero `unsupported shape` refusals.
