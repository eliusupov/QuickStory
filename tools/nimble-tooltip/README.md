# Nimble Feet client tooltip

For `USE_ULTRA_NIMBLE_FEET: true`: speed +10/+20/+30, duration 340 seconds,
no cooldown, MP cost 4/7/10. Updates `String.wz/Skill.img` nodes `0001002`,
`10001002`, `20001002` (Agile Body), and `20011002`. Names stay unchanged.

```powershell
dotnet run --project tools/nimble-tooltip -c Release -- D:\games\MapleStory\String.wz D:\games\MapleStory\Server\Cosmic\build\nimble-tooltip-new
```

Requires the existing HaRepacker MapleLib checkout referenced by the project.
The output directory must be new and outside protected client data.
The tool reopens its output and compares all 102,733 archive nodes, allowing
only the 16 specified string value changes. It retains `String.original.wz`
for rollback and writes `changes.tsv` and `verification.txt` with hashes.

**Installation requires an explicit owner exception for protected
`D:\games\MapleStory\String.wz`, with the client closed.** Then copy the
staged `String.wz` to that exact target and verify its SHA256 matches.
Rollback copies `String.original.wz` back to the same target with the client
closed. No server restart is needed for the tooltip; reopen the client.
