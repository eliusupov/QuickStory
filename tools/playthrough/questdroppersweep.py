"""Does the dropper live where the quest sends you?

The check that would have caught quest 22529. Its Refreshing Stump Sap row was authored by
reading the `#o0130100#` token in the quest's own text, and then defended by pointing at that
same token - circular, because **client WZ never contained drop tables**. Nexon kept drops
server-side, so no drop row in this tree is recovered v84 data; every one is somebody's reading
of something else. A token cannot corroborate a row derived from the token.

What CAN be checked is whether a row contradicts the rest of the quest:

  1. the quest's own `#m<mapid>#` tokens say where it is staged - is the dropper placed there,
     or one portal hop away?
  2. the quest's `lvmin` versus `Mob.wz/<id>/info/level` - a level-22 quest on a level-4 mob is
     the 22529 signature.
  3. `#r...#k` red text holding a bare plural ("Defeat the #rStumps#k") names a FAMILY, while
     the `#o` token names one member of it.

Scope: only rows this project authored. Upstream HeavenMS seeds (151, 152) came from a different
lineage and are not suspect.

    python tools/playthrough/questdroppersweep.py

Writes docs/work-plan/V84-QUEST-DROPPER-SWEEP.tsv. Reads wz/ and the seed SQL only - no DB, no
running server.
"""
import re, os, sys, glob, collections
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
UPSTREAM_SEEDS = ('151-', '152-')          # HeavenMS lineage, out of scope
TODAY = '20260818'


def load_img(path):
    def rec(e):
        d = {}
        for c in e:
            d[c.get('name')] = rec(c) if c.tag == 'imgdir' else c.get('value')
        return d
    return rec(ET.parse(path).getroot())


check = load_img(os.path.join(ROOT, 'wz/Quest.wz/Check.img.xml'))
qinfo = load_img(os.path.join(ROOT, 'wz/Quest.wz/QuestInfo.img.xml'))

ids = set()
for f in ('09/Quest.paths.txt', '33/Quest.paths.txt'):
    for l in open(os.path.join(ROOT, 'docs/wz-baseline/merge-lists', f), encoding='utf-8'):
        p = l.strip().split('/')
        if len(p) == 3 and p[0] == 'Quest.wz' and p[2].isdigit():
            ids.add(p[2])
ids = sorted(ids, key=int)

# ---- maps: mob placement and the one-hop portal graph --------------------------------------
tag = re.compile(r'<(/?)imgdir\b[^>]*?(/?)>')
idre = re.compile(r'<string name="id" value="(\d+)"/>')
tyre = re.compile(r'<string name="type" value="(\w)"/>')
entry = re.compile(r'<imgdir name="[^"]*">(.*?)</imgdir>', re.S)
tmre = re.compile(r'<int name="tm" value="(\d+)"/>')


def block(s, header):
    i = s.find(header)
    if i < 0:
        return ''
    depth = 0
    for m in tag.finditer(s, i):
        if m.group(2):
            continue
        depth += -1 if m.group(1) else 1
        if depth == 0:
            return s[i:m.end()]
    return ''


_scr_cache = {}


def portal_script_targets(name):
    if name not in _scr_cache:
        f = os.path.join(ROOT, 'scripts/portal', name + '.js')
        try:
            body = open(f, encoding='utf-8', errors='replace').read()
        except OSError:
            body = ''
        _scr_cache[name] = {str(int(x)) for x in re.findall(r'warp\s*\(\s*(\d{4,9})', body)}
    return _scr_cache[name]


