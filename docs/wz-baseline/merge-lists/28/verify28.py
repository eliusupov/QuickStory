#!/usr/bin/env python3
"""Ticket 28 - prove that NOTHING PRE-EXISTING CHANGED, and that exactly the
manifest landed.

Usage:
  python verify28.py [--rev <baseline-git-rev>]      default: HEAD

Two independent proofs per touched file, plus four self-checks that deliberately
break each proof and require it to notice. A check that can only print PASS is
not a check.

  PROOF 1 (parse).  Canonical record for EVERY node in the file:
        path -> (tag, all attributes, ordered child-name list)
     Duplicate sibling names are disambiguated by occurrence, so a duplicated
     block cannot hide behind its own key (ticket 33's Proof B hole).
     Assert: every baseline path is present post-merge with an IDENTICAL record
     (changed=0, missing=0), and the set of new paths is EXACTLY the subtrees of
     this file's manifest rows.  A node smuggled inside a pre-existing record is
     a new path that is not under a manifest row, so it fails.

  PROOF 2 (text).  Every line of the baseline must still be present in the merged
     file with at least the same multiplicity.  Independent of the XML parser;
     catches a deletion the parser might normalise away.

Read-only w.r.t. wz/ - it never writes there.
"""
import subprocess, sys, os, glob, collections
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
ARCHIVES = ("Effect", "Etc", "Map", "Mob", "Npc", "Quest", "Reactor", "Skill", "String")


def git(*args):
    return subprocess.run(["git", "-C", ROOT, *args], capture_output=True)


def blob(rev, relpath):
    """Baseline bytes for a repo-relative path, or None if it did not exist."""
    r = git("show", f"{rev}:{relpath}")
    return r.stdout if r.returncode == 0 else None


def records(xml_bytes):
    """path -> (tag, sorted attrs, ordered child names) for every node in the tree."""
    if xml_bytes.startswith(b"\xef\xbb\xbf"):
        xml_bytes = xml_bytes[3:]
    root = ET.fromstring(xml_bytes)
    out = {}

    def walk(el, path):
        kids = list(el)
        names, seen = [], collections.Counter()
        for k in kids:
            n = k.get("name")
            seen[n] += 1
            names.append(n if seen[n] == 1 else f"{n}#{seen[n]}")
        attrs = tuple(sorted((k, v) for k, v in el.attrib.items() if k != "name"))
        out[path] = (el.tag, attrs, tuple(names))
        for k, nm in zip(kids, names):
            walk(k, f"{path}/{nm}")

    walk(root, root.get("name") or "")
    return out


def manifest_rows():
    """{repo-relative xml path: [subpath-under-image or '' for a whole image]}"""
    per_file = collections.defaultdict(list)
    # Every *.paths.txt beside this script, not a hardcoded list: "Etc-appended" is not a
    # WzMerge manifest (it is the re-slotted cash-shop rows append-commodity.py wrote) and
    # "Etc-npcloc" holds only the rows that survived the deny-list, but here they are all
    # the same thing - the expected-new set. A file this loop misses shows up as
    # UNEXPECTED, which is the safe direction.
    for p in sorted(glob.glob(os.path.join(HERE, "*.paths.txt"))):
        for line in open(p, encoding="utf-8"):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            arch, rest = line.split(".wz/", 1)
            img, _, sub = rest.partition(".img/")
            if not _:
                img = rest[:-len(".img")] if rest.endswith(".img") else rest
                sub = ""
            per_file[f"wz/{arch}.wz/{img}.img.xml"].append(sub)
    return per_file


def check_file(relpath, subs, rev, log, mutate=None):
    """Returns (changed, missing, unexpected_new, expected_new, proof2_lost)."""
    base = blob(rev, relpath)
    with open(os.path.join(ROOT, relpath), "rb") as f:
        post = f.read()
    if mutate:
        post = mutate(post)
    if base is None:
        # whole new file: every path is new, and the manifest must claim the image
        assert "" in subs, f"{relpath} is new but no whole-image manifest row claims it"
        return 0, 0, 0, len(records(post)), 0

    rb, rp = records(base), records(post)
    imgname = os.path.basename(relpath)[:-len(".xml")]
    prefixes = [f"{imgname}/{s}" for s in subs if s]
    # The expectation set is derived from the same manifests that drove the merge, so it
    # must not be trusted blind: a row naming a path that ALREADY EXISTED would whitelist
    # everything injected inside a pre-existing record - precisely the section 4.5 hazard
    # this proof exists to catch. Every manifest row has to be new.
    stale = [q for q in prefixes if q in rb]
    assert not stale, (f"{relpath}: manifest rows name paths that already existed at the "
                       f"baseline, so they cannot be treated as expected-new: {stale[:5]}")
    missing = [p for p in rb if p not in rp]
    new = [p for p in rp if p not in rb]
    expected = set(p for p in new
                   if any(p == q or p.startswith(q + "/") for q in prefixes))
    unexpected = [p for p in new if p not in expected]

    def unchanged(p):
        """A pre-existing node is unchanged if its tag and attributes are identical AND
        its own child sequence still appears, in order, inside the post child sequence -
        the only permitted delta being children this manifest added. A reordered or
        replaced pre-existing child therefore still fails."""
        (tb, ab, kb), (tp, ap, kp) = rb[p], rp[p]
        if tb != tp or ab != ap:
            return False
        added = {k for k in kp if f"{p}/{k}" in expected}
        return tuple(k for k in kp if k not in added) == kb

    changed = [p for p in rb if p in rp and not unchanged(p)]

    # PROOF 2 - line multiset, parser-independent
    norm = lambda b: collections.Counter(
        b.replace(b"\xef\xbb\xbf", b"", 1).replace(b"\r\n", b"\n").split(b"\n"))
    cb, cp = norm(base), norm(post)
    lost = sum(max(0, c - cp.get(l, 0)) for l, c in cb.items())

    log.append(f"  {relpath}: baseline nodes={len(rb)} post={len(rp)} | changed={len(changed)} "
               f"missing={len(missing)} new={len(new)} (expected {len(expected)}, "
               f"UNEXPECTED {len(unexpected)}) | proof2 lost lines={lost}")
    for p in (changed[:4] + missing[:4] + unexpected[:4]):
        log.append(f"      ! {p}")
    return len(changed), len(missing), len(unexpected), len(expected), lost


