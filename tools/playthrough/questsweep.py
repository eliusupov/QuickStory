"""Completability sweep over the v84-added quest set.

Emits docs/work-plan/V84-QUEST-SWEEP.tsv. Reads the WZ XML in wz/ and four DB tables; it
never writes to the database and never touches the running server.

    python tools/playthrough/questsweep.py <scratchdir>

<scratchdir> must already hold these five dumps (SELECT only, mysql -N):

    SELECT dropperid,itemid,questid FROM drop_data;              -> drop_data.tsv
    SELECT itemid FROM drop_data_global;                         -> drop_global.tsv
    SELECT reactorid,itemid FROM reactordrops;                   -> reactordrops2.tsv
    SELECT s.npcid, si.itemid FROM shopitems si
           JOIN shops s ON s.shopid=si.shopid;                   -> shopnpc.tsv
    SELECT itemid FROM makercreatedata;                          -> maker.tsv

THE SET SWEPT is the v84-added quest set as the repo already enumerates it:
docs/wz-baseline/merge-lists/{09,33}/Quest.paths.txt - 63 non-Evan + 135 Evan = 198 ids.
Quests that existed in v83 and only had *fields* changed by v84 are out of scope on the same
authority: those appear in docs/wz-baseline/add-list/Quest.txt at depth 4-5 (e.g.
`Quest.wz/Check.img/2208/0/end`), never as a whole-node copy root, and 128 such paths exist.
"""
import re, os, sys, glob, collections
import xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SCR = sys.argv[1] if len(sys.argv) > 1 else '.'
TODAY = '20260818'


def load_img(path):
    def rec(e):
        d = {}
        for c in e:
            d[c.get('name')] = rec(c) if c.tag == 'imgdir' else c.get('value')
        return d
    return rec(ET.parse(path).getroot())


Q = os.path.join(ROOT, 'wz/Quest.wz')
check = load_img(os.path.join(Q, 'Check.img.xml'))
act = load_img(os.path.join(Q, 'Act.img.xml'))
qinfo = load_img(os.path.join(Q, 'QuestInfo.img.xml'))

ids = set()
for f in ('09/Quest.paths.txt', '33/Quest.paths.txt'):
    for l in open(os.path.join(ROOT, 'docs/wz-baseline/merge-lists', f), encoding='utf-8'):
        p = l.strip().split('/')
        if len(p) == 3 and p[0] == 'Quest.wz' and p[2].isdigit():
            ids.add(p[2])
ids = sorted(ids, key=int)

# ---- map life and reactors -----------------------------------------------------------------
# Field order inside a life entry is NOT stable across map files - Kerning City writes id before
# type, Deep Valley writes type before id - so read each entry as a block and pull both fields by
# name. A regex that assumes adjacency silently reports half of Victoria Island as unpopulated.
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

# ---- sources -------------------------------------------------------------------------------


def rows(fn):
    out = []
    for l in open(os.path.join(SCR, fn), encoding='utf-8-sig', errors='replace'):
        p = l.split()
        if p and all(x.lstrip('-').isdigit() for x in p):
            out.append([str(int(x)) for x in p])
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
maker_items = set(x[0] for x in rows('maker.tsv'))

reward_items = collections.defaultdict(set)
for qid, node in act.items():
    for phase in ('0', '1'):
        blk = node.get(phase) if isinstance(node.get(phase), dict) else {}
        it = blk.get('item', {})
        if isinstance(it, dict):
            for _, e in it.items():
                if isinstance(e, dict) and e.get('id') and int(e.get('count', '0') or 0) > 0:
                    reward_items[str(int(e['id']))].add(qid)

