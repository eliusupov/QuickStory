"""The two client images this package reads, and the one address fact they share.

Both are flat: FILE OFFSET == VA - 0x400000.

  v83  the shipped MapleStory.exe/localhome.exe, on disk, section 1 mapped 1:1.
  v84  a MEMORY DUMP of the running client based at 0x400000. A dump, not the on-disk exe,
       because the v84 executable is packed - the on-disk bytes are not the bytes that run.

Neither is in the repo: the v83 image is the owner's live client (read-only, never written) and the
v84 dump is 12 MB of someone else's binary. The derived tables ARE committed, so the Java tests are
self-contained; only RE-derivation needs the images. Point them with:

    set COSMIC_V83_IMAGE=D:\\games\\MapleStory\\localhome.exe
    set COSMIC_V84_IMAGE=...\\v84_mem.bin

A second, independently taken v84 dump can be given as COSMIC_V84_IMAGE_B; a finding that does not
reproduce in both is not a finding.
"""
import os

IMG = 0x400000

_ENV = {
    '83': ('COSMIC_V83_IMAGE', r'D:\games\MapleStory\localhome.exe'),
    '84': ('COSMIC_V84_IMAGE', None),
    '84b': ('COSMIC_V84_IMAGE_B', None),
}
_cache = {}


def path(v):
    env, default = _ENV[v]
    p = os.environ.get(env) or default
    if not p:
        raise SystemExit(
            'Set %s to the v%s client image (see tools/v84/binmodel/images.py).' % (env, v))
    return p


def img(v):
    if v not in _cache:
        with open(path(v), 'rb') as f:
            _cache[v] = f.read()
    return _cache[v]
