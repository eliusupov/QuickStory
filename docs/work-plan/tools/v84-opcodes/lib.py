import json, re, collections, os

SRC = r'D:\games\MSv84\opcodes'
COS = r'D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\src\main\resources\opcodes'

def parse_yaml(p):
    rows = []; cur = None
    for line in open(os.path.join(SRC, p), encoding='utf-8'):
        if line.startswith('- op:'):
            cur = {'op': line.split(':', 1)[1].strip()}; rows.append(cur)
        elif cur is not None and re.match(r'^  [a-z_]+:', line):
            k, v = line.rstrip('\n').strip().split(':', 1)
            cur[k] = v.strip()
    for r in rows:
        r['opcode'] = int(r['opcode'])
    return rows

def key(r):
    return (r['direction'], r['op'])

def load_props(p):
    d = {}
    for line in open(os.path.join(COS, p), encoding='utf-8'):
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        k, v = line.split('=', 1)
        d[k.strip()] = int(v.strip(), 16) if v.strip().lower().startswith('0x') else int(v.strip())
    return d

def load_template():
    t = json.load(open(os.path.join(SRC, 'template_gms_84_1.json'), encoding='utf-8'))
    s = t['socket']
    cb = [(int(w['opCode'], 16), w.get('fname', ''), w['writer']) for w in s['writers']]
    sb = [(int(h['opCode'], 16), h.get('fname', ''), h['handler']) for h in s['handlers']]
    return cb, sb

def load_ida():
    return json.load(open(os.path.join(SRC, 'ida_export_gms_v84.json'), encoding='utf-8'))['functions']

def ida_text(fn):
    """All prose attached to an IDA function, concatenated."""
    parts = [fn.get('note', ''), fn.get('_note', '')]
    ns = fn.get('notes')
    if isinstance(ns, list): parts += [str(x) for x in ns]
    elif ns: parts.append(str(ns))
    for c in fn.get('calls', []) or []:
        parts.append(c.get('comment', '') or '')
    return ' \n'.join(p for p in parts if p)
