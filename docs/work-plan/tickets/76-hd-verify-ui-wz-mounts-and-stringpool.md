# 76 - HD S3: verify EzorsiaV2_UI.wz mounts on v84 and its four StringPool ids resolve

**Class:** owner-requested-hd
**Slice:** S3 of `docs/work-plan/HD-CLIENT-V84-SPEC.md`
**Blocked by:** None. Whether it *renders* needs a launch (S4); the structural check does not.
**Startable now:** YES - offline structural verification by an agent.

The assembled HD client ships `EzorsiaV2_UI.wz` (the HD UI archive). This ticket confirms, offline,
that the archive mounts on the v84 client's WZ surface and that the four StringPool ids it references
resolve - so a bad mount or a dangling id is caught before the owner launches, not during S4.

**Owner-requested HD row, not a v84-parity gap.** Do not relabel.

## What to do

- Confirm `EzorsiaV2_UI.wz` is structurally valid and mounts against the v84 WZ layout (it parses,
  its img directories load, and it does not collide with a stock v84 UI node in a way that would
  fail the client's loader).
- Confirm the **four StringPool ids** the archive references resolve to real StringPool entries on
  the v84 side - no dangling id, which would render as null text or crash the string lookup.

This is a read-only structural check. It does **not** prove the UI draws correctly - only that
nothing is missing or malformed. The render check is S4, on launch.

## Precedent

The WZ-mount and StringPool-resolution checks this project already runs offline (the `RealLoad`
style of structural test against the WZ tree) are the shape. The HD client assembly under tickets
30 / 30b is where `EzorsiaV2_UI.wz` came from.

## Acceptance criteria

- [ ] `EzorsiaV2_UI.wz` parses and mounts against the v84 WZ surface with no load error.
- [ ] All four referenced StringPool ids resolve to real entries; none dangles.
- [ ] A short offline check records the four ids and their resolved strings, so S4 can compare what
      it sees on screen against them.
- [ ] No client launch. Rendering is explicitly deferred to S4.

## Do not

- Do not claim the UI renders correctly - that is S4, on launch.
- Do not edit the archive. This ticket verifies; if it finds a defect, it reports it for S1/S2 or a
  follow-up, it does not patch here.
- Do not launch a client.
- Do not open server work; relabel nothing as parity.