def main():
    rev = "HEAD"
    if "--rev" in sys.argv:
        rev = sys.argv[sys.argv.index("--rev") + 1]
    rev = git("rev-parse", rev).stdout.decode().strip()
    log = [f"baseline rev: {rev}"]
    per_file = manifest_rows()
    tot = collections.Counter()
    for relpath, subs in sorted(per_file.items()):
        c, m, u, e, l = check_file(relpath, subs, rev, log)
        tot.update(changed=c, missing=m, unexpected=u, expected_new=e, lost=l)
    log.append(f"TOTAL over {len(per_file)} files: changed={tot['changed']} "
               f"missing={tot['missing']} unexpectedNew={tot['unexpected']} "
               f"expectedNew={tot['expected_new']} proof2LostLines={tot['lost']}")

    # ---- self-checks: break each proof and require it to notice ----------
    probe = "wz/String.wz/Consume.img.xml"
    subs = per_file[probe]
    log.append("SELF-CHECKS (on " + probe + ")")

    def mut_value(b):
        # change a PRE-EXISTING leaf's value (the first entry in the file)
        i = b.index(b'value="')
        return b[:i + 7] + b"ZZ" + b[i + 7:]

    def mut_delete(b):
        # drop one whole self-closing leaf line - a real deletion that still parses
        k = b.index(b"/>")
        i = b.rindex(b"\r\n", 0, k)
        j = b.index(b"\r\n", k)
        return b[:i] + b[j:]

    def mut_nest(b):
        # smuggle a new node INSIDE a pre-existing record - ticket 33's Proof A hole
        i = b.index(b"\r\n", b.index(b'<imgdir name=', b.index(b'<imgdir name=') + 1))
        return b[:i] + b'\r\n    <string name="smuggled" value="x"/>' + b[i:]

    def mut_reorder(b):
        # swap two PRE-EXISTING sibling leaves inside the first record
        s = b.split(b"\r\n")
        idx = [i for i, l in enumerate(s) if l.startswith(b"    <") and l.endswith(b"/>")]
        s[idx[0]], s[idx[1]] = s[idx[1]], s[idx[0]]
        return b"\r\n".join(s)

    results = []
    c, m, u, e, l = check_file(probe, subs, rev, log)
    results.append(("control (unmutated)", c == 0 and m == 0 and u == 0 and l == 0 and e > 0))
    c, m, u, e, l = check_file(probe, subs, rev, [], mutate=mut_value)
    results.append(("changed pre-existing value -> proof1 changed>0", c > 0))
    c, m, u, e, l = check_file(probe, subs, rev, [], mutate=mut_delete)
    results.append(("deleted a pre-existing line -> proof2 lost>0", l > 0))
    c, m, u, e, l = check_file(probe, subs, rev, [], mutate=mut_nest)
    results.append(("node nested inside a pre-existing record -> unexpectedNew>0", u > 0))
    c, m, u, e, l = check_file(probe, subs, rev, [], mutate=mut_reorder)
    results.append(("two pre-existing siblings reordered -> proof1 changed>0", c > 0))

    ok = True
    for label, good in results:
        ok = ok and good
        log.append(f"  {'PASS' if good else 'FAIL'}  {label}")

    hard = tot["changed"] or tot["missing"] or tot["unexpected"] or tot["lost"]
    log.append("RESULT: " + ("PASS" if ok and not hard else "FAIL"))
    print("\n".join(log))
    with open(os.path.join(HERE, "verify28-report.txt"), "w", encoding="utf-8",
              newline="\n") as f:
        f.write("\n".join(log) + "\n")
    return 0 if ok and not hard else 1


if __name__ == "__main__":
    sys.exit(main())