mob_maps = collections.defaultdict(set)
neighbours = collections.defaultdict(set)
for f in glob.glob(os.path.join(ROOT, 'wz/Map.wz/Map/Map*/*.img.xml')):
    mid = str(int(os.path.basename(f)[:-8]))
    s = open(f, encoding='utf-8', errors='replace').read()
    for body in entry.findall(block(s, '<imgdir name="life">')):
        mi, mt = idre.search(body), tyre.search(body)
        if mi and mt and mt.group(1) == 'm':
            mob_maps[str(int(mi.group(1)))].add(mid)
    pblock = block(s, '<imgdir name="portal">')
    for t in tmre.findall(pblock):
        if t != '999999999':
            neighbours[mid].add(str(int(t)))
            neighbours[str(int(t))].add(mid)
    # a tm of 999999999 is not a dead end: it means the destination is in a portal SCRIPT.
    # 22559's Enraged Golems sit on 910600010, reachable only through evanDollGR.js, and a
    # tm-only graph calls that map unreachable and the drop row broken. It is neither.
    for scr in re.findall(r'<string name="script" value="(\w+)"/>', pblock):
        for t in portal_script_targets(scr):
            neighbours[mid].add(t)
            neighbours[t].add(mid)

mob_level = {}
for f in glob.glob(os.path.join(ROOT, 'wz/Mob.wz/*.img.xml')):
    head = open(f, encoding='utf-8', errors='replace').read(4000)
    m = re.search(r'<int name="level" value="(\d+)"/>', head)
    if m:
        mob_level[str(int(os.path.basename(f)[:-8]))] = int(m.group(1))

# ---- drop rows, with provenance -------------------------------------------------------------
drops = collections.defaultdict(list)      # itemid -> [(dropper, questid, chance, seedfile)]
insert = re.compile(r'INSERT INTO (\w+)')
rowre = re.compile(r'\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*\)')
for f in sorted(glob.glob(os.path.join(ROOT, 'src/main/resources/db/data/*.sql'))):
    base = os.path.basename(f)
    table = ''
    for line in open(f, encoding='utf-8', errors='replace'):
        if line.lstrip().startswith('--'):
            continue
        m = insert.search(line)
        if m:
            table = m.group(1)
        if table != 'drop_data':
            continue
        for d, i, lo, hi, q, ch in rowre.findall(line):
            drops[str(int(i))].append((str(int(d)), str(int(q)), ch, base))

def _names(img):
    t = open(os.path.join(ROOT, 'wz/String.wz/' + img), encoding='utf-8', errors='replace').read()
    d = {}
    for m in re.finditer(r'<imgdir name="(\d+)">(.*?)</imgdir>', t, re.S):
        n = re.search(r'<string name="name" value="([^"]*)"', m.group(2))
        de = re.search(r'<string name="desc" value="([^"]*)"', m.group(2))
        if n:
            d[str(int(m.group(1)))] = (n.group(1), de.group(1) if de else '')
    return d


MOBNAME = {k: v[0] for k, v in _names('Mob.img.xml').items()}
ITEMNAME = {}
for _img in ('Etc.img.xml', 'Consume.img.xml', 'Eqp.img.xml'):
    ITEMNAME.update(_names(_img))


def name_link(item, dropper):
    """Does the mob's name appear in the item's own name or description?

    String.wz text is client data and is NOT derived from any drop row, so this corroborates a
    row independently of the quest token. Its LIMIT is family granularity: 4032460 "Refreshing
    Stump Sap" matches "Stump", "Axe Stump" and "Ghost Stump" alike, which is exactly how 22529
    got through. Evidence, never a verdict."""
    mob = MOBNAME.get(dropper, '')
    nm, desc = ITEMNAME.get(item, ('', ''))
    if not mob:
        return ''
    for word in (w for w in mob.split() if len(w) > 3):
        if word.lower() in (nm + ' ' + desc).lower():
            return 'NAME_LINK:%s~%s' % (mob, nm)
    return ''


