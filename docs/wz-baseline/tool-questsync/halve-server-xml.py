"""Apply the owner's quest-requirement halving to Cosmic's Quest.wz XML.

The rule, recovered by diffing the owner's v83 client against a pristine v83 archive
(1563 of 1578 changed Check.img counts follow it exactly; the other 15 are unrelated
content differences between the two distributions):

    ceil(n / 2), never below 1

applied to:
  * Check.img  positive `count` under `mob` and `item`  - what the quest asks for
  * Act.img    negative `count` under `item`            - what the hand-in takes

and to nothing else. Positive Act.img counts are rewards. Negative Check.img counts are
not quantities at all - `ItemRequirement.check` reads `countNeeded <= 0` as "you must NOT
hold this item" (ItemRequirement.java:93), so halving one would be meaningless at best.

A row is halved only when it still equals the pristine archive, which makes this
idempotent and keeps it from re-halving what the October 2025 pass already did. Rows the
pristine archive has no node for are Cosmic's own injected content, whose counts are
usually also hardcoded in scripts/quest/*.js - those are left alone and listed on stderr.

    python halve-server-xml.py <wz/Quest.wz dir> <pristine Check.img dump> <pristine Act.img dump> [--apply]

The dumps are `tool-peek/WzPeek dump <pristine Quest.wz> Check.img` (and Act.img). Without
--apply it prints what it would do and writes nothing.
"""
import re
import sys

# one element per line in these files; both self-closing spellings appear across revisions
OPEN = re.compile(r'<imgdir name="([^"]*)">')
SELF = re.compile(r'<imgdir name="[^"]*"\s*/>')
CLOSE = re.compile(r'</imgdir>')
COUNT = re.compile(r'(<int name="count" value=")(-?\d+)("\s*/>)')


def load_dump(path):
    out = {}
    for line in open(path, encoding='utf-8', errors='replace'):
        parts = line.rstrip('\n').split('\t')
        if len(parts) >= 3 and parts[0] != 'FOUND' and parts[0].endswith('/count'):
            out[parts[0]] = int(parts[2])
    return out


def halve(n):
    return (-1 if n < 0 else 1) * max(1, (abs(n) + 1) // 2)


def wanted(img, path, value):
    """Is this leaf one the rule applies to?"""
    kind = path.split('/')[-3]          # .../<mob|item>/<i>/count
    if kind not in ('mob', 'item'):
        return False
    if img == 'Check.img':
        return kind in ('mob', 'item') and value >= 2
    return kind == 'item' and value <= -2


def process(xml_path, img, pristine, apply_):
    changed, skipped = [], []
    stack, out = [], []
    for line in open(xml_path, encoding='utf-8').read().splitlines(keepends=True):
        m = COUNT.search(line)
        if m:
            path = '/'.join(stack) + '/count'   # stack[0] is the root imgdir, e.g. "Check.img"
            value = int(m.group(2))
            if wanted(img, path, value):
                if path not in pristine:
                    skipped.append((path, value, 'no node in the pristine archive'))
                elif pristine[path] != value:
                    pass                # already halved by the October 2025 pass
                else:
                    new = halve(value)
                    changed.append((path, value, new))
                    line = line[:m.start()] + m.group(1) + str(new) + m.group(3) + line[m.end():]
            out.append(line)
            continue
        out.append(line)
        if SELF.search(line):
            continue
        if OPEN.search(line):
            stack.append(OPEN.search(line).group(1))
        elif CLOSE.search(line):
            stack.pop()
    if stack:
        raise SystemExit(f'{xml_path}: unbalanced imgdir nesting, {len(stack)} left open')
    if apply_ and changed:
        open(xml_path, 'w', encoding='utf-8', newline='').write(''.join(out))
    return changed, skipped


def main():
    wz_dir, check_dump, act_dump = sys.argv[1], sys.argv[2], sys.argv[3]
    apply_ = '--apply' in sys.argv[4:]
    total = 0
    for img, dump in (('Check.img', check_dump), ('Act.img', act_dump)):
        changed, skipped = process(f'{wz_dir}/{img}.xml', img, load_dump(dump), apply_)
        total += len(changed)
        print(f'== {img}: {len(changed)} halved, {len(skipped)} left alone')
        for path, old, new in changed:
            print(f'   {path}\t{old}\t->\t{new}')
        for path, value, why in skipped:
            print(f'   SKIP {path}\t{value}\t{why}', file=sys.stderr)
    print(('applied' if apply_ else 'dry run, nothing written') + f': {total} counts')


if __name__ == '__main__':
    main()
