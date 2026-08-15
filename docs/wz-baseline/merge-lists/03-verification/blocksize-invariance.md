# What a merge is actually proven to preserve

Re-measured 2026-08-16 (ticket 03b). This file replaces an earlier version that claimed every
non-target image "survived a full MapleLib repack byte-for-byte the same size". That claim was
not wrong so much as **not a measurement** — see "The BlockSize table proves less than it looks
like" below. The mechanism is stronger than the old evidence and the old evidence could not have
failed, so both are stated plainly here.

Three independent checks, in descending order of strength:

| # | check | covers | can it fail? |
|---|---|---|---|
| 1 | **content digest of the re-serialized image**, pre vs post | the one image the merge rewrote — the only place a serializer bug can live | yes, and it is the check that would catch it |
| 2 | verbatim-memcpy argument from MapleLib source | every *other* image in the file | no — it is a source-code argument, not a measurement |
| 3 | BlockSize table from the diff tool | node counts, additions, deletions | partly — see below |

---

## 1. Content digest — the check that can fail (`WzMerge hash`)

`WzMerge hash <wz> <path>` walks a node, digests the **decoded** value of every descendant
(scalars via their parsed value, canvases via SHA-256 of their compressed pixel bytes), and
prints one SHA-256 per direct child plus a total. Run it on the same image before and after the
merge and diff the two listings: any child whose decoded content changed shows up by name.

This is the check the old BlockSize table could not be: the merge sets `Changed = true` on
exactly the image it inserted into (`tool-merge/Program.cs`, the `pi.Changed = true` /
`ParentImage.Changed = true` lines), so that image — and only that image — is decoded,
re-serialized and re-encrypted under the target's IV. Everything that could go wrong in
`DeepClone` → serializer → `WzBinaryWriter` goes wrong *there*, and BlockSize says nothing
about it beyond "the length changed, as expected".

### Item.wz — `Consume/0200.img`, 56 children before, 57 after

```
> WzMerge hash <pre>/Item.wz Consume/0200.img       -> 56 child digests
> WzMerge hash <post>/Item.wz Consume/0200.img      -> 57 child digests
> Compare-Object pre post

=> 4f42f3d5a333b2697ed80ae667bcbc5e6e800f8c2e9c53a58988c3c39cbc4e3b  02001500
=> 73a131aac5f9a1083327386d406dd8d4b66d1bfa2bced4af1a91b4b1b388914f  TOTAL Consume/0200.img (post)
<= 4bf094c119159169d31cd3415086a7836be0da37df5b45a54c25717310197d01  TOTAL Consume/0200.img (pre)
```

**55 of 56 pre-existing children are digest-identical across the repack.** The only new line is
the imported node. (The TOTAL differs on both sides because it covers the whole image, which
gained a child — that is the expected difference, not a finding.)

### String.wz — `Consume.img`, 2,291 children before, 2,292 after

```
=> 090f56bde57502f7f6c06cf7eeee5d3f7aee8632a9de8f10f28c2faf49ec6abe  2001500
=> 5d4e7eb2f7dbd5b288772e897649903deaab48e80d46368fdf13be1d0f3fa0c0  TOTAL Consume.img (post)
<= 05283aa6e9861587a8b485fce0718e896f56cae390d80af24cfee65ccdb12fbe  TOTAL Consume.img (pre)
```

**2,290 of 2,291 pre-existing children digest-identical.**

### And the imported node is identical to its v84 source

```
> WzMerge hash <v84>/Item.wz   Consume/0200.img | findstr 02001500
4f42f3d5a333b2697ed80ae667bcbc5e6e800f8c2e9c53a58988c3c39cbc4e3b  02001500     <- v84 source
4f42f3d5a333b2697ed80ae667bcbc5e6e800f8c2e9c53a58988c3c39cbc4e3b  02001500     <- v83 output

> WzMerge hash <v84>/String.wz Consume.img | findstr "  2001500"
090f56bde57502f7f6c06cf7eeee5d3f7aee8632a9de8f10f28c2faf49ec6abe  2001500      <- v84 source
090f56bde57502f7f6c06cf7eeee5d3f7aee8632a9de8f10f28c2faf49ec6abe  2001500      <- v83 output
```

Same digest on both sides, including the compressed PNG payloads. So the v84 → DeepClone →
v83-IV re-serialization round-trips the decoded content exactly. That is the version-trap claim
("the output is v83-encoded because its target always was") turned into a measurement of the
*content*, not just of the header.

### What this still cannot prove

- **It does not cover images the merge did not touch.** Those are check 2, which is an argument
  rather than a measurement. Running `hash` over a whole `.wz` would cover them, but it decodes
  every image in the file: seconds for `String.wz`, and not a sane proposition for `Map.wz`.
- **It compares decoded values, not the serialized bytes**, so a difference that both encoders
  round-trip identically is invisible to it by construction. That is the intended blind spot —
  raw block bytes embed absolute offsets and would report a difference for every image after an
  insertion.
- **Leaf values are digested via their parsed representation.** For the property types these
  manifests carry (int/short/long/float/double/string/uol/vector/canvas) that is the real value.
  A type whose parsed form loses information would be under-checked; none is in use here.