# ---- hand review -----------------------------------------------------------------------------
# Every heuristic hit below was read in full - QuestInfo 0/1/2, Say.img, Check, Act, and the
# String.wz name of every id the text touches - before a verdict was written. The quoted text is
# in docs/work-plan/V84-QUEST-DROPPER-SWEEP.md. The owner's rule, and it is the right one: the
# heuristics are triage, the quest's own whole text is the authority.
REVIEWED = {
    ('22407', '4032475'): ('CLEARED', 'text: "obtain #t4032475#s from #m211000000#", and the item IS '
                           '"Lycanthrope Leather"; dropper 8140000 IS "Lycanthrope", placed on 211040800/'
                           '211040900/211041000 - El Nath Dungeon, i.e. the El Nath the token names. The '
                           'token labels the REGION by its town; a one-hop check cannot walk that far.'),
    ('22410', '4032504'): ('CLEARED', 'text: "the same materials as before"; 4032504 is a second item id '
                           'with the same name and desc, "Lycanthrope Leather". Same answer as 22407.'),
    ('22412', '4000270'): ('CLEARED', '"Wyvern Toenail" <- 8300004 "Soaring Black Wyvern"; the row is a '
                           'verbatim table copy from Dark Wyvern 8150302 (changeSet 153) and four upstream '
                           'droppers already carry the item. The quest names no source map, only Kenta.'),
    ('22412', '4000271'): ('CLEARED', '"Destroyed Nest" <- 9500377 "Nest Golem", name identity with the '
                           'upstream 8190002/8190005 Nest Golem rows. The level gap is the Crimson Sky '
                           'copy at 100 against a level-120 quest; the same item drops from Leafre at 110.'),
    ('22412', '4000272'): ('CLEARED', '"Egg Shell", desc "a broken piece of egg shell that Newt Jr. had on '
                           'its head" <- 9500376 "Jr. Newtie", name identity with upstream 8190000.'),
    ('22413', '4000270'): ('CLEARED', 'identical requirement set to 22412 ("pay double the fee"); same answer.'),
    ('22413', '4000271'): ('CLEARED', 'identical requirement set to 22412; same answer.'),
    ('22413', '4000272'): ('CLEARED', 'identical requirement set to 22412; same answer.'),
    ('22532', '4032462'): ('CLEARED', 'Check.img/22532/1 itself requires 50 kills of mob 2230112, and the '
                           'drop row is on 2230112 "Terrified Wild Boar" for "Wild Boar Doll". The map '
                           'tokens are only where the NPC stands - "Find out what it is from #p1040001# at '
                           'the #m106000300#" - not where the boars are.'),
    ('22559', '4032466'): ('CLEARED', 'text: "enter that door, defeat the Enraged Golems, and bring back the '
                           'culprit... a doll or puppet". Item 4032466 IS "Golem Doll"; dropper 9300387 IS '
                           '"Enraged Golem", placed on 910600000 and 910600010. 910600010 is reachable - '
                           'scripts/portal/evanDollGR.js warps there from 106010102 portal 8. No tm points '
                           'at it, which is why a tm-only reachability check calls it orphaned.'),
    ('22004', '4032498'): ('CLEARED', 'text: "Defeat some of the #r#o0130100#s#k NEARBY". The quest is given '
                           'at the Evan farm and Stump 130100 spawns on 100030300, a farm map. The item '
                           'desc independently reads "A tree branch from a Stump". Level 5 quest, level 4 mob.'),
    ('22503', '4032453'): ('CLEARED', 'pinned separately by EvanPorkSourceRealLoad: the quest names '
                           '#o1210100# "Pig" for "Pork" and farm map 100030310 carries 20 of them.'),
}

MAPTOK = re.compile(r'#m(\d+)#')
REDTOK = re.compile(r'#r(.*?)#k')


def strings(q):
    n = qinfo.get(q, {})
    return ' '.join(str(n.get(k, '')) for k in ('0', '1', '2', 'summary', 'demandSummary'))