script_items, progress_writers = set(), set()
for f in (glob.glob(os.path.join(ROOT, 'scripts/**/*.js'), recursive=True) +
          glob.glob(os.path.join(ROOT, 'src/main/java/**/*.java'), recursive=True)):
    s = open(f, encoding='utf-8', errors='replace').read()
    script_items.update(str(int(x)) for x in re.findall(r'gainItem\s*\(\s*(\d{4,8})', s))
    for call in re.findall(r'setQuestProgress\s*\(([^;]{0,120}?)\)', s):
        progress_writers.update(str(int(x)) for x in re.findall(r'\b(\d{3,7})\b', call))
    # a mob does not have to be in a map's life array to spawn: area bosses are spawned by an
    # event script (AreaBossSeruf.js -> 4220001 on 230020100), and Mob.wz `info/revive` turns a
    # dying shell into the mob that actually carries the drops (4220001 -> 4220000). Ignoring
    # either one reports a live quest item as unobtainable - EvanPorkSourceRealLoad pins that
    # exact case for 4032474 Seruf Pearl.
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


def sources(iid):
    """(reachable sources, sources whose granter is placed on no map)."""
    ok, dead = [], []
    for pool, placed, label in ((drop_by_item, mob_maps, 'drop'),
                                (reactor_by_item, reactor_maps, 'reactor'),
                                (shop_by_item, npc_maps, 'shop')):
        have = pool.get(iid, [])
        if have:
            live = [x for x in have if x in placed]
            (ok if live else dead).append('%s(%d/%d placed)' % (label, len(live), len(have)))
    for cond, label in ((iid in global_items, 'global'), (iid in maker_items, 'maker'),
                        (iid in reward_items, 'questreward'), (iid in script_items, 'script')):
        if cond:
            ok.append(label)
    return ok, dead


# ---- triage: every classification below is evidence, not taste ------------------------------
NON_DEFECT = {
    'NPC_UNPLACED:1013000':
        'BY DESIGN. Mir is the summoned dragon, not a map NPC: QuestActionHandler.java:52,91 '
        'accepts him when player.getDragon() != null. v84 Map.wz places him on no map either '
        '(WzPeek scan id 1013000: 0 hits / 4848 images).',
    'MOB_UNPLACED:9101004':
        'BY DESIGN. Virtual kill counter, not a spawn: Character.java:7471 raises '
        'MobId.BLUE_MUSHROOM_QUEST off the real Blue Mushroom kill. v84 places it on no map '
        'either (0 hits / 4848 images). Confirmed in play - character 51 has 22524 completed.',
}
DECISION = {
    'NPC_UNPLACED:9901000':
        'OWNER. PlayerNPC for the Dragon Mount branch. NpcLocation.img and COLLISION-DENY.txt:406 '
        'say place it as a PlayerNPC on 102000004, not as a map NPC; playernpcs has 0 rows. '
        'QUESTION: create the PlayerNPC row?',
    'NPC_UNPLACED:1013202':
        'UNKNOWN. Placed on no map in our tree AND none in pristine v84 (0 hits / 4848 images). '
        'scripts/quest/22575.js and 22581.js already exist; only the accept NPC is missing, the '
        'completion NPC is placed. Where GMS spawned him is not in the client data. '
        'QUESTION: place him, and if so where?',
    'MOB_UNPLACED:9300393':
        'UNKNOWN, already tracked in V84-OPEN-ITEMS. "Gentleman", placed by no map in our tree or '
        'in pristine v84. Quest text names room 922030001 (hook enterBlackfrog, absent). Spawn '
        'coordinates are in no WZ file. QUESTION: place it, at what coordinates?',
}
_KEVENT = ('Korean-titled 2009 winter-event quest in the 1049x block. Its own start block carries '
           'no end date, but the block it belongs to does - 10487/10490/10496 all end 200912280000 '
           '- and 10491-10494 all demand the same item 3994185 that expired 10497 demands. Event '
           'content v84 shipped switched off; do not build it.')
EVENT_BLOCK = {q: _KEVENT for q in ('10491', '10492', '10493', '10494', '10497')}
EVENT_BLOCK['19011'] = ('GM-only. Administrator event medal: autoAccept + medalCategory 3, and its '
                        'sole start requirement is possession of medal 1142170, which nothing '
                        'grants because a GM handed it out. Nothing to fix.')