- It says nothing about whether the client *likes* the file. That is the human step in ticket 03.

## 2. Every other image: verbatim memcpy, argued from MapleLib source

`WzDirectory.SaveImages` (MapleLib `WzLib/WzDirectory.cs:348-357`):

```csharp
if (img.Changed) { fs.Position = img.tempFileStart; CopyBytes(fs, wzWriter, img.size); }
else            { img.reader.BaseStream.Position = img.tempFileStart;
                  CopyBytes(img.reader.BaseStream, wzWriter, img.tempFileEnd - img.tempFileStart); }
```

An unchanged image is never decoded, never re-encrypted and never re-serialized: its bytes are
copied straight out of the still-open reader on the **input** file into the output. That is a
stronger guarantee than "same size" — it is "same bytes" — but it comes from reading the source,
not from anything this pipeline measures.

It also means the BlockSize of an untouched image cannot possibly differ, which is why check 3
below is weaker than it looks.

## 3. The BlockSize table proves less than it looks like

Do not repeat the old claim. For every image except the one inserted into, the `else` branch
above copies bytes verbatim and `size` is carried straight off the input directory entry — it is
never recomputed. So comparing pre-BlockSize with post-BlockSize for those images compares a
number with a copy of itself. **The comparison is structurally incapable of failing**, and its
passing is not evidence.

What the diff tool run *does* establish, and is worth running:

- `add-list` must contain **exactly** the paths you asked for — a node landing somewhere
  unintended shows up here.
- `removed-list` must be **empty** — nothing was dropped by the repack.
- `modified-list/<wz>.txt` must contain **only** the image(s) you inserted into. An entry for any
  other image would mean an image was re-serialized that should not have been (`Changed` set
  somewhere unexpected), which *is* a real failure this can catch.

### The invocation, with its arguments labelled honestly

The diff tool takes **four directories, positionally**, and `SUMMARY.md` always labels its
columns `v83-stock` / `v84` / `live client` no matter what you actually pass:

```
dotnet run -c Release --project docs/wz-baseline/tool -- <outDir> <v83Dir> <v84Dir> <liveDir>
```

For merge verification: `v83Dir` = the **pre-merge staging copy**, `v84Dir` = the **post-merge
output**, `liveDir` = the pre-merge copy again (there is no third tree, and passing the real
client directory would parse all 12 GB of it). Consequence, stated so nobody quotes these as
findings: with those roots the `protect` column is structurally 0, and `modified-list/<wz>.live.txt`
is a tree compared with itself and is structurally empty. **Read only `add-list`, `removed-list`
and `modified-list/<wz>.txt`.** `<wz>.live.txt` is retained only as a canary — a non-empty one
would mean the harness is comparing the wrong roots.

Pick an `<outDir>` outside `docs/wz-baseline/`, or the run overwrites the committed manifests.

### Verbatim run — 2026-08-16, pre/ vs 03b/

```
Roots: v83=`.../wz-merge/pre` v84=`.../wz-merge/03b` live=`.../wz-merge/pre`

| wz | v83-stock | v84 | live client | add | removed | protect | modified v83→v84 | modified v83→live |
|---|---|---|---|---|---|---|---|---|
| Item.wz   |  90,892 |  90,900 |  90,892 | 1 | 0 | 0 | 1 | 0 |
| String.wz | 113,140 | 113,143 | 113,140 | 1 | 0 | 0 | 1 | 0 |

525 images parsed, **0 parse failures**.
```

Path counts are much larger than ticket 03 recorded (7,361 / 12,859) because the manifest
expansion depth was increased afterwards; the *deltas* are what matter and they are unchanged.

```
add-list/Item.txt        Item.wz/Consume/0200.img/02001500
add-list/String.txt      String.wz/Consume.img/2001500
removed-list/Item.txt    (empty)
removed-list/String.txt  (empty)
modified-list/Item.txt   Item.wz/Consume/0200.img   53447  54270
modified-list/String.txt String.wz/Consume.img     329281 329327
modified-list/*.live.txt (empty — pre compared with itself, see above)
```

The two `modified-list` rows are the images that were inserted into, and nothing else appears.
The size deltas are `+823` and `+46` bytes; those are *observations*, not a check — nothing
computes what the tracer ought to serialize to, so the numbers cannot be confirmed by arithmetic.
Check 1 is what confirms the content.

## 4. Determinism, and the safety changes did not alter output

The merge was re-run from the pristine `pre/` copies by the hardened tool (ticket 03b: staging
guards, `.partial` + verify + move, post-write reparse) and produced files **byte-identical** to
ticket 03's:

```
115FEAC165175297CE947015D951395522528BAA97A2121A2D87DB1A587EABDE  03b/Item.wz   == post/Item.wz
D5721DE27F6C4742AAF2216EEC16E10F68D3E1E798C1D8126CE6EB0BC1AA8FBD  03b/String.wz == post/String.wz
```

The XML side likewise: stripping `02001500` out of `wz/Item.wz/Consume/0200.img.xml`, re-running
`WzMerge xml`, and hashing gives back the committed file exactly.

Guard evidence for the merge safety rules is in `safety-guards.md`; gate-refusal evidence and the
input/output SHA-256 list are in `gate-fires.md`.
