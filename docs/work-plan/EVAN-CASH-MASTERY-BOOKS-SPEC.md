# Evan cash mastery books at Sly

**Class:** owner-requested

## Problem

Three Evan mastery books exist as cash items in GMS v84. The owner wants them available from the
mastery-book shop he built at Sly, NPC 2080001, for mesos instead of cash currency.

## Solution

Add those three books to Sly's existing stock, retaining the current stock and ordering. The
owner's prices are final:

| Book | Available at | Price |
| --- | ---: | ---: |
| Magic Guard Mastery Book | level 30 | 250,000 mesos |
| Magic Booster Mastery Book | level 60 | 500,000 mesos |
| Critical Magic Mastery Book | level 60 | 500,000 mesos |

## User story

As an Evan player, I can buy each of these cash-only mastery books from Sly for the owner-set meso
price, so I can progress without Cash Shop access.

## Decisions

- The v84 carve establishes the three item identities and their Evan skill targets. It does not
  establish meso prices; those are an explicit owner override.
- This is **owner-requested**, not a v84-parity gap. Do not relabel it as parity work.
- Sly's existing stock remains untouched; the three rows append after it.

## Out of scope

- Any other Evan book, skill book, mastery book, or Cash Shop catalog change.
- Changing a v84 cash item's sale state, NX price, period, or package.
- Repricing or reordering existing Sly stock.
