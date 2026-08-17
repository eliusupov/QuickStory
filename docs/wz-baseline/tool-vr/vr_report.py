#!/usr/bin/env python3
"""Camera-bounds report for a client Map.wz, from the TSV WzVR.exe emits.

    WzVR.exe <Map.wz> vr-v84.tsv
    python vr_report.py vr-v84.tsv [--per-map out.tsv]
    python vr_report.py vr-v84.tsv --selftest

What the client does with VR (v84 MapleStory.exe, function at 0x00657766; the same
function is at 0x00641EE0 in v83 with the fields at +0xF0..+0xFC instead of +0x104..+0x110):

    clampLeft   = VRLeft   + halfW      (0x006577D6  mov ebx, 400   -> patch P175: 640)
    clampTop    = VRTop    + halfH      (0x0065783D  add eax, 300   -> patch P176: 360)
    clampRight  = VRRight  - halfW      (0x006578A2  sub eax, ebx)
    clampBottom = VRBottom - halfH      (0x00657904  sub eax, 300   -> patch P177: 360)

    if (clampRight - clampLeft) <= 0:   clampLeft = clampRight = (clampLeft+clampRight)/2
    if (clampBottom - clampTop) <= 0:   clampTop = clampBottom = (clampTop+clampBottom)/2
                                        (0x00657925 - 0x00657973)

So the four stored values are the legal range of the camera CENTRE, and a map whose VR
span does not exceed the viewport collapses to a single point - the camera stops moving
and sits at the VR centre. That midpoint is (VRLeft+VRRight)/2 whatever halfW is, so the
resolution patch cannot move it: widening the viewport shows more around a fixed centre,
it never shifts or breaks the framing. This report counts which maps land in that state.

Absent VR nodes are not a separate case: each of the four is read with its own default
taken from a per-map rect the map loader fills (0x00657788 loads the global, and the
loader at 0x00A9098D tightens that same rect with VRLeft+20 / VRRight-20 / VRTop+65 /
VRBottom), then the same shrink and the same collapse run.
"""
import csv
import statistics
import sys

VIEWPORTS = ((800, 600, 'vanilla'), (1280, 720, 'HD patch'))


def load(path):
    with open(path, newline='') as f:
        return list(csv.DictReader(f, delimiter='\t'))


def span(r):
    return int(r['vrR']) - int(r['vrL']), int(r['vrB']) - int(r['vrT'])


def default_span(r):
    """No-VR maps: the loader's rect comes from map geometry; the VR read then applies
    -20/-60/+20/+100 around it. Foothold extent is the only part of that geometry this
    tool can see, so this is an estimate and is reported as one."""
    if r['nFh'] == '0':
        return None
    return ((int(r['fhR']) + 20) - (int(r['fhL']) - 20),
            (int(r['fhB']) + 100) - (int(r['fhT']) - 60))


