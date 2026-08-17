#!/usr/bin/env python3
"""Build the phase-B per-archive merge inputs.

Emits, per archive, into <OUT>/lists/:
  <A>.paths.txt   the manifest for `WzMerge merge` (force rows, then removed, then protect)
  <A>.force.txt   the force roots (live-edited LIVE-ONLY images minus the removed-set overlap)
and one <OUT>/lists/MANIFEST.tsv recording set membership for every row.
"""
import os, sys, collections

ROOT  = r"D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\wz-baseline"
OUT   = sys.argv[1] if len(sys.argv) > 1 else os.path.join(os.path.dirname(os.path.abspath(__file__)), "lists")
DENY  = os.path.join(ROOT, "merge-lists", "COLLISION-DENY.txt")
ARCHIVES = ["Base","Character","Effect","Etc","Item","Map","Mob","Morph","Npc","Quest",
            "Reactor","Skill","Sound","String","TamingMob","UI"]
os.makedirs(OUT, exist_ok=True)

def rows(path):
    out = []
    if not os.path.exists(path): return out
    with open(path, encoding="utf-8-sig") as f:
        for ln in f:
            s = ln.strip()
            if s and not s.startswith("#"): out.append(s)
    return out

def col0(p): return [r.split("\t")[0] for r in rows(p)]

deny_roots = [r.split("\t")[0].strip() for r in rows(DENY)]
def denied(p):
    for d in deny_roots:
        if p == d or p.startswith(d + "/") or d.startswith(p + "/"):
            return d
    return None

excluded = set(rows(os.path.join(ROOT, "backport", "removed-set-excluded.txt")))

# ---------- derive ----------
liveonly, conflict, removed, protect = {}, {}, {}, {}
for a in ARCHIVES:
    live = col0(os.path.join(ROOT, "modified-list", a + ".live.txt"))
    v84  = set(col0(os.path.join(ROOT, "modified-list", a + ".txt")))
    liveonly[a] = [p for p in live if p not in v84]
    conflict[a] = [p for p in live if p in v84]
    removed[a]  = rows(os.path.join(ROOT, "removed-list",  a + ".txt"))
    protect[a]  = rows(os.path.join(ROOT, "protect-list",  a + ".txt"))

allrem = set().union(*[set(v) for v in removed.values()])

summary = []
man = open(os.path.join(OUT, "MANIFEST.tsv"), "w", encoding="utf-8", newline="\n")
man.write("archive\tset\tdisposition\tpath\treason\n")

grand = collections.Counter()
for a in ARCHIVES:
    # --- force list: LIVE-ONLY minus rows that are really removed-set ---
    force = [p for p in liveonly[a] if p not in allrem]
    force_as_removed = [p for p in liveonly[a] if p in allrem]

    fset = set(force)
    def covered_by_force(p):
        return any(p.startswith(f + "/") for f in fset)

    # --- removed rows: drop the owner's own deletions, keep the rest ---
    rem_keep, rem_drop_excl, rem_drop_cov = [], [], []
    for p in removed[a]:
        if p in excluded:            rem_drop_excl.append(p)
        elif covered_by_force(p):    rem_drop_cov.append(p)
        else:                        rem_keep.append(p)

    # --- protect rows: drop ones already inside a forced image ---
    pro_keep, pro_drop_cov = [], []
    for p in protect[a]:
        if covered_by_force(p): pro_drop_cov.append(p)
        else:                   pro_keep.append(p)

    paths = force + rem_keep + pro_keep
    # dedupe, preserving order
    seen, dedup, dups = set(), [], 0
    for p in paths:
        if p in seen: dups += 1; continue
        seen.add(p); dedup.append(p)
    paths = dedup

    dn = [(p, denied(p)) for p in paths]
    n_denied = sum(1 for _, d in dn if d)

    if paths or force:
        with open(os.path.join(OUT, a + ".paths.txt"), "w", encoding="utf-8", newline="\n") as f:
            f.write(f"# {a}.wz phase-B backport manifest: {len(force)} live-edited (forced) "
                    f"+ {len(rem_keep)} removed + {len(pro_keep)} protect = {len(paths)} rows\n")
            for p in paths: f.write(p + "\n")
        if force:
            with open(os.path.join(OUT, a + ".force.txt"), "w", encoding="utf-8", newline="\n") as f:
                f.write(f"# {a}.wz force roots: {len(force)} live-edited stock images "
                        f"whose content the owner changed. Subtree-replace from the live client.\n")
                for p in force: f.write(p + "\t# live-edited: restore the owner's image over v84's\n")

    for p in force:          man.write(f"{a}\tlive-edited\tFORCE\t{p}\t\n")
    for p in rem_keep:       man.write(f"{a}\tremoved\tADD\t{p}\t\n")
    for p in pro_keep:       man.write(f"{a}\tprotect\tADD\t{p}\t\n")
    for p in force_as_removed: man.write(f"{a}\tremoved\tADD(reclassified from live-edited)\t{p}\tabsent from v84 base entirely\n")
    for p in rem_drop_excl:  man.write(f"{a}\tremoved\tSKIP\t{p}\towner deleted this himself\n")
    for p in rem_drop_cov:   man.write(f"{a}\tremoved\tSKIP\t{p}\tinside a forced image, arrives with it\n")
    for p in pro_drop_cov:   man.write(f"{a}\tprotect\tSKIP\t{p}\tinside a forced image, arrives with it\n")
    for p in conflict[a]:    man.write(f"{a}\tlive-edited-CONFLICT\tHOLD\t{p}\tv84 also edited it - needs a decision\n")

    grand["force"] += len(force); grand["rem"] += len(rem_keep); grand["pro"] += len(pro_keep)
    grand["rem_cov"] += len(rem_drop_cov); grand["pro_cov"] += len(pro_drop_cov)
    grand["rem_excl"] += len(rem_drop_excl); grand["conflict"] += len(conflict[a])
    grand["denied"] += n_denied; grand["rows"] += len(paths); grand["dups"] += dups

    if paths:
        summary.append((a, len(force), len(rem_keep), len(pro_keep), len(paths), n_denied))

man.close()
print(f"{'archive':<11}{'force':>7}{'removed':>9}{'protect':>9}{'ROWS':>8}{'deny-hit':>10}")
for s in summary:
    print(f"{s[0]:<11}{s[1]:>7}{s[2]:>9}{s[3]:>9}{s[4]:>8}{s[5]:>10}")
print(f"{'TOTAL':<11}{grand['force']:>7}{grand['rem']:>9}{grand['pro']:>9}{grand['rows']:>8}{grand['denied']:>10}")
print()
print(f"  removed rows skipped (owner's own deletions)      : {grand['rem_excl']}")
print(f"  removed rows skipped (inside a forced image)      : {grand['rem_cov']}")
print(f"  protect rows skipped (inside a forced image)      : {grand['pro_cov']}")
print(f"  duplicate rows removed                            : {grand['dups']}")
print(f"  CONFLICT rows held back for a decision            : {grand['conflict']}")
print()
print(f"  accounting: removed  {grand['rem']} + {grand['rem_excl']} + {grand['rem_cov']} "
      f"= {grand['rem']+grand['rem_excl']+grand['rem_cov']} (manifest says 3969)")
print(f"  accounting: protect  {grand['pro']} + {grand['pro_cov']} = {grand['pro']+grand['pro_cov']} (manifest says 17569)")
print(f"  accounting: forced   {grand['force']} + reclassified-to-removed = 6112 live-only rows")
print(f"\nlists in {OUT}")
