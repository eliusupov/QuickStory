# Tulcus regular-scroll-only stock

## Owner decision

Tulcus shop 1052104 should sell regular gray scrolls only. Remove the exact 56 currently stocked
rows identified from the owner's screenshots and pristine v84 icon data:

- 11 stocked Balrog scrolls: `2040728..2040738`. The v84-only Twilight scroll `2040739` is not
  stocked and needs no deletion.
- 31 white-icon 100% scrolls: `2040041, 2040042, 2040334, 2040430, 2040538, 2040539, 2040630,
  2040740, 2040741, 2040742, 2040829, 2040830, 2040936, 2041066, 2041067, 2043023, 2043117,
  2043217, 2043312, 2043712, 2043812, 2044025, 2044117, 2044217, 2044317, 2044417, 2044512,
  2044612, 2044712, 2044815, 2044908`.
- All six 6th Anniversary scrolls: `2049105..2049110`.
- Eight pictured specials: `2041200, 2049101, 2049102, 2049103, 2049104, 2049112, 2049113,
  2049114`.

These sets are disjoint: `11 + 31 + 6 + 8 = 56` unique and physical rows. In pristine v84
`Item.wz`, every white-icon 100% row has the same `icon` / `iconRaw` byte hashes
`7C3A3FE118A80B62` / `FE6794DD34F17BC7`; the six anniversary rows deliberately reuse that same
icon pair. The 31 exact ids, not their names, define the white-icon 100% scope.

## Delivery

Do not edit applied changeSets 178-181. Add changeSet 182 to delete only the 56 exact post-181
tuples and restore those exact tuples on rollback. Add changeSet 183 to re-sort every remaining
row under the existing purpose-first contract and use the exact inverse map for rollback.

Post-state: **375 physical rows, 373 unique item ids**, unique positions `104..1600` in steps of
four. Keep every other scroll, including ordinary gray 100% scrolls.