def report(rows, per_map=None):
    full = [r for r in rows if r['hasVR'] == '4']
    none = [r for r in rows if r['hasVR'] == '0']
    part = [r for r in rows if r['hasVR'] not in ('0', '4')]
    print('maps                    %5d' % len(rows))
    print('  all four VR nodes     %5d' % len(full))
    print('  no VR nodes           %5d' % len(none))
    print('  partial VR            %5d' % len(part))
    print()

    for W, H, label in VIEWPORTS:
        cx = sum(1 for r in full if span(r)[0] <= W)
        cy = sum(1 for r in full if span(r)[1] <= H)
        ce = sum(1 for r in full if span(r)[0] <= W or span(r)[1] <= H)
        print('%4dx%-4d (%-8s) VR maps pinned:  X %4d   Y %4d   either %4d'
              % (W, H, label, cx, cy, ce))
        dx = sum(1 for r in none if (d := default_span(r)) and d[0] <= W)
        dy = sum(1 for r in none if (d := default_span(r)) and d[1] <= H)
        print('                          no-VR maps pinned: X %4d   Y %4d   (foothold estimate)'
              % (dx, dy))
    print()

    print('VR span exactly 800x600 %5d   (already pinned in the vanilla client)'
          % sum(1 for r in full if span(r) == (800, 600)))
    newly = [r for r in full
             if not (span(r)[0] <= 800 or span(r)[1] <= 600)
             and (span(r)[0] <= 1280 or span(r)[1] <= 720)]
    print('newly pinned by 1280x720%5d' % len(newly))
    print()

    dx = [max(0, 1280 - span(r)[0]) // 2 for r in full]
    dy = [max(0, 720 - span(r)[1]) // 2 for r in full]
    ox = [max(0, 800 - span(r)[0]) // 2 for r in full]
    oy = [max(0, 600 - span(r)[1]) // 2 for r in full]
    print('px visible beyond VR, per side   X median %3d max %3d   Y median %3d max %3d'
          % (statistics.median(dx), max(dx), statistics.median(dy), max(dy)))
    print('  same figure in the vanilla client  X median %3d max %3d   Y median %3d max %3d'
          % (statistics.median(ox), max(ox), statistics.median(oy), max(oy)))
    print()

    coll = [r for r in full if span(r)[0] <= 1280 or span(r)[1] <= 720]
    tiled = [r for r in coll if r['bgTiled'] == '1']
    nobg = [r for r in coll if r['nBack'] == '0'
            or int(r['backNoImg']) == int(r['nBack'])]
    flat = [r for r in coll if r not in tiled and r not in nobg]
    short = [r for r in flat if int(r['bgFlatW']) < 1280 or int(r['bgFlatH']) < 720]
    print('of the %d pinned maps at 1280x720:' % len(coll))
    print('  %4d have a repeating backdrop (covers any viewport)' % len(tiled))
    print('  %4d draw no backdrop at all (unchanged by resolution)' % len(nobg))
    print('  %4d have a single flat backdrop, of which %d are smaller than 1280x720'
          % (len(flat), len(short)))

    if per_map:
        with open(per_map, 'w', newline='') as f:
            w = csv.writer(f, delimiter='\t', lineterminator='\n')
            w.writerow(['mapid', 'source', 'spanX', 'spanY',
                        'scrollX_800x600', 'scrollY_800x600',
                        'scrollX_1280x720', 'scrollY_1280x720', 'pinned_1280x720'])
            for r in rows:
                if r['hasVR'] == '4':
                    sx, sy, src = *span(r), 'VR'
                else:
                    d = default_span(r)
                    if d is None:
                        w.writerow([r['mapid'], 'none', '', '', '', '', '', '', '?'])
                        continue
                    sx, sy, src = *d, 'foothold-est'
                a, b = max(0, sx - 800), max(0, sy - 600)
                c, e = max(0, sx - 1280), max(0, sy - 720)
                w.writerow([r['mapid'], src, sx, sy, a, b, c, e,
                            1 if (c == 0 or e == 0) else 0])
        print()
        print('per-map spans written to', per_map)


def selftest(rows):
    """Pinned by the pristine v84 archive; these are the numbers the report rests on."""
    assert len(rows) == 4505, len(rows)
    full = [r for r in rows if r['hasVR'] == '4']
    none = [r for r in rows if r['hasVR'] == '0']
    assert len(full) == 3445 and len(none) == 1060
    assert len(full) + len(none) == len(rows), 'no map carries a partial VR set'
    assert sum(1 for r in full if span(r) == (800, 600)) == 291
    # the client's own predicate is <=, not <: a span equal to the viewport still collapses
    assert sum(1 for r in full if span(r)[0] <= 1280 or span(r)[1] <= 720) == 2398
    assert sum(1 for r in full if span(r)[0] <= 800 or span(r)[1] <= 600) == 814
    # Henesys carries no VR and is 7.4k wide - proof the no-VR default is per-map geometry
    hen = next(r for r in rows if r['mapid'] == '100000000')
    assert hen['hasVR'] == '0' and default_span(hen)[0] > 7000, hen
    print('selftest ok')


if __name__ == '__main__':
    rows = load(sys.argv[1])
    if '--selftest' in sys.argv:
        selftest(rows)
    else:
        i = sys.argv.index('--per-map') if '--per-map' in sys.argv else None
        report(rows, sys.argv[i + 1] if i else None)