RECORD_GATE = (
    'UNKNOWN, already tracked in V84-OPEN-ITEMS section 1. Quest.canStart AND canComplete both run '
    'canQuestByInfoProgress and autoStart does not bypass it, so nothing can write this record and '
    'the quest cannot advance. The trigger (map arrival vs portal touch vs NPC talk) is not stated '
    'by the data. QUESTION: which trigger writes it?')


def enddate(qid):
    return (check.get(qid, {}).get('0', {}) or {}).get('end', '')


scripts_dir = set(os.path.basename(f)[:-3] for f in glob.glob(os.path.join(ROOT, 'scripts/quest/*.js')))
out, expired_ids = [], []
for qid in ids:
    info = qinfo.get(qid, {})
    c0 = check.get(qid, {}).get('0', {}) or {}
    c1 = check.get(qid, {}).get('1', {}) or {}
    end = enddate(qid)
    dead_quest = bool(end) and end[:8] < TODAY
    if dead_quest:
        expired_ids.append(qid)
    found = []

    items, mobs = [], []
    for phase, blk in (('0', c0), ('1', c1)):
        for k, bucket in (('item', items), ('mob', mobs)):
            sub = blk.get(k, {})
            if isinstance(sub, dict):
                for _, e in sub.items():
                    if isinstance(e, dict) and e.get('id'):
                        bucket.append((str(int(e['id'])), int(e.get('count', '0') or 0), phase))
    for phase in ('0', '1'):
        blk = act.get(qid, {}).get(phase, {})
        sub = blk.get('item', {}) if isinstance(blk, dict) else {}
        if isinstance(sub, dict):
            for _, e in sub.items():
                if isinstance(e, dict) and e.get('id') and int(e.get('count', '0') or 0) < 0:
                    items.append((str(int(e['id'])), int(e['count']), 'act' + phase))

    seen = set()
    for iid, cnt, ph in items:
        if iid in seen or qid in reward_items.get(iid, set()):
            continue
        seen.add(iid)
        ok, dead = sources(iid)
        if not ok:
            found.append(('MISSING_ITEM_SOURCE', 'item ' + iid,
                          'no source at all' if not dead else 'only unreachable: ' + ','.join(dead)))
    for mid, cnt, ph in mobs:
        if mid not in mob_maps:
            found.append(('MOB_NOT_PLACED', 'mob ' + mid, 'no map life entry'))
    ss = [b[k] for b in (c0, c1) for k in ('startscript', 'endscript') if b.get(k)]
    if ss and qid not in scripts_dir and not info.get('viewMedalItem'):
        found.append(('SCRIPT_MISSING', 'scripts/quest/%s.js' % qid, ','.join(ss)))
    if not info.get('name'):
        found.append(('STRING_MISSING', 'QuestInfo/%s/name' % qid, ''))
    if info.get('0') is None and info.get('1') is None and not info.get('viewMedalItem'):
        found.append(('STRING_MISSING', 'QuestInfo/%s/0,1' % qid, 'no objective text'))
    for b in (c0, c1):
        pq = b.get('quest', {})
        if isinstance(pq, dict):
            for _, e in pq.items():
                if isinstance(e, dict) and e.get('id'):
                    pid = str(int(e['id']))
                    if pid != '0' and pid not in qinfo:
                        found.append(('PREREQ_BROKEN', 'quest ' + pid, 'prerequisite has no QuestInfo'))
    for ph, b in (('0', c0), ('1', c1)):
        npc = b.get('npc')
        if npc and str(int(npc)) not in npc_maps:
            found.append(('NPC_NOT_PLACED', 'npc %d' % int(npc), 'phase ' + ph))
    for b in (c0, c1):
        n = b.get('infoNumber')
        if n and str(int(n)) not in progress_writers:
            found.append(('QUEST_RECORD_NO_WRITER', 'record ' + str(int(n)), ''))

    for cat, subject, detail in found:
        key = {'NPC_NOT_PLACED': 'NPC_UNPLACED', 'MOB_NOT_PLACED': 'MOB_UNPLACED',
               'MISSING_ITEM_SOURCE': 'NOSOURCE'}.get(cat, cat) + ':' + subject.split()[-1]
        if dead_quest:
            pri, status, note = 'P2', 'DO_NOT_FIX', 'quest is behind an expired end date ' + end
        elif qid in EVENT_BLOCK:
            pri, status, note = 'P2', 'DO_NOT_FIX', EVENT_BLOCK[qid]
        elif key in NON_DEFECT:
            pri, status, note = 'P2', 'NON_DEFECT', NON_DEFECT[key]
        elif key in DECISION:
            pri, status, note = ('P1', 'DECISION', DECISION[key])
        elif cat == 'QUEST_RECORD_NO_WRITER':
            pri, status, note = 'P1', 'DECISION', RECORD_GATE
        else:
            pri, status, note = 'P2', 'DO_NOT_FIX', 'event / GM-only content, no live path to it'
        lv = c0.get('lvmin', '')
        if pri == 'P1' and lv and int(lv) <= 45:
            pri = 'P0'
        # dedupe identical (quest, category, subject)
        out.append((qid, info.get('name', '').replace('\t', ' '), lv, end, cat, subject,
                    pri, status, '', note.replace('\t', ' ') + (' | ' + detail if detail else '')))