out = []
for q in ids:
    c0 = check.get(q, {}).get('0', {}) or {}
    c1 = check.get(q, {}).get('1', {}) or {}
    end = c0.get('end', '')
    expired = bool(end) and end[:8] < TODAY
    lvmin = int(c0.get('lvmin') or 0)
    text = strings(q)
    tokens = [str(int(t)) for t in sorted(set(MAPTOK.findall(text)), key=int)]
    near = set(tokens)
    for t in tokens:
        near |= neighbours.get(t, set())
    red = [r.strip() for r in REDTOK.findall(text) if '#' not in r and r.strip()]

    items = []
    for blk in (c0, c1):
        sub = blk.get('item', {})
        if isinstance(sub, dict):
            for _, e in sub.items():
                if isinstance(e, dict) and e.get('id'):
                    items.append(str(int(e['id'])))
    for item in sorted(set(items), key=int):
        rowset = drops.get(item, [])
        if not rowset:
            continue
        ours = [r for r in rowset if not r[3].startswith(UPSTREAM_SEEDS)]
        droppers = sorted(set(r[0] for r in rowset), key=int)
        ourdroppers = sorted(set(r[0] for r in ours), key=int)
        seeds = ','.join(sorted(set(r[3].split('-')[0] for r in ours))) or 'upstream'
        # ordinary loot (Orange Potion and friends) carries hundreds of droppers and is not a
        # quest-specific item; the question "is the dropper where the quest is" is meaningless
        # for it. ponytail: 20 is a threshold, not a law - raise it if a real quest item trips it.
        if len(droppers) > 20:
            continue
        placed = {d: mob_maps.get(d, set()) for d in droppers}
        on_token = sorted({d for d in droppers if placed[d] & set(tokens)}, key=int)
        on_near = sorted({d for d in droppers if placed[d] & near}, key=int)
        levels = {d: mob_level.get(d) for d in (ourdroppers or droppers)}
        lv = [v for v in levels.values() if v is not None]
        gap = (lvmin - max(lv)) if (lv and lvmin) else None

        if not ours:
            verdict = 'UPSTREAM_ONLY'
        elif expired:
            verdict = 'EXPIRED_QUEST'
        elif on_token:
            verdict = 'OK_ON_QUEST_MAP'
        elif on_near:
            verdict = 'OK_ONE_PORTAL_AWAY'
        elif not tokens:
            verdict = 'NO_MAP_TOKEN_UNCHECKABLE'
        else:
            verdict = 'DROPPER_OFF_QUEST_MAPS'
        flags = []
        if gap is not None and gap >= 10:
            flags.append('LEVEL_GAP+%d' % gap)
        if gap is not None and gap <= -25:
            flags.append('LEVEL_GAP%d' % gap)
        if red and ours and verdict not in ('UPSTREAM_ONLY', 'EXPIRED_QUEST'):
            flags.append('RED_TEXT:' + '/'.join(red[:2]))
        for d in (ourdroppers or droppers):
            nl = name_link(item, d)
            if nl:
                flags.append(nl)
                break
        upstream_only_droppers = [r[0] for r in rowset if r[3].startswith(UPSTREAM_SEEDS)]
        if upstream_only_droppers and ours:
            flags.append('ALSO_UPSTREAM_SOURCED')
        rv, ev = REVIEWED.get((q, item), ('', ''))
        out.append((q, qinfo.get(q, {}).get('name', '').replace('\t', ' '), str(lvmin or ''), item,
                    ','.join(ourdroppers) or ','.join(droppers), seeds,
                    ','.join(tokens) or '-',
                    'yes' if on_token else ('neighbour' if on_near else 'no'),
                    ','.join('%s=%s' % (d, levels[d]) for d in sorted(levels, key=int)),
                    verdict, ';'.join(flags), rv, ev))

dst = os.path.join(ROOT, 'docs/work-plan/V84-QUEST-DROPPER-SWEEP.tsv')
with open(dst, 'w', encoding='utf-8', newline='\n') as fh:
    fh.write('quest_id\tquest_name\tlvmin\titem\tdroppers\tseed_changesets\tquest_map_tokens\t'
             'dropper_on_quest_map\tdropper_levels\theuristic_verdict\tflags\t'
             'reviewed_verdict\tevidence\n')
    for r in out:
        fh.write('\t'.join(r) + '\n')

sys.stderr.write('%d quest/item pairs carrying drop rows\n' % len(out))
sys.stderr.write('%s\n' % collections.Counter(r[9] for r in out))
for r in out:
    if r[9] in ('DROPPER_OFF_QUEST_MAPS', 'NO_MAP_TOKEN_UNCHECKABLE') or (
            r[10] and r[9] not in ('UPSTREAM_ONLY', 'EXPIRED_QUEST')):
        print('%s lv%-4s item %s drop %s seed %s tok %s on=%s lv[%s] -> %s %s'
              % (r[0], r[2], r[3], r[4], r[5], r[6], r[7], r[8], r[9], r[10]))
