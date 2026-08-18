"""Source sweep over the v84-added ITEM set.

Emits docs/work-plan/V84-ITEM-SOURCE-SWEEP.tsv - one row per v84-new item that nothing in this
server can produce. Reads the WZ XML in wz/ and eight SELECT-only DB dumps; it never writes to the
database and never touches the running server.

    python tools/playthrough/itemsweep.py <scratchdir>

<scratchdir> must already hold these dumps (SELECT only, mysql -N):

    SELECT dropperid,itemid,questid FROM drop_data;              -> drop_data.tsv
    SELECT itemid FROM drop_data_global;                         -> drop_global.tsv
    SELECT reactorid,itemid FROM reactordrops;                   -> reactordrops2.tsv
    SELECT s.npcid, si.itemid FROM shopitems si
           JOIN shops s ON s.shopid=si.shopid;                   -> shopnpc.tsv
    SELECT itemid FROM makercreatedata;                          -> maker.tsv
    SELECT itemid FROM makerrecipedata;                          -> makerrecipe.tsv
    SELECT rewardid FROM makerrewarddata;                        -> makerreward.tsv
    SELECT cardid FROM monstercarddata;                          -> cards.tsv

THE SET SWEPT is docs/wz-baseline/add-list/, the computed v84-minus-v83 diff, read as copy roots
(no listed path is an ancestor of another):

  * Item.txt      234 depth-4 roots  Item.wz/<cat>/<img>.img/<itemid>   = whole-new item nodes
                    2 depth-3 roots  Item.wz/Cash/0562.img (3 ids inside), Item.wz/Pet/5000067.img
  * Character.txt 176 depth-3 roots  Character.wz/<slot>/<itemid>.img   = whole-new equip images,
                    minus the 20 Character.wz/00002000.img/<effect> roots, which are animation
                    nodes on an existing image and not items at all -> 156 equips.

Depth-5/6 paths in either file are FIELD changes to nodes that already existed in v83, and are out
of scope on the same authority the quest sweep used for quests.

The map-life reader is lifted from questsweep.py. Field order inside a life entry is NOT stable
across map files, so both fields are pulled by name out of the entry block; a regex that assumes
adjacency silently reports half of Victoria Island as unpopulated.
"""
import re, os, sys, glob, collections
import xml.etree.ElementTree as ET