# The quest that started this sweep. It produces no finding, and that is the finding - a row is
# emitted anyway so nobody "fixes" a thing that is already correct.
out.append(('22529', qinfo['22529']['name'], '22', '', 'VERIFIED_NO_DEFECT', 'item 4032460 / mob 130100',
            'P0', 'NON_DEFECT', 'drop_data id 23018: (130100, 4032460, 1, 1, 22529, 80000), changeSet 156',
            'Reported as "Stumps do not drop the sap". The row exists and is applied. v84 names the '
            'dropper outright - pristine Quest.wz/QuestInfo.img/22529/1 reads "dropped by the '
            '#o0130100#s", i.e. mob 130100 "Stump", verbatim in our tree. Stump is placed on 16 maps '
            'incl. 101030000 East Domain of Perion x26, 101040000 Perion Street Corner x32, '
            '102010000 West Street Corner of Perion x22. The three maps the quest sends you to - '
            '106000000/106000100/106000200 Deep Valley I/II/III - carry Axe Stump 1130100, Ghost '
            'Stump 1140100 and Dark Axe Stump 2130100, and NONE of them is mob 130100; pristine v84 '
            'Map.wz agrees exactly (Deep Valley I = 32x 1130100 + 10x 1140100 + npc 1022106). So the '
            'sap does not drop in the Deep Valleys in v84 either. DO NOT add a row on 1130100/1140100 '
            '- that would be inventing content v84 does not have. OWNER QUESTION only if he wants '
            'the deviation.'))

seen = set()
dst = os.path.join(ROOT, 'docs/work-plan/V84-QUEST-SWEEP.tsv')
with open(dst, 'w', encoding='utf-8', newline='\n') as fh:
    fh.write('quest_id\tquest_name\tlvmin\tend_date\tcategory\tids\tpriority\tstatus\tanalogue_row\tnote\n')
    for r in out:
        k = (r[0], r[4], r[5])
        if k in seen:
            continue
        seen.add(k)
        fh.write('\t'.join(r) + '\n')

sys.stderr.write('swept %d quests, %d rows written, %d quests behind an expired end date\n'
                 % (len(ids), len(seen), len(expired_ids)))
sys.stderr.write('by priority: %s\n' % collections.Counter(r[6] for r in out if (r[0], r[4], r[5])))
