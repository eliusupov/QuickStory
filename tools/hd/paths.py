"""Paths for the v83->v84 HD port tooling.

Everything is overridable by env var so the tool is not welded to one machine.
The two v84 dumps and the Ezorsia checkout are large / not ours, so they live
outside the repo. Nothing here ever WRITES to a client directory.
"""
import os

HD = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HD, 'data')

# v83 reference image: an unpacked full dump, base 0x400000, file offset == VA - 0x400000.
V83 = os.environ.get('HD_V83', r'D:\games\MapleStory\localhome.exe')

# Two independent ReadProcessMemory dumps of the live v84 client. Same base property.
# A signature hit that does not reproduce in BOTH is not a hit.
_SCRATCH = os.environ.get(
    'HD_SCRATCH',
    r'C:\Users\elius\AppData\Local\Temp\claude'
    r'\D--games-MapleStory-Server-Cosmic--claude-worktrees-evan-dualblade'
    r'\153450ca-8c96-43b9-ab04-ac30f7fe175a\scratchpad')
V84_A = os.environ.get('HD_V84_A', os.path.join(_SCRATCH, 'v84_mem.bin'))
V84_B = os.environ.get('HD_V84_B', os.path.join(_SCRATCH, 'v84_mem2.bin'))

# git clone https://github.com/444Ro666/MapleEzorsia-v2
EZORSIA = os.environ.get('HD_EZORSIA', os.path.join(_SCRATCH, 'ezorsia-src', 'ezorsia'))

BASE = 0x400000
# .text extents. v83 raw == virtual == 0x7F8000; v84 image is 0x851000 of mapped code.
R83 = (0x1000, 0x1000 + 0x7F8000)
R84 = (0x1000, 0x1000 + 0x851000)

PATCHES = os.path.join(DATA, 'ezorsia-v83-patches.json')
RESOLVED = os.path.join(DATA, 'v84-resolved.json')
MANUAL = os.path.join(DATA, 'manual-sites.json')


def load(p):
    with open(p, 'rb') as f:
        return f.read()


def require(*ps):
    missing = [p for p in ps if not os.path.exists(p)]
    if missing:
        raise SystemExit('missing instrument(s):\n  ' + '\n  '.join(missing) +
                         '\n(set HD_V83 / HD_V84_A / HD_V84_B / HD_EZORSIA)')