ROOT = os.environ.get('SWEEP_ROOT') or os.path.dirname(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCR = sys.argv[1] if len(sys.argv) > 1 else '.'
TODAY = '20260818'   # same constant questsweep.py uses


def load_img(path):
    def rec(e):
        d = {}
        for c in e:
            d[c.get('name')] = rec(c) if c.tag == 'imgdir' else c.get('value')
        return d
    return rec(ET.parse(path).getroot())


# ---------------------------------------------------------------------------------------------
# 1. the set
# ---------------------------------------------------------------------------------------------
AL = os.path.join(ROOT, 'docs/wz-baseline/add-list')
items = {}   # id -> dict(cat, path, wz)


def add(iid, cat, path, wz):
    iid = str(int(iid))
    if iid in items:
        raise SystemExit('duplicate id %s (%s / %s)' % (iid, items[iid]['path'], path))
    items[iid] = {'cat': cat, 'path': path, 'wz': wz}


roots3 = []
for line in open(os.path.join(AL, 'Item.txt'), encoding='utf-8'):
    p = line.strip().split('/')
    if not p or p[0] != 'Item.wz':
        continue
    if len(p) == 4 and p[3].isdigit():
        add(p[3], p[1], line.strip(), ('wz/Item.wz/%s/%s.xml' % (p[1], p[2]), p[3]))
    elif len(p) == 3 and p[2].endswith('.img'):
        roots3.append(line.strip())

for root in roots3:
    p = root.split('/')
    xml = os.path.join(ROOT, 'wz', p[0], p[1], p[2] + '.xml')
    if not os.path.exists(xml):
        raise SystemExit('whole-new image %s is not in our tree; cannot enumerate its ids' % root)
    if p[1] == 'Pet':                      # Pet/<id>.img - the image IS the item
        add(p[2][:-4], 'Pet', root, ('wz/Item.wz/Pet/%s.xml' % p[2], None))
        continue
    for iid in sorted(set(re.findall(r'<imgdir name="(\d{6,8})">',
                                     open(xml, encoding='utf-8', errors='replace').read()))):
        add(iid, p[1], root + '/' + iid, ('wz/Item.wz/%s/%s.xml' % (p[1], p[2]), iid))

for line in open(os.path.join(AL, 'Character.txt'), encoding='utf-8'):
    p = line.strip().split('/')
    if len(p) == 3 and p[0] == 'Character.wz' and re.fullmatch(r'\d+\.img', p[2]):
        add(p[2][:-4], p[1], line.strip(), ('wz/Character.wz/%s/%s.xml' % (p[1], p[2]), None))

# ---------------------------------------------------------------------------------------------
# 2. names verbatim from String.wz, and the item's own info block from Item.wz / Character.wz
# ---------------------------------------------------------------------------------------------
names = {}


def walk_names(node):
    for k, v in node.items():
        if isinstance(v, dict):
            if k.isdigit() and isinstance(v.get('name'), str):
                names.setdefault(str(int(k)), v['name'].strip())
            else:
                walk_names(v)


for img in ('Eqp', 'Consume', 'Etc', 'Ins', 'Cash', 'Pet'):
    walk_names(load_img(os.path.join(ROOT, 'wz/String.wz/%s.img.xml' % img)))

_cache, info = {}, {}
for iid, rec in items.items():
    f, sub = rec['wz']
    path = os.path.join(ROOT, f)
    if path not in _cache:
        _cache[path] = load_img(path) if os.path.exists(path) else {}
    node = _cache[path]
    if sub is not None:
        node = node.get(sub, {})
    info[iid] = node.get('info', {}) if isinstance(node.get('info'), dict) else {}


def iv(iid, key, default='0'):
    v = info[iid].get(key, default)
    return str(v) if v is not None else default


# ---------------------------------------------------------------------------------------------
# 3. placement - map life / reactors, script spawners, Mob.wz revive chains (questsweep.py logic)
# ---------------------------------------------------------------------------------------------
mob_maps, npc_maps, reactor_maps = (collections.defaultdict(list) for _ in range(3))
tag = re.compile(r'<(/?)imgdir\b[^>]*?(/?)>')
idre = re.compile(r'<string name="id" value="(\d+)"/>')
tyre = re.compile(r'<string name="type" value="(\w)"/>')
entry = re.compile(r'<imgdir name="\d+">(.*?)</imgdir>', re.S)


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


for f in glob.glob(os.path.join(ROOT, 'wz/Map.wz/Map/Map*/*.img.xml')):
    mid = os.path.basename(f)[:-8]
    s = open(f, encoding='utf-8', errors='replace').read()
    for body in entry.findall(block(s, '<imgdir name="life">')):
        mi, mt = idre.search(body), tyre.search(body)
        if mi and mt:
            (mob_maps if mt.group(1) == 'm' else npc_maps)[str(int(mi.group(1)))].append(mid)
    for body in entry.findall(block(s, '<imgdir name="reactor">')):
        mi = idre.search(body)
        if mi:
            reactor_maps[str(int(mi.group(1)))].append(mid)

# Self-check on the life reader, because a silent failure here invents blockers rather than
# reporting them - it produced four imaginary ones once. Kerning City writes id before type, Deep
# Valley writes type before id; both must come back populated. Stump 130100 must be on 101030000
# and NOT in the Deep Valleys, which is the case V84-QUEST-SWEEP pinned.
for _n in ('1052000', '1052002', '1052103', '1052106'):
    assert '103000000' in npc_maps[_n], 'life reader lost Kerning City npc ' + _n
for _m in ('1130100', '1140100'):
    assert '106000000' in mob_maps[_m], 'life reader lost Deep Valley mob ' + _m
assert '101030000' in mob_maps['130100'] and '106000000' not in mob_maps['130100']

# scripts: gainItem(<literal>) is a grant. A bare numeric mention is NOT proof of a grant, but it
# is the only signal available when the id reaches gainItem through a variable - scripts/npc/
# 9000011.js keeps its whole prize table in Array() literals and gains by index, which is how the
# five 15-day mount coupons 2430073-77 are actually obtained. Both are recorded, separately.
grant_items, mention_items = collections.defaultdict(list), collections.defaultdict(list)
num = re.compile(r'(?<![\w.])(\d{4,8})(?![\w.])')
for f in glob.glob(os.path.join(ROOT, 'scripts/**/*.js'), recursive=True):
    s = open(f, encoding='utf-8', errors='replace').read()
    rel = os.path.relpath(f, ROOT).replace('\\', '/')
    for x in re.findall(r'gainItem\s*\(\s*(\d{4,8})', s):
        grant_items[str(int(x))].append(rel)
    for x in set(num.findall(s)):
        mention_items[str(int(x))].append(rel)
    for m in re.findall(r'(?:spawnMonster(?:OnGroundBelow)?|LifeFactory\.getMonster)\s*\(\s*(\d{5,8})', s):
        mob_maps[str(int(m))].append('script:' + os.path.basename(f))
for f in glob.glob(os.path.join(ROOT, 'src/main/java/**/*.java'), recursive=True):
    s = open(f, encoding='utf-8', errors='replace').read()
    rel = os.path.relpath(f, ROOT).replace('\\', '/')
    for x in re.findall(r'gainItem\s*\(\s*(\d{4,8})', s):
        grant_items[str(int(x))].append(rel)
    for m in re.findall(r'(?:spawnMonster(?:OnGroundBelow)?|LifeFactory\.getMonster)\s*\(\s*(\d{5,8})', s):
        mob_maps[str(int(m))].append('script:' + os.path.basename(f))

for f in glob.glob(os.path.join(ROOT, 'wz/Mob.wz/*.img.xml')):
    src_mob = str(int(os.path.basename(f)[:-8]))
    if src_mob not in mob_maps:
        continue
    s = open(f, encoding='utf-8', errors='replace').read()
    if 'name="revive"' in s:
        for t in re.findall(r'value="(\d{5,8})"', block(s, '<imgdir name="revive">')):
            mob_maps[str(int(t))].append('revive:' + src_mob)


def script_label(rel):
    """scripts/npc/<n>.js is a live source only if npc <n> is actually placed on a map."""
    m = re.fullmatch(r'scripts/npc/(\d+)\.js', rel)
    if not m:
        return rel, True
    n = str(int(m.group(1)))
    placed = n in npc_maps
    return rel + ('@npc on ' + npc_maps[n][0] if placed else '@NPC NOT PLACED'), placed


# ---------------------------------------------------------------------------------------------
# 4. sources
# ---------------------------------------------------------------------------------------------
def rows(fn):
    out, p = [], os.path.join(SCR, fn)
    if not os.path.exists(p):
        raise SystemExit("missing dump %s - see this file's docstring" % p)
    for l in open(p, encoding='utf-8-sig', errors='replace'):
        c = l.split()
        if c and all(x.lstrip('-').isdigit() for x in c):
            out.append([str(int(x)) for x in c])
    return out


drop_by_item = collections.defaultdict(list)
for d, i, q in rows('drop_data.tsv'):
    drop_by_item[i].append(d)
global_items = set(x[0] for x in rows('drop_global.tsv'))
reactor_by_item = collections.defaultdict(list)
for r, i in rows('reactordrops2.tsv'):
    reactor_by_item[i].append(r)
shop_by_item = collections.defaultdict(list)
for n, i in rows('shopnpc.tsv'):
    shop_by_item[i].append(n)
maker_items = set(x[0] for x in rows('maker.tsv')) | set(x[0] for x in rows('makerrecipe.tsv'))
maker_rewards = set(x[0] for x in rows('makerreward.tsv'))
card_items = set(x[0] for x in rows('cards.tsv'))

act = load_img(os.path.join(ROOT, 'wz/Quest.wz/Act.img.xml'))
check = load_img(os.path.join(ROOT, 'wz/Quest.wz/Check.img.xml'))
quest_grant, quest_needs = collections.defaultdict(set), collections.defaultdict(set)
for qid, node in act.items():
    for phase in ('0', '1'):
        blk = node.get(phase) if isinstance(node.get(phase), dict) else {}
        it = blk.get('item', {})
        if isinstance(it, dict):
            for _, e in it.items():
                if isinstance(e, dict) and e.get('id'):
                    n = int(e.get('count', '0') or 0)
                    (quest_grant if n > 0 else quest_needs)[str(int(e['id']))].add(qid)
for qid, node in check.items():
    for phase in ('0', '1'):
        blk = node.get(phase) if isinstance(node.get(phase), dict) else {}
        it = blk.get('item', {})
        if isinstance(it, dict):
            for _, e in it.items():
                if isinstance(e, dict) and e.get('id'):
                    quest_needs[str(int(e['id']))].add(qid)

# a quest whose Check.img/<id>/0/end is in the past is refused by EndDateRequirement before
# anything else is consulted, so it can neither grant nor consume anything. Same rule the quest
# sweep used to retire 48 of the 198 v84 quests.
qend = {}
for qid, node in check.items():
    e = (node.get('0', {}) or {}).get('end', '') if isinstance(node.get('0'), dict) else ''
    qend[qid] = e if (e and e[:8] < TODAY) else ''


def split_quests(qs):
    live = sorted(q for q in qs if not qend.get(q))
    dead = sorted(q for q in qs if qend.get(q))
    return live, dead

commodity = collections.defaultdict(list)
com_s = open(os.path.join(ROOT, 'wz/Etc.wz/Commodity.img.xml'), encoding='utf-8',
             errors='replace').read()
for body in re.findall(r'<imgdir name="\d+">(.*?)</imgdir>', com_s, re.S):
    g = lambda k: (re.search(r'<int name="%s" value="(-?\d+)"/>' % k, body) or [None, None])[1]
    if g('ItemId'):
        commodity[str(int(g('ItemId')))].append((g('SN'), g('OnSale') == '1'))

# an item with only an OnSale=0 Commodity row can still be reached if its SN is inside a
# CashPackage - CashShop.CashItemFactory loads Etc.wz/CashPackage.img into `packages`.
pkg_s = open(os.path.join(ROOT, 'wz/Etc.wz/CashPackage.img.xml'), encoding='utf-8',
             errors='replace').read()
pkg_sns = set(re.findall(r'value="(\d+)"', pkg_s))
sn_item = {}
for body in re.findall(r'<imgdir name="\d+">(.*?)</imgdir>', com_s, re.S):
    g = lambda k: (re.search(r'<int name="%s" value="(-?\d+)"/>' % k, body) or [None, None])[1]
    if g('SN') and g('ItemId'):
        sn_item[g('SN')] = str(int(g('ItemId')))
in_package = collections.defaultdict(list)
for sn in pkg_sns:
    if sn in sn_item:
        in_package[sn_item[sn]].append(sn)

# containers: any Item.wz node carrying reward/<n>/item (ItemInformationProvider.getItemReward)
container_of = collections.defaultdict(list)
for f in glob.glob(os.path.join(ROOT, 'wz/Item.wz/*/*.img.xml')):
    if '<imgdir name="reward">' not in open(f, encoding='utf-8', errors='replace').read():
        continue
    for cid, node in load_img(f).items():
        rw = node.get('reward') if isinstance(node, dict) else None
        if cid.isdigit() and isinstance(rw, dict):
            for _, e in rw.items():
                if isinstance(e, dict) and e.get('item'):
                    container_of[str(int(e['item']))].append(str(int(cid)))


def sources(iid):
    """(live sources, sources that exist but whose granter is not reachable)."""
    ok, dead = [], []
    for pool, placed, label in ((drop_by_item, mob_maps, 'drop'),
                                (reactor_by_item, reactor_maps, 'reactor'),
                                (shop_by_item, npc_maps, 'shop')):
        have = pool.get(iid, [])
        if have:
            live = [x for x in have if x in placed]
            (ok if live else dead).append(
                '%s(%d/%d placed: %s)' % (label, len(live), len(have), ','.join(sorted(set(have)))))
    for cond, label in ((iid in global_items, 'drop_data_global'), (iid in maker_items, 'maker'),
                        (iid in maker_rewards, 'makerreward'), (iid in card_items, 'monstercard')):
        if cond:
            ok.append(label)
    if iid in quest_grant:
        live_q, dead_q = split_quests(quest_grant[iid])
        if live_q:
            ok.append('questreward(' + ','.join(live_q[:4]) + ')')
        else:
            dead.append('questreward(' + ','.join('%s end %s' % (q, qend[q]) for q in dead_q[:4])
                        + ' - all expired)')
    if iid in grant_items:
        ok.append('gainItem(' + ','.join(sorted(set(grant_items[iid]))[:3]) + ')')
    elif iid in mention_items:
        for rel in sorted(set(mention_items[iid]))[:3]:
            lbl, live = script_label(rel)
            (ok if live else dead).append('script_mention(' + lbl + ')')
    if iid in commodity:
        live = [sn for sn, on in commodity[iid] if on]
        if live:
            ok.append('cashshop(SN ' + ','.join(live) + ' OnSale=1)')
        else:
            dead.append('cashshop(SN ' + ','.join(sn for sn, _ in commodity[iid]) + ' OnSale=0)')
    return ok, dead


# ---------------------------------------------------------------------------------------------
# 5. buckets and verdicts
# ---------------------------------------------------------------------------------------------
BUCKET = {'Hair': 'cosmetic_hair_face', 'Face': 'cosmetic_hair_face', 'TamingMob': 'mount',
          'PetEquip': 'pet_equip', 'Pet': 'pet', 'Weapon': 'equip_weapon',
          'Cap': 'equip_armor', 'Coat': 'equip_armor', 'Longcoat': 'equip_armor',
          'Pants': 'equip_armor', 'Shoes': 'equip_armor', 'Glove': 'equip_armor',
          'Cape': 'equip_armor', 'Shield': 'equip_armor',
          'Accessory': 'equip_accessory', 'Ring': 'equip_accessory',
          'Consume': 'consume', 'Etc': 'etc', 'Install': 'install', 'Cash': 'cash',
          # Item.wz/Special/0910.img holds the 91xxxxx cash-PACKAGE ids, not wearable content
          'Special': 'cash'}

# Evidence the repo already established for whole families. Same pattern questsweep.py uses:
# every string here is a citation, not an opinion.
SETTLED = {}
for i in range(2047000, 2047003):
    SETTLED[str(i)] = SETTLED[str(i + 100)] = (
        'the only drop row is on mob 8300007 Dragon Rider, which no map places - '
        '153-crimson-sky-drop-data.sql:39 records that refusal deliberately, and the rows come '
        'from 160-monsterbook-drop-data.sql:282-287. The fix is a spawner for 8300007, NOT a new '
        'drop row.')
for i in range(2047300, 2047310):
    SETTLED[str(i)] = (
        'no drop row at all. The sibling weapon tablets 2047000-2047102 have rows only on the '
        'unplaced 8300007, so copying one of those verbatim would change nothing. No analogue.')
for i in (1932006, 1932007, 1932008, 1932009, 1932011, 1932018, 1932019, 1932020):
    SETTLED[str(i)] = (
        'mount saddle, not loot. v84 grants a mount through the coupon spec/script '
        '(Item.wz/Consume/0243.img/<coupon>/spec/script = consume_2430073 and friends) and none of '
        'those script files exists - V84-OPEN-ITEMS section 4, ticket 05. The fix is a script, '
        'not a drop row.')
for i in (1142170, 1142171, 1142172):
    SETTLED[str(i)] = (
        'Mesoranger medal. Quest 19011 is GM-only - V84-QUEST-SWEEP settled it: autoAccept + '
        'medalCategory 3, and its sole start requirement is possession of 1142170, which nothing '
        'grants because a GM handed it out.')
for i in (3994184, 3994185):
    SETTLED[str(i)] = (
        'Korean 1049x winter-event block. The quests that need it carry no end date of their own '
        'but their block does - 10487/10490/10496 all end 200912280000 - and V84-QUEST-SWEEP '
        'retired 10491-10494 and 10497 on exactly that evidence. Switched-off event content.')
SETTLED['4032530'] = (
    'named for Leviathan but it is NOT a Leviathan drop: String.wz/Etc.img/4032530/desc reads '
    'literally "G-Star Clear", a G-Star 2009 convention giveaway, same family as 2430034 '
    '"G-star Reset Item". Do not put it on mob 9500382.')
SETTLED['5000067'] = (
    'V84-OPEN-ITEMS already tracks this: 5240028 Dynamite (Commodity SN 10002346/47/60200078/79, '
    'OnSale=1) feeds this pet, and the pet itself has no Commodity row.')

out, counts, bucket_total = [], collections.Counter(), collections.Counter()
mention_only = []
for iid in sorted(items, key=int):
    cat = items[iid]['cat']
    b = BUCKET[cat]
    bucket_total[b] += 1
    if b == 'cosmetic_hair_face':
        # decided up front, before any source lookup: a hair id is applied by a beauty-salon NPC
        # that reads Character.wz directly (scripts/npc/1012103.js and the other stylists pass the
        # raw id to setHair), it never becomes an inventory item, so no drop / shop / quest row can
        # exist for one. Reported as its own bucket rather than counted as an unsourced item.
        out.append((iid, names.get(iid, ''), cat, items[iid]['path'], '', b,
                    'COSMETIC_NOT_APPLICABLE',
                    'beauty-shop content: applied by an NPC from Character.wz, never an item'))
        counts['COSMETIC_NOT_APPLICABLE'] += 1
        counts['COSMETIC_NOT_APPLICABLE:' + b] += 1
        continue

    ok, dead = sources(iid)
    cont = sorted(set(container_of.get(iid, [])))
    live_cont = [c for c in cont if sources(c)[0]]
    if live_cont:
        ok.append('container(' + ','.join(live_cont) + ' is itself sourced)')
    if ok:
        counts['SOURCED'] += 1
        counts['SOURCED:' + b] += 1
        if all(s.startswith('script_mention') for s in ok):
            counts['SOURCED_BY_SCRIPT_MENTION_ONLY'] += 1
            mention_only.append((iid, names.get(iid, ''), ';'.join(ok)))
        continue

    notes = []
    if iid in quest_needs:
        live_q, dead_q = split_quests(quest_needs[iid])
        if live_q:
            notes.append('REQUIRED BY LIVE QUEST ' + ','.join(live_q))
        if dead_q:
            notes.append('required only by expired quest ' +
                         ','.join('%s(end %s)' % (q, qend[q]) for q in dead_q))
    if cont:
        notes.append('container ' + ','.join(cont))

    if cont:
        verdict = 'CONTAINER_ONLY'
        notes.append('every container listed is itself unsourced')
    elif iid in commodity:
        verdict = 'CASH_ONLY'
        notes.append('Commodity.img row exists with OnSale=0 - v84 shipped it switched off' +
                     (' but its SN %s is inside a CashPackage.img package'
                      % ','.join(sorted(in_package[iid])) if iid in in_package else ''))
    elif iv(iid, 'cash') == '1':
        verdict = 'NO_SOURCE_IN_V84'
        notes.append('info/cash=1 but NO row in Etc.wz/Commodity.img - v84 ships the art and lists '
                     'no way to buy it')
    elif iv(iid, 'only') == '1' and iv(iid, 'tradeBlock') == '1' and iv(iid, 'price') == '1':
        verdict = 'NO_SOURCE_IN_V84'
        notes.append('only=1 + tradeBlock=1 + price=1: GM/test item, handed out by command, not '
                     'by content')
    else:
        verdict = 'PARITY_GAP'

    if iid in SETTLED:
        notes.append(SETTLED[iid])
    out.append((iid, names.get(iid, ''), cat, items[iid]['path'], ';'.join(dead), b, verdict,
                ' | '.join(notes)))
    counts[verdict] += 1
    counts[verdict + ':' + b] += 1

dst = os.path.join(ROOT, 'docs/work-plan/V84-ITEM-SOURCE-SWEEP.tsv')
with open(dst, 'w', encoding='utf-8', newline='\n') as fh:
    fh.write('item_id\titem_name\tcategory\tadd_list_path\tsources_found\tbucket\tverdict\tnote\n')
    for r in out:
        fh.write('\t'.join(x.replace('\t', ' ') for x in r) + '\n')

sys.stderr.write('v84-new items: %d   rows written: %d\n' % (len(items), len(out)))
for k in sorted(counts):
    sys.stderr.write('  %-42s %d\n' % (k, counts[k]))
sys.stderr.write('bucket totals: %s\n' % dict(sorted(bucket_total.items())))
sys.stderr.write('\nsourced ONLY by a bare script mention (weakest evidence, verify by hand):\n')
for r in mention_only:
    sys.stderr.write('  %s\t%s\t%s\n' % r)
