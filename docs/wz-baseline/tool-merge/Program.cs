using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.Serializer;

// WzMerge — additive-only node importer for the v83 -> v84 upgrade.
//
//   WzMerge dump   <wz> <path/under/wz> [depth]
//   WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt>
//                  --deny <denyList> [--force <forceList>] [--live <liveWz>]
//   WzMerge xml    <sourceWz> <xmlRoot>            <pathsFile> <conflictsTxt>
//                  --deny <denyList> [--force <forceList>] [-]
//   WzMerge verify <wz> <pathsFile> [--baseline <targetWz>]
//   WzMerge hash   <wz> <path/under/wz>
//   WzMerge deps   <mapWz> <mapId|Map/MapN/<id>.img> <addListDir>
//   WzMerge guard  <outWz> [--baseline <targetWz>]
//   WzMerge selftest
//
// EXIT CODE CONTRACT (scripted callers depend on it; see WZ-MERGE-PROCEDURE.md):
//   0  success — every requested path was written (or, on a dry run, would be)
//   1  unexpected failure (exception); nothing installable was produced
//   2  bad invocation — usage error OR a safety guard refused the arguments
//   3  completed, but >=1 manifest row was REFUSED. conflicts.txt is non-empty.
//      A dry run that finds collisions exits 3 by design; that is the answer, not a fault.
//   4  post-write verification FAILED. The output is not trustworthy: DO NOT install it.
//   5  completed and refused rows, and added NOTHING AT ALL. (M1: 3 conflated "added 5000,
//      refused 1" with "added 0, refused 1" — the second is the one that reads as "nothing
//      owed" and is almost always a mistake in the manifest or the arguments.)
// "added 0, refused N" used to exit 0, which let a scripted 04-09 loop report green
// having imported nothing. It exits 5 now.
//
// A paths file with ZERO manifest rows is a hard error (exit 2), never a successful no-op:
// `WzMerge deps ... > f` failing leaves f truncated to nothing by the shell, and
// add-list/{Base,TamingMob}.txt are legitimately 0-row files. Both used to exit 0 with
// "added 0, refused 0", which reads as "the client already has everything".
//
// TWO LISTS GOVERN WHAT MAY BE WRITTEN, and `merge`/`xml` require the first of them:
//   --deny  <file>   paths that must NOT be written even though the target lacks them. The
//                    additive-only gate only refuses paths that already EXIST, so it is
//                    structurally blind to a harmful v84 *addition* — a server-allocated NPC
//                    id, a positional-array slot spliced into someone else's list.
//   --force <file>   paths the operator authorises overwriting. This is the ONLY way to
//                    overwrite an existing node; without it the additive-only gate stands.
// Same format, one parser: "<path>\t# <reason>", blanks and '#' comments ignored, each listed
// path is a ROOT (nothing at or beneath it is written / it may be overwritten). Deny beats
// force, and a path in both lists is a hard exit rather than a silent resolution.
// Data: docs/wz-baseline/merge-lists/COLLISION-{DENY,FORCE}.txt.
//
// Paths in <pathsFile> are manifest lines, exactly as docs/wz-baseline/add-list/*.txt
// writes them: "Item.wz/Consume/0200.img/02001500". '#' and blanks ignored. BOTH `merge`
// and `xml` derive the expected "<Name>.wz" root of those lines from the SOURCE argument,
// so a renamed staging copy of the target never breaks manifest matching.
//
// The additive-only rule is enforced HERE, in the write path, not audited afterwards:
// a node whose path already exists in the target is never written, it is appended to
// <conflictsTxt> and skipped. A post-hoc presence diff cannot prove this (a destructive
// overwrite preserves paths too) — correctness comes from construction. See
// docs/work-plan/WZ-MERGE-PROCEDURE.md.
//
// conflicts.txt is a DELIVERABLE, not a log: it is the list of v84 changes this rule
// dropped on the floor. v84 edits to existing nodes (a portal added to an existing map,
// a mob merely renamed) land there and need a human decision.

// Anything the tool refuses on the ARGUMENTS — a bad flag, a 0-row manifest, a deny/force
// overlap, a staging violation — is exit 2, not exit 1. Throwing it means every check can be
// written where it belongs instead of threading a return code back up.
sealed class BadArgs : Exception { public BadArgs(string m) : base(m) { } }

static class Program
{
    static readonly List<string> Conflicts = new();

    static int Main(string[] args)
    {
        if (args.Length == 0) { Usage(); return 2; }
        try
        {
            switch (args[0].ToLowerInvariant())
            {
                case "dump": return Dump(args);
                case "merge": return Merge(args);
                case "xml": return Xml(args);
                case "verify": return VerifyCmd(args);
                case "hash": return Hash(args);
                case "deps": return Deps(args);
                case "guard": return Guard(args);
                case "selftest": return SelfTest();
                default: Usage(); return 2;
            }
        }
        catch (BadArgs ex)
        {
            Console.Error.WriteLine("REFUSED: " + ex.Message);
            return 2;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("FAILED: " + ex.Message);
            return 1;
        }
    }

    static void Usage() => Console.Error.WriteLine(
        "WzMerge dump   <wz> <path/under/wz> [depth]\n" +
        "WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt> --deny <denyList> [--force <forceList>] [--live <liveWz>]\n" +
        "WzMerge xml    <sourceWz> <xmlRoot> <pathsFile> <conflictsTxt> --deny <denyList> [--force <forceList>] [-]\n" +
        "WzMerge verify <wz> <pathsFile> [--baseline <targetWz>]\n" +
        "WzMerge hash   <wz> <path/under/wz>\n" +
        "WzMerge deps   <mapWz> <mapId|Map/MapN/<id>.img> <addListDir>\n" +
        "WzMerge guard  <outWz> [--baseline <targetWz>]   (--baseline REQUIRED if <outWz> exists)\n" +
        "WzMerge selftest\n" +
        "  '-' in the <outWz> slot (merge) or as a trailing arg (xml) = DRY RUN.\n" +
        "  --live is REQUIRED for a real merge: it is hashed against <targetWz> to prove the\n" +
        "  staging snapshot is not stale. 'guard' answers 'may I write here?' and writes nothing;\n" +
        "  if a file already exists at <outWz> it ALSO asserts positional-array continuity and\n" +
        "  exits 4 on a holed array. --baseline discounts holes the merge target already had.\n" +
        "exit: 0 ok | 1 error | 2 bad args/refused by a safety guard | 3 rows refused | 4 verification failed | 5 refused rows and added nothing");

    // ---------- open ----------

    // Same three-IV fallback as WzDump: a wrong IV throws, that is a failed candidate,
    // not a failed file. HaRepacker's default write path is BMS, so anything re-saved
    // by hand will land on the second candidate.
    static (WzFile file, WzMapleVersion ver) Open(string path)
    {
        var errs = new List<string>();
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try
            {
                f = new WzFile(path, -1, ver);
                if (f.ParseWzFile() == WzFileParseStatus.Success) return (f, ver);
                errs.Add($"{ver}: parse status not Success");
            }
            catch (Exception ex) { errs.Add($"{ver}: {ex.GetType().Name} {ex.Message}"); }
            f?.Dispose();
        }
        throw new Exception($"cannot open {path}: {string.Join(" | ", errs)}");
    }

    // ---------- path resolution ----------

    // Deliberately not WzFile.GetObjectFromPath: that overload needs the global
    // WzFileManager.fileManager singleton and returns null without it.
    static WzObject? Resolve(WzFile file, IReadOnlyList<string> segs, int count)
    {
        WzObject? cur = file.WzDirectory;
        for (int i = 0; i < count; i++)
        {
            string s = segs[i];
            cur = cur switch
            {
                WzDirectory d => d[s],
                WzImage img => img[s],                       // indexer parses on demand
                WzImageProperty p => p.WzProperties?.FirstOrDefault(
                    c => string.Equals(c.Name, s, StringComparison.OrdinalIgnoreCase)),
                _ => null
            };
            if (cur == null) return null;
        }
        return cur;
    }

    // manifest line -> segments after the leading "<Name>.wz"
    static string[] Rel(string manifestPath, string wzFileName)
    {
        var segs = manifestPath.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segs.Length < 2 || !segs[0].Equals(wzFileName, StringComparison.OrdinalIgnoreCase))
            throw new Exception($"path '{manifestPath}' is not rooted at '{wzFileName}'");
        return segs.Skip(1).ToArray();
    }

    // H2: `merge`/`xml`/`verify` speak manifest form ("Map.wz/Back/x.img"); `dump`/`hash`/`deps`
    // historically spoke root-relative form ("Back/x.img") and nothing said so, which is why
    // section 6.1 of the procedure fed `hash` manifest rows it could not resolve. Accept both,
    // everywhere, by stripping a leading "<Name>.wz" when it matches the file being read.
    static string[] Segs(string path, string wzFileName)
    {
        var segs = path.Split('/', StringSplitOptions.RemoveEmptyEntries);
        return segs.Length > 0 && segs[0].Equals(wzFileName, StringComparison.OrdinalIgnoreCase)
            ? segs.Skip(1).ToArray() : segs;
    }

    // B4/H4: a paths file with zero rows used to run the loop zero times and exit 0 —
    // "added 0, refused 0", which reads as "the target already has everything". Two real ways
    // to get one: the procedure's `WzMerge deps ... > f` pipeline, where a failing deps leaves
    // f truncated to nothing by the shell before the tool ever ran; and
    // add-list/{Base,TamingMob}.txt, which are genuinely 0-row files. Never succeed on one.
    static List<string> ReadPaths(string file)
    {
        if (!File.Exists(file)) throw new BadArgs($"paths file does not exist: {file}");
        var rows = File.ReadAllLines(file)
            .Select(l => l.Trim())
            .Where(l => l.Length > 0 && !l.StartsWith('#'))
            .ToList();
        if (rows.Count == 0)
            throw new BadArgs($"{file} holds 0 manifest rows (empty, or nothing but comments). " +
                "A merge of nothing must not report success — if the file was produced by a redirect, " +
                "the producing command failed after the shell had already truncated it.");
        return rows;
    }

    // ---------- deny / force lists (B1) ----------

    // COLLISION-DENY.txt and COLLISION-FORCE.txt are the same format on purpose, so this is the
    // one parser: "<path>\t# <reason>", blank lines and '#' comments ignored. Every listed path
    // is a ROOT — deny means nothing at or beneath it is written, force means it may be
    // overwritten — which is why neither file needs wildcard syntax (17 `reward` parents cover
    // all 36 at-risk MonsterBook slots).
    static List<(string path, string reason)> LoadRoots(string kind, string file)
    {
        if (!File.Exists(file)) throw new BadArgs($"{kind}-list does not exist: {file}");
        var rows = new List<(string, string)>();
        foreach (var raw in File.ReadAllLines(file))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line[0] == '#') continue;
            int tab = line.IndexOf('\t');
            string p = (tab < 0 ? line : line[..tab]).Trim().Replace('\\', '/').Trim('/');
            string why = tab < 0 ? "" : line[(tab + 1)..].TrimStart('#', ' ', '\t').TrimEnd();
            if (p.Length > 0) rows.Add((p, why));
        }
        if (rows.Count == 0)
            throw new BadArgs($"{kind}-list {file} holds 0 rows. An empty {kind}-list is never what was meant; " +
                // ponytail: no row count here. The first version said "has 40"; the list is at 188
                // and still growing, and a stale number in an error message is worse than none.
                (kind == "force" ? "omit --force instead." : "the committed COLLISION-DENY.txt is never empty — check the path."));
        return rows;
    }

    static bool Under(string path, string root) =>
        path.Equals(root, StringComparison.OrdinalIgnoreCase) ||
        path.StartsWith(root + "/", StringComparison.OrdinalIgnoreCase);

    // the row is the root, or sits beneath it — writing the row writes listed content
    static (string path, string reason)? RootOver(List<(string path, string reason)> roots, string row)
    {
        foreach (var r in roots) if (Under(row, r.path)) return r;
        return null;
    }

    // a root sits strictly BENEATH the row — writing the row (a copy root) drags the listed
    // subtree in with it, and there is no way to write "all of it except that". Refuse.
    static (string path, string reason)? RootInside(List<(string path, string reason)> roots, string row)
    {
        foreach (var r in roots) if (!Under(row, r.path) && Under(r.path, row)) return r;
        return null;
    }

    // ONE gate for both subcommands: is this manifest row allowed to be written at all?
    // Returns a refusal reason, or null. Both `merge` and `xml` route through it so the deny
    // semantics cannot drift apart between them — and so the row is NORMALISED the same way the
    // write path normalises it before any comparison. Without that, `Map.wz//Npc/2159.img`
    // resolves and writes (Split(RemoveEmptyEntries) eats the empty segment) while matching no
    // deny root, which is a bypass.
    static string? GateRefusal(List<(string path, string reason)> deny, string wzName, string manifestPath)
    {
        var segs = manifestPath.Split('/', StringSplitOptions.RemoveEmptyEntries);
        if (segs.Length < 1 || !segs[0].Equals(wzName, StringComparison.OrdinalIgnoreCase))
            return $"row is rooted at {(segs.Length > 0 ? segs[0] : "nothing")}, not {wzName} — handle it in that file's own run";
        string row = string.Join('/', segs);
        if (RootOver(deny, row) is { } hit)
            return $"DENIED by deny-list [{hit.path}]: {hit.reason}";
        // The row is a copy root with a denied node inside it. Writing "all of it except that" is
        // not something the write path can do, so refuse the row and say why.
        if (RootInside(deny, row) is { } inside)
            return $"DENIED by deny-list: this row is a copy root containing the denied node '{inside.path}' ({inside.reason}), and a partial write is not possible";
        return null;
    }

    // Deny beats force. An overlap is an operator error worth stopping for — resolving it
    // silently in either direction hides the fact that two decisions contradict each other.
    static void AssertNoOverlap(List<(string path, string reason)> deny, List<(string path, string reason)> force)
    {
        foreach (var d in deny)
            foreach (var f in force)
                if (Under(d.path, f.path) || Under(f.path, d.path))
                    throw new BadArgs($"deny/force overlap: deny '{d.path}' and force '{f.path}' cover the same node. " +
                        "Deny wins by rule, but an overlap means two decisions contradict each other — fix the lists.");
    }

    // Slots this run REFUSED, keyed by container exactly as RequestedSlots keys it. Recorded
    // HERE, in the one funnel every refusal passes through, because the hazard does not care WHY
    // a slot was refused: deny-list, MISSING IN SOURCE, already-exists, unsupported shape and the
    // array gate itself all remove a slot from the run, and any one of them turns a later slot's
    // "append" into a HOLE. See RunningRefusal / the continuity sweep in Merge().
    static readonly Dictionary<string, SortedSet<int>> RefusedSlots = new(StringComparer.OrdinalIgnoreCase);

    static void Conflict(string path, string reason)
    {
        Conflicts.Add($"{path}\t{reason}");
        Console.WriteLine($"  SKIP  {path}  ({reason})");
        int i = path.LastIndexOf('/');
        if (i > 0 && IsIndex(path[(i + 1)..], out int n))
        {
            if (!RefusedSlots.TryGetValue(path[..i], out var s)) RefusedSlots[path[..i]] = s = new SortedSet<int>();
            s.Add(n);
        }
    }

    static void WriteConflicts(string outFile, string header)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outFile))!);
        var lines = new List<string>
        {
            "# " + header,
            "# Every row is a v84 node this merge REFUSED to write. Two reasons appear here:",
            "#   * 'already exists in target' — the additive-only gate, enforced in the write path.",
            "#     A v84 EDIT to an existing node (renamed mob, portal added to an existing map)",
            "#     looks exactly like this and is silently lost unless someone decides otherwise.",
            "#   * 'DENIED by deny-list' — a v84 ADDITION the target lacks and must keep lacking.",
            "#     The gate is structurally blind to these; only the deny-list catches them.",
            "# Additive-only is enforced in the write path, so this file is the exhaustive list of",
            "# v84 changes that were dropped. Read it before shipping.",
            "# Columns: path, reason.",
            "",
        };
        lines.AddRange(Conflicts);
        File.WriteAllLines(outFile, lines);
        Console.WriteLine($"conflicts: {Conflicts.Count} -> {outFile}");
    }

    // ---------- dump ----------

    static int Dump(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        int depth = args.Length > 3 ? int.Parse(args[3]) : 3;
        var (file, ver) = Open(args[1]);
        using var _ = file;
        Console.WriteLine($"{args[1]}  iv={ver}  patchVersion={file.Version}");

        var segs = Segs(args[2], Path.GetFileName(args[1]));
        var obj = Resolve(file, segs, segs.Length);
        if (obj == null) { Console.WriteLine("NOT FOUND: " + args[2]); return 1; }
        Print(obj, "", depth);
        return 0;
    }

    static void Print(WzObject obj, string ind, int depth)
    {
        // canvases print their pixel payload size: an icon that survived a merge with
        // 0 compressed bytes is a broken sprite, and nothing else in the pipeline notices.
        string extra = obj switch
        {
            MapleLib.WzLib.WzProperties.WzCanvasProperty c =>
                $" {c.PngProperty?.Width}x{c.PngProperty?.Height}, png {(c.PngProperty?.GetCompressedBytes(false)?.Length ?? 0)} bytes",
            WzImageProperty p0 when (p0.WzProperties?.Count ?? 0) == 0 => " = " + p0.ToString(),
            _ => ""
        };
        Console.WriteLine($"{ind}{obj.Name} [{obj.GetType().Name}]{extra}");
        if (depth <= 0) return;
        foreach (var k in Kids(obj)) Print(k, ind + "  ", depth - 1);
    }

    static IEnumerable<WzObject> Kids(WzObject obj) => obj switch
    {
        WzDirectory d => d.WzDirectories.Cast<WzObject>().Concat(d.WzImages),
        WzImage i => i.WzProperties,
        WzImageProperty p => (IEnumerable<WzObject>?)p.WzProperties ?? Array.Empty<WzObject>(),
        _ => Array.Empty<WzObject>()
    };

    // ---------- B5: the positional-array gate ----------
    //
    // 03c's rule, made executable: "any manifest row that is one slot of a positional array is
    // unsafe to merge piecemeal — the tell is a parent whose children are consecutive integers."
    // The additive-only gate is structurally blind to this and cannot be taught to see it: a row
    // like `220011000.img/portal/4/script` names a leaf the target does NOT have, under a parent
    // it DOES have, so the gate is satisfied and the write lands on whatever portal happens to
    // occupy index 4 in THIS tree. v84 reindexed that array; index 4 is `scr00` there and the
    // working portal into Ludibrium Toy Factory here. Ten of the twelve rows ticket 08 refused by
    // hand are that shape, and nothing in the pipeline said a word about any of them.

    // A container is a positional array iff EVERY child name is a non-negative integer and they
    // form ONE CONSECUTIVE RUN. "Every" is load-bearing: a map .img has children `0`-`7` (the
    // layers) ALONGSIDE info/portal/foothold/life, and calling that an array would refuse every
    // row that writes into a layer — including the six appends ticket 08 correctly merged.
    //
    // 03i: the run NO LONGER HAS TO START AT 0, and that clause is the whole ticket. The first
    // version demanded exactly 0..c-1, so `Glove/01082262.img/swingT2` (live children `{1,2}`)
    // and `swingO3` (live `{1}`) were not arrays at all and two v84 frames were spliced into
    // Ezorsia's art — the exact hazard the six refusals on their siblings exist to stop.
    //
    // The run must still be CONSECUTIVE, and that is the clause 03h declined to drop and this
    // ticket declines again. Allowing holes would make every id container in String.wz an array
    // (`Consume.img`'s children are 2,290 integer item ids with enormous gaps) and refuse 501
    // legitimate name rows. The cost is a known, stated blind spot: a genuinely sparse array
    // like `Glove/01082262.img/swingOF` = `{0,3}`, or `Check.img/4940` = `{0,1,4961}`, reads as
    // "not an array". Nothing structural can separate those from an id table; the deny-list is
    // what stands in front of them. See WZ-MERGE-PROCEDURE.md 4.4.
    static (int Min, int Count)? ArrayRange(IEnumerable<string> childNames)
    {
        var seen = new HashSet<int>();
        int min = int.MaxValue, max = int.MinValue;
        foreach (var n in childNames)
        {
            if (!IsIndex(n, out int v) || !seen.Add(v)) return null;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (seen.Count == 0) return null;              // an EMPTY container is not an array:
                                                       // `01082262.img/ladder` has no children and
                                                       // v84's two frames are a legitimate fill.
        if (max - min + 1 != seen.Count) return null;   // a run with holes — see above
        return (min, seen.Count);
    }

    // "4" yes; "04", "+4", "-4", "4 " no. A leading zero is a different NAME than the integer it
    // parses to, and WZ lookup is by name — treating `04` as slot 4 would misjudge the array.
    static bool IsIndex(string s, out int v) =>
        int.TryParse(s, out v) && v >= 0 && s == v.ToString();

    // Digest of a node's CONTENT with its own name excluded, so slot 15 of one array can be
    // compared with slot 14 of another. Canon() already keys every descendant by its path
    // relative to the prefix it is handed.
    static string SlotDigest(WzObject o)
    {
        var sb = new StringBuilder();
        Canon(o, "", sb);
        return Sha(Encoding.UTF8.GetBytes(sb.ToString()));
    }

    // What the target's array looked like BEFORE this run touched it. Memoised per container:
    // the whole check has to be answered against the baseline, or the answer depends on the
    // order the manifest happens to list its slots in (add-list rows are sorted as TEXT, so
    // `back/10` arrives before `back/9`).
    sealed class ArrayBase
    {
        public int Min;                 // 03i: arrays do not all start at 0
        public int Count;
        public int Max => Min + Count - 1;
        public HashSet<string> Digests = new();
    }

    // Refusal reason, or null. `requested` is every slot index this run asks for, per container,
    // so a manifest that appends 9..28 in text order is judged on the set, not on the row.
    static string? PositionalRefusal(WzFile tgt, string wzName, string[] rel, WzObject srcObj,
        Dictionary<string, ArrayBase?> baseline, Dictionary<string, SortedSet<int>> requested)
    {
        for (int i = 0; i < rel.Length; i++)
        {
            if (!IsIndex(rel[i], out int idx)) continue;
            string container = string.Join('/', new[] { wzName }.Concat(rel.Take(i)));
            if (!baseline.TryGetValue(container, out var b))
            {
                // ponytail: memoised from the TARGET as it was before this run, so a manifest
                // holding both `obj/25` and `obj/25/foo` would refuse the second even though this
                // run supplies slot 25 itself. No composed row is an ancestor of another (checked,
                // all eleven files), so it cannot fire today; fix it by folding `requested` into
                // the baseline if a manifest ever needs both.
                var node = Resolve(tgt, rel, i);
                var r = node == null ? null : ArrayRange(Kids(node).Select(k => k.Name));
                b = r == null ? null : new ArrayBase
                {
                    Min = r.Value.Min,
                    Count = r.Value.Count,
                    Digests = Kids(node!).Select(SlotDigest).ToHashSet()
                };
                baseline[container] = b;   // a null is memoised too: "not an array, stop asking"
            }
            if (b == null) continue;

            string tell = $"'{container}' holds exactly the consecutive integers {b.Min}..{b.Max}, so its children are SLOTS OF A POSITIONAL ARRAY, not identities";

            // 03h: the wording matters as much as the refusal. This branch covers TWO different
            // hazards and the first draft named only one, which is a refusal an operator can
            // disprove and then override. Ticket 09's 108 `Check.img/<id>/0/lvmax` rows land
            // here and the indices line up perfectly — slot 0 is the start block in both trees —
            // so "the index may name a different entry" reads as false and the row looks safe.
            // It is not: the write ADDS a field to a record that already works.
            if (i != rel.Length - 1)
                return $"POSITIONAL ARRAY: {tell}. This row writes '{string.Join('/', rel.Skip(i + 1))}' INTO slot {idx}, which already EXISTS. TWO hazards, and this refusal covers both — checking one and finding it harmless does not clear the row: (a) the source's slot {idx} need not be the same ENTRY as this tree's, so the field lands on whichever entry sits at that index HERE (v84 reindexes arrays, and the two trees need not even hold the same NUMBER of entries); (b) even when it is the same entry, the row EDITS a record the target already has by adding a field to it, and the additive gate cannot see an edit that adds. What makes (b) different from adding a field to any other existing node — which this same run does permit — is that the record here has NO NAME, only a position, so there is nothing to check the edit against: you cannot tell WHICH record you are editing without dumping it. Do that: dump BOTH slots in full, decide what the added field does to the record that is already there, and either re-author the row against THIS tree or deny it. (Worked example of (b) landing with the indices lining up perfectly: ticket 09's `Quest.wz/Check.img/<id>/0/lvmax`, which caps 108 working quests at Lv.40.)";

            if (idx >= b.Min && idx <= b.Max)
                return $"POSITIONAL ARRAY: {tell}. Slot {idx} is already occupied; this is not an append.";

            // 03i: an array that starts at 1 makes index 0 reachable, and it is not an append —
            // the source is numbering the same animation from a different origin, which means the
            // two arrays are not aligned at all. `Glove/01082262.img/swingO3` is exactly this:
            // live `{1}`, v84 `{0,1}`, and v84's slot 0 carries a 47x9 rGlove against the live 6x5.
            if (idx < b.Min)
                return $"POSITIONAL ARRAY: {tell}. Slot {idx} sits BELOW the array's first index {b.Min}, so this is a PREPEND, not an append. A source that numbers this container from a different origin than the target is not aligned with it, and the entry would sit beside slots that belong to a different set. Dump both containers by content and either re-author the row or deny it.";

            // T23: the gap check is against the RUNNING state, not the manifest's wish-list.
            // `requested` says slot k is ASKED FOR; it does not say slot k LANDS. UI.wz
            // MapLogin.img/back was `0..47`, the manifest asked for 48..54, the duplicate-content
            // rule below correctly refused 48..52 — and 53/54 then read as clean appends because
            // 53 >= 48 against the BASELINE count and 48..52 were "in the manifest". The output
            // was `{0..47,53,54}`, a five-index hole, and the client died before the login screen.
            // An index is only an append if every index between the array's end and it actually
            // lands, so a refused slot poisons every later slot of the same container.
            var want = requested.TryGetValue(container, out var w) ? w : new SortedSet<int>();
            RefusedSlots.TryGetValue(container, out var gone);
            for (int k = b.Max + 1; k < idx; k++)
            {
                if (!want.Contains(k))
                    return $"POSITIONAL ARRAY: {tell}. Slot {idx} would leave a GAP — the array ends at {b.Max} and nothing in this manifest supplies slot {k}. A client that walks the array stops at the hole.";
                if (gone != null && gone.Contains(k))
                    return $"POSITIONAL ARRAY: {tell}. Slot {idx} would leave a GAP — the array ends at {b.Max}, this manifest DOES list slot {k}, and slot {k} was REFUSED earlier in this run (read conflicts.txt for its reason). A partial fill is worse than no fill: the array would run {b.Min}..{k - 1} and then jump, and a client that walks it stops at the hole. Every slot from {k} up has to land, or none of them may.";
            }

            // A pure append by index can still be a duplicate by content: if v84 INSERTED an
            // entry earlier in the array, every later slot is the target's own content shifted
            // one place, and the last one lands past the end and looks like new material.
            // `220000300.img/portal/15` is exactly that — byte-identical to the target's
            // portal/14 (`in06` -> 220000307), because v84 inserted `scr00` at index 4.
            if (b.Digests.Contains(SlotDigest(srcObj)))
                return $"POSITIONAL ARRAY: {tell}. Index {idx} IS a pure append onto {b.Count} entries, but the source entry is content-identical to one the array already holds — the two arrays have diverged (the source inserted earlier and every later slot is shifted), so this appends a DUPLICATE rather than new content.";
        }
        return null;
    }

    // row "…/portal/15" -> requested["…/portal"] += 15. Rows whose last segment is not an index
    // contribute nothing; a container that is not an array is never looked up.
    static Dictionary<string, SortedSet<int>> RequestedSlots(IEnumerable<string> paths)
    {
        var d = new Dictionary<string, SortedSet<int>>(StringComparer.OrdinalIgnoreCase);
        foreach (var p in paths)
        {
            int i = p.LastIndexOf('/');
            if (i <= 0 || !IsIndex(p[(i + 1)..], out int n)) continue;
            if (!d.TryGetValue(p[..i], out var s)) d[p[..i]] = s = new SortedSet<int>();
            s.Add(n);
        }
        return d;
    }

    // ---------- verify (B1: the tool checks its own output) ----------

    // Re-OPEN a written .wz from disk and re-RESOLVE every manifest path in it. This is the
    // only thing that distinguishes "SaveToDisk returned" from "a file a client can read":
    // SaveToDisk truncates the destination up front (WzFile.cs:675) and then streams images
    // for minutes, so OOM, a full disk or Ctrl-C leaves a plausible-looking .wz that parses
    // partway and then stops. Before this existed, nothing in the pipeline ever re-parsed the
    // tool's own output — the first reader was the user's game client.
    // M2: `digests` is the content check. Key = manifest path of an image the merge inserted
    // into; value = the SHA-256 of that image's canonical decoded form taken from the IN-MEMORY
    // merged tree, immediately before SaveToDisk. Re-taking it from the file on disk and
    // comparing is the only thing in this pipeline that would notice a corrupted 4 KB canvas
    // payload — path re-resolution and ParseImage both pass straight over one. The machinery
    // (`Canon`/`Sha`) already existed for the `hash` subcommand and was never wired into a merge.
    // `baselineWzPath` is the merge TARGET — the pre-merge file the output was built from. It is
    // used for one thing: an image MapleLib cannot parse in the target cannot have been broken by
    // this merge. Without it, Sound.wz/BgmGL.img (unreadable in all three trees, procedure §11)
    // made EVERY Sound.wz merge fail verification, stay .partial and exit 4 no matter how correct
    // the data was — ticket 06 hit exactly that and discarded a good file. The discount is
    // per-image and one-directional on purpose: an image that parses in the target and fails in
    // the output is the corruption this check exists to catch, and still fails.
    static bool VerifyFile(string wzPath, string wzName, IReadOnlyList<string> expect,
                           IReadOnlyDictionary<string, string>? digests = null,
                           string? baselineWzPath = null)
    {
        WzFile f; WzMapleVersion ver;
        // A badly truncated file fails at the header, before any image — that is still a
        // verification failure, not a tool crash, so it must exit 4 like every other one.
        try { (f, ver) = Open(wzPath); }
        catch (Exception ex) { Console.Error.WriteLine($"  UNREADABLE: {ex.Message}"); return false; }
        using var _ = f;
        Console.WriteLine($"verify {wzPath}  iv={ver} patchVersion={f.Version}  re-resolving {expect.Count} paths");
        int missing = 0;
        foreach (var p in expect)
        {
            var rel = Rel(p, wzName);
            if (Resolve(f, rel, rel.Length) == null) { Console.Error.WriteLine($"  MISSING in output: {p}"); missing++; }
        }
        int drift = 0;
        foreach (var (imgPath, want) in digests ?? new Dictionary<string, string>())
        {
            var rel = Rel(imgPath, wzName);
            var img = Resolve(f, rel, rel.Length);
            if (img == null) { Console.Error.WriteLine($"  CONTENT: {imgPath} absent from output"); drift++; continue; }
            string got = Digest(img);
            if (got == want) Console.WriteLine($"  content OK  {imgPath}  {got[..16]}…");
            else { Console.Error.WriteLine($"  CONTENT DRIFT in {imgPath}: expected {want}, on disk {got}"); drift++; }
        }
        // Force EVERY image to parse, then immediately unparse it. A truncated tail is
        // invisible to a path lookup that never reaches it; ParseImage throws or returns
        // false on a short/garbled block, which is exactly the failure a half-written .wz has.
        // Unparsing keeps this bounded on Map.wz (629 MB) instead of materialising the file.
        int imgs = 0;
        // Keyed on the path RELATIVE to the .wz root, never FullPath: the output is opened as
        // "<Name>.wz.partial", so its FullPath root differs from the target's and nothing would
        // ever match the baseline.
        var badMsg = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
        void Walk(WzDirectory d, string prefix)
        {
            foreach (var img in d.WzImages)
            {
                imgs++;
                try
                {
                    if (!img.Parsed && !img.ParseImage()) throw new Exception("ParseImage returned false");
                    img.UnparseImage();
                }
                catch (Exception ex) { badMsg[prefix + img.Name] = $"{ex.GetType().Name} {ex.Message}"; }
            }
            foreach (var sub in d.WzDirectories) Walk(sub, prefix + sub.Name + "/");
        }
        Walk(f.WzDirectory, "");

        var alreadyBad = badMsg.Count > 0 && baselineWzPath != null
            ? UnparseableIn(baselineWzPath, badMsg.Keys)
            : new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        int bad = 0, preExisting = 0;
        foreach (var (p, msg) in badMsg)
        {
            if (alreadyBad.Contains(p))
            {
                preExisting++;
                Console.WriteLine($"  pre-existing UNPARSEABLE {p}: {msg} — unreadable in the merge target too, so NOT damage from this merge; discounted");
            }
            else { Console.Error.WriteLine($"  UNPARSEABLE image {p}: {msg}"); bad++; }
        }
        Console.WriteLine($"verify: {imgs} images parsed, {bad} unparseable" +
                          (preExisting > 0 ? $" ({preExisting} pre-existing, discounted)" : "") +
                          $", {missing} requested paths missing, " +
                          $"{(digests?.Count ?? 0)} images content-checked, {drift} drifted");
        return missing == 0 && bad == 0 && drift == 0;
    }

    // Which of `candidates` (paths relative to the .wz root) are ALSO unparseable in `wzPath`?
    // Called only when the output already has at least one bad image, and it parses only the
    // candidates, so a clean merge pays nothing for this.
    static HashSet<string> UnparseableIn(string wzPath, IEnumerable<string> candidates)
    {
        var want = new HashSet<string>(candidates, StringComparer.OrdinalIgnoreCase);
        var found = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        WzFile bf;
        // A baseline we cannot open discounts nothing — the output's failures stand. Failing
        // open is the safe direction here; the alternative is suppressing a real corruption
        // because the check itself broke.
        try { (bf, var _ver) = Open(wzPath); }
        catch (Exception ex) { Console.Error.WriteLine($"  (baseline {wzPath} would not open, so nothing is discounted: {ex.Message})"); return found; }
        using var _bf = bf;
        void Walk(WzDirectory d, string prefix)
        {
            foreach (var img in d.WzImages)
            {
                string p = prefix + img.Name;
                if (!want.Contains(p)) continue;
                try
                {
                    if (!img.Parsed && !img.ParseImage()) throw new Exception("ParseImage returned false");
                    img.UnparseImage();
                }
                catch { found.Add(p); }
            }
            foreach (var sub in d.WzDirectories) Walk(sub, prefix + sub.Name + "/");
        }
        Walk(bf.WzDirectory, "");
        return found;
    }

    static int VerifyCmd(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        var expect = ReadPaths(args[2]);
        // The manifest declares its own "<Name>.wz" root, so a renamed or .partial copy of
        // the output still verifies. Falls back to the filename for an empty manifest.
        string wzName = expect.Count > 0 ? expect[0].Split('/')[0] : Path.GetFileName(args[1]);
        // Optional --baseline <targetWz>: same discount the merge applies (see VerifyFile).
        // Without it nothing is discounted, which is what a bare `verify` always did.
        string? baseline = null;
        for (int i = 3; i < args.Length - 1; i++)
            if (args[i] == "--baseline") baseline = Path.GetFullPath(args[i + 1]);
        // A typo'd or value-less flag would otherwise just not discount anything, which looks
        // identical to a genuine failure. Say so rather than let someone debug the wrong thing.
        if (args.Length > 3 && baseline == null)
            Console.Error.WriteLine($"  note: trailing arguments ignored ({string.Join(' ', args[3..])}); only --baseline <targetWz> is recognised here");
        return VerifyFile(Path.GetFullPath(args[1]), wzName, expect, null, baseline) ? 0 : 4;
    }

    // ---------- hash (B3: content check for the one image that is re-serialized) ----------

    // BlockSize is a length, and for every image the merge did NOT touch it is not even a
    // measurement — MapleLib memcpy's those straight out of the source file and carries the
    // recorded size across (WzDirectory.cs:353-357), so pre/post BlockSize compares a number
    // to a copy of itself. The image that WAS re-serialized (Changed=true) is the only one
    // where the serializer could have got something wrong, and it is the one BlockSize says
    // least about. So: digest the DECODED values of each direct child, pre and post, and diff.
    static string Sha(ReadOnlySpan<byte> b) => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(b)).ToLowerInvariant();

    // 03i: `WzMerge hash <Reactor.wz> /` exited 0xC00000FD — a stack overflow — on six images,
    // symmetric pre and post, so `hash` (this project's protect-verification instrument) failed on
    // a whole file in a way an operator reads as merge damage. The cause is not depth, it is a
    // CYCLE: `Reactor.wz/1050000.img/0/hit/2` is a WzUOLProperty pointing back at its own ancestor
    // `0`, and `Kids()` on a UOL hands back the RESOLVED TARGET's children, so Canon walked
    // 0 -> hit/2 -> 0 -> hit/2 forever, branching twice per level.
    //
    // The fix is to stop following the symlink, not to bound the walk: a bound would still expand
    // 2^depth lines before it stopped. A UOL's content IS its link string — the node it points at
    // is digested at its own path in the same pass, so nothing is lost and nothing is counted
    // twice. The depth cap below is a backstop for a cycle of some other shape, not the fix; it
    // writes its own marker line INTO the digest so a truncated subtree can never read as a
    // matching one.
    const int CanonMaxDepth = 64;

    static void Canon(WzObject o, string prefix, StringBuilder sb, int depth = 0)
    {
        // ponytail: leaf value via ToString(), which is the decoded scalar for every property
        // type these manifests carry (int/short/long/float/double/string/uol/vector). Canvases
        // hash their compressed pixel bytes instead — that is the payload a broken merge loses.
        string val = o switch
        {
            MapleLib.WzLib.WzProperties.WzCanvasProperty c =>
                $"canvas {c.PngProperty?.Width}x{c.PngProperty?.Height} png:{Sha(c.PngProperty?.GetCompressedBytes(false) ?? Array.Empty<byte>())}",
            MapleLib.WzLib.WzProperties.WzUOLProperty u => $"UOL -> {u.Value}",
            WzImageProperty p when (p.WzProperties?.Count ?? 0) == 0 => $"{p.PropertyType} = {p}",
            _ => o.GetType().Name
        };
        sb.Append(prefix).Append('\t').Append(val).Append('\n');
        if (o is MapleLib.WzLib.WzProperties.WzUOLProperty) return;   // a link, not a subtree
        if (depth >= CanonMaxDepth)
        {
            sb.Append(prefix).Append("\tDEPTH LIMIT ").Append(CanonMaxDepth).Append(" — subtree NOT digested\n");
            return;
        }
        foreach (var k in Kids(o).OrderBy(k => k.Name, StringComparer.Ordinal))
            Canon(k, prefix + "/" + k.Name, sb, depth + 1);
    }

    // whole-subtree digest of one node. Used by `hash` per direct child, and by the merge (M2)
    // per inserted-into image, pre-save vs post-save.
    static string Digest(WzObject o)
    {
        var sb = new StringBuilder();
        Canon(o, o.Name, sb);
        return Sha(Encoding.UTF8.GetBytes(sb.ToString()));
    }

    static int Hash(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        var (file, ver) = Open(args[1]);
        using var _ = file;
        var segs = Segs(args[2], Path.GetFileName(args[1]));
        var obj = Resolve(file, segs, segs.Length);
        if (obj == null) { Console.Error.WriteLine("NOT FOUND: " + args[2]); return 1; }
        // one line per DIRECT CHILD so a diff of two of these files names exactly which
        // children changed; sorted, so insertion order is not mistaken for a difference.
        var all = new StringBuilder();
        foreach (var k in Kids(obj).OrderBy(k => k.Name, StringComparer.Ordinal))
        {
            var sb = new StringBuilder();
            Canon(k, k.Name, sb);
            all.Append(sb);
            Console.WriteLine($"{Sha(Encoding.UTF8.GetBytes(sb.ToString()))}  {k.Name}");
        }
        Console.WriteLine($"{Sha(Encoding.UTF8.GetBytes(all.ToString()))}  TOTAL {args[2]} ({args[1]} iv={ver} patchVersion={file.Version})");
        return 0;
    }

    // ---------- deps (B4: what a map image references outside itself) ----------

    // A map .img names everything it needs by NAME, and none of those names appear anywhere in
    // the manifest ordering rule:
    //   back/<n>/bS                      -> Map.wz/Back/<bS>.img
    //   <layer>/obj/<n>/{oS,l0,l1,l2}    -> Map.wz/Obj/<oS>.img/<l0>/<l1>/<l2>
    //   <layer>/info/tS                  -> Map.wz/Tile/<tS>.img
    //   info/bgm   "Bgm14/DragonRider"   -> Sound.wz/Bgm14.img/DragonRider
    //   info/mapMark "Leafre"            -> Map.wz/MapHelper.img/mark/Leafre
    //   info/link  "240080100"           -> another whole map image, with references of its own
    // Merge a map without these and the client renders it broken, silently drops its background,
    // shows a blank world-map marker or plays no music.
    //
    // B3 — GRANULARITY IS THE POINT. This used to print references at WHOLE-IMAGE granularity
    // ("Map.wz/Back/dragonRoad.img"). That image exists in v83, so the merge gate refused the row
    // and the reader concluded "nothing owed" — while what v84 actually adds lives INSIDE it
    // (add-list/Map.txt:6-15, ani/20..24 and back/42..46), and eight of the nine back-bearing
    // Crimson Sky maps draw those frames. Same defect for Tile (grassySoil.img/edD/1) and for Obj
    // at l1/l2 depth. So every reference is now resolved against the ADD-LIST: what gets printed
    // is the set of manifest rows that actually carry the new content, at whatever depth the
    // manifest holds it, and a reference with no add-list rows under it is printed as a comment
    // saying "already in v83, nothing owed" instead of as a row the merge will refuse.
    // Resolution is per-reference and therefore slightly over-inclusive: referencing ONE new
    // frame of Back/dragonRoad.img pulls all ten of that image's new rows. Additive and cheap;
    // narrowing it to the exact frame numbers would trade that for a chance of missing one.
    //
    // NOT resolved, deliberately, and the cut is unchanged: mob / npc / reactor ids. State it
    // honestly rather than leaving the banner reading as an all-clear — a v84-only mob id placed
    // in a v84 map means the LIVE CLIENT HAS NO SPRITE for it.
    static int Deps(string[] args)
    {
        if (args.Length < 4) { Usage(); return 2; }
        var (file, ver) = Open(args[1]);
        using var _ = file;
        string wzName = Path.GetFileName(args[1]);

        // B4: the procedure hardcoded "Map/Map2/<id>.img" and ticket 07's maps are Map6.
        // Substituting the id but not the bucket printed NOT FOUND and exited 1 — after the
        // shell had already truncated the redirect target, so the next command merged an empty
        // manifest and exited 0, "added 0, refused 0". Take a bare id and find its bucket.
        var segs = Segs(args[2], wzName);
        if (segs.Length == 1 && !segs[0].EndsWith(".img", StringComparison.OrdinalIgnoreCase))
        {
            string? bucket = FindMap(file, segs[0]);
            if (bucket == null)
            {
                Console.Error.WriteLine($"NOT FOUND: no {segs[0]}.img under Map/* in {args[1]}. " +
                    "Nothing was written; if you redirected this command, its target is now empty and the merge that reads it will refuse.");
                return 1;
            }
            Console.Error.WriteLine($"# id {segs[0]} resolved to {bucket}");
            segs = bucket.Split('/');
        }
        if (Resolve(file, segs, segs.Length) == null)
        {
            Console.Error.WriteLine("NOT FOUND: " + args[2]);
            return 1;
        }

        var refs = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
        var unresolved = new List<string>();
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        void WalkMap(string[] mapSegs)
        {
            string key = string.Join('/', mapSegs);
            if (!seen.Add(key)) return;                       // link cycles are real; visit once
            var img = Resolve(file, mapSegs, mapSegs.Length);
            if (img == null) { unresolved.Add($"map image {key} does not exist in {wzName}"); return; }
            refs.Add($"{wzName}/{key}");                       // the map itself is a dependency too

            void Walk(WzObject o)
            {
                string? C(string n) => Kids(o).FirstOrDefault(
                    k => string.Equals(k.Name, n, StringComparison.OrdinalIgnoreCase))?.ToString();
                if (C("bS") is string bs && bs.Length > 0) refs.Add($"{wzName}/Back/{bs}.img");
                if (C("tS") is string ts && ts.Length > 0) refs.Add($"{wzName}/Tile/{ts}.img");
                if (C("oS") is string os && os.Length > 0 && C("l0") is string l0 && l0.Length > 0)
                {
                    string p = $"{wzName}/Obj/{os}.img/{l0}";
                    if (C("l1") is string l1 && l1.Length > 0)
                    {
                        p += "/" + l1;
                        if (C("l2") is string l2 && l2.Length > 0) p += "/" + l2;
                    }
                    refs.Add(p);
                }
                foreach (var k in Kids(o)) Walk(k);
            }
            Walk(img);

            var info = Kids(img).FirstOrDefault(k => string.Equals(k.Name, "info", StringComparison.OrdinalIgnoreCase));
            string? I(string n) => info == null ? null : Kids(info).FirstOrDefault(
                k => string.Equals(k.Name, n, StringComparison.OrdinalIgnoreCase))?.ToString();
            // "Bgm14/DragonRider" is <img>/<track>, and Sound.wz is a different file entirely.
            if (I("bgm") is string bgm && bgm.Contains('/'))
            {
                var p = bgm.Split('/', 2);
                refs.Add($"Sound.wz/{p[0]}.img/{p[1]}");
            }
            if (I("mapMark") is string mark && mark.Length > 0) refs.Add($"{wzName}/MapHelper.img/mark/{mark}");
            // A link stub has essentially nothing of its own: 8 of ticket 06's 21 maps are pure
            // stubs whose entire layout lives in the link target. Without this, deps printed
            // "references 0 scenery sets" under a banner that reads as an all-clear.
            if (I("link") is string link && link.Length > 0)
            {
                string? target = FindMap(file, link);
                if (target == null) unresolved.Add($"info/link -> {link}, which has no .img under Map/* in {wzName}");
                else WalkMap(target.Split('/'));
            }
        }
        WalkMap(segs);

        var lists = new Dictionary<string, List<string>>(StringComparer.OrdinalIgnoreCase);
        var owed = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
        var nothingOwed = new List<string>();
        foreach (var r in refs)
        {
            string wz = r.Split('/')[0];
            if (!lists.TryGetValue(wz, out var rows)) lists[wz] = rows = AddList(args[3], wz);
            // a manifest row that IS this reference, sits under it, or is a copy root ABOVE it
            var hits = rows.Where(x => Under(x, r) || Under(r, x)).ToList();
            if (hits.Count == 0) nothingOwed.Add(r);
            else foreach (var h in hits) owed.Add(h);
        }

        Console.WriteLine($"# deps {string.Join('/', segs)}  ({args[1]} iv={ver} patchVersion={file.Version}; add-list {args[3]})");
        Console.WriteLine($"# {seen.Count} map image(s) walked, {refs.Count} references, {owed.Count} add-list rows owed, {nothingOwed.Count} references already in v83.");
        Console.WriteLine("# This IS the paths file for these maps — it already includes the map image row(s) themselves.");
        Console.WriteLine("# Rows are grouped by .wz file; a merge of one file refuses rows rooted at another and says so.");
        Console.WriteLine("# NOT resolved here: mob / npc / reactor ids. A v84-only mob id in one of these maps means the");
        Console.WriteLine("# live client has NO SPRITE for it — check the ids under life/ against add-list/{Mob,Npc,Reactor}.txt.");
        foreach (var n in nothingOwed) Console.WriteLine($"# already in v83, nothing owed: {n}");
        foreach (var u in unresolved) Console.WriteLine($"# UNRESOLVED: {u}");
        // On an unresolved reference the rows are printed COMMENTED OUT. This output is normally
        // consumed through `> file`, and an incomplete-but-non-empty manifest would sail straight
        // past the 0-row guard that exists to stop exactly this class of accident: better that the
        // redirect target contain no manifest rows at all, so the merge that reads it exits 2.
        string prefix = unresolved.Count > 0 ? "# INCOMPLETE, do not merge: " : "";
        string group = "";
        foreach (var row in owed)
        {
            string wz = row.Split('/')[0];
            if (wz != group) { group = wz; Console.WriteLine($"# ==== {wz} ===="); }
            Console.WriteLine(prefix + row);
        }
        if (unresolved.Count > 0)
            Console.Error.WriteLine($"{unresolved.Count} reference(s) could not be resolved. This deps file is INCOMPLETE and every row in it is commented out; fix the cause and re-run.");
        return unresolved.Count > 0 ? 1 : 0;
    }

    // which Map/MapN holds <id>.img. Scanning beats "the bucket is the first digit": it is the
    // same answer when that rule holds and a loud null when it does not.
    static string? FindMap(WzFile file, string id)
    {
        if (file.WzDirectory?["Map"] is not WzDirectory maps) return null;
        foreach (var d in maps.WzDirectories)
            if (d.WzImages.Any(i => string.Equals(i.Name, id + ".img", StringComparison.OrdinalIgnoreCase)))
                return $"Map/{d.Name}/{id}.img";
        return null;
    }

    // Deliberately not ReadPaths: a 0-row add-list is legitimate input here
    // (Base.txt and TamingMob.txt really do add nothing), it is only a fatal *manifest*.
    static List<string> AddList(string dir, string wzName)
    {
        string f = Path.Combine(dir, Path.GetFileNameWithoutExtension(wzName) + ".txt");
        if (!File.Exists(f)) throw new BadArgs($"deps needs an add-list for {wzName} and {f} does not exist");
        return File.ReadAllLines(f).Select(l => l.Trim())
                   .Where(l => l.Length > 0 && !l.StartsWith('#')).ToList();
    }

    // ---------- merge (client binary .wz) ----------

    static bool SamePath(string? a, string? b) =>
        a != null && b != null &&
        string.Equals(Path.TrimEndingDirectorySeparator(Path.GetFullPath(a)),
                      Path.TrimEndingDirectorySeparator(Path.GetFullPath(b)),
                      StringComparison.OrdinalIgnoreCase);

    static int Refuse(string why)
    {
        Console.Error.WriteLine("REFUSED: " + why);
        return 2;
    }

    // Dropped into a staging directory the first time WzMerge writes into it. Its only job is to
    // let the guard below tell "a staging directory that already holds this ticket's other merged
    // .wz files" apart from "a directory full of somebody else's .wz files".
    const string StageMarker = ".wz-merge-stage";

    // ===================== B2: WHERE THE OUTPUT MAY GO, ABSOLUTELY =====================
    // MapleLib's SaveToDisk is NOT atomic and NOT copy-on-write. It File.Create()s the
    // destination — truncating it instantly (WzFile.cs:675) — and only then spends the next
    // several minutes streaming unchanged images out of the TARGET's own open reader
    // (WzDirectory.cs:353-357). Map.wz is 629 MB. Its scratch file is CWD-relative
    // (GetFileNameWithoutExtension(path) + ".TEMP", WzFile.cs:664).
    //
    // The three guards this replaces were all RELATIONAL to <targetWz>: out is not the target,
    // not the source, not in the target's directory. But the procedure's own real merge sets the
    // target to <stage>\<T>\pre\<Name>.wz, so an <outWz> of D:\games\MapleStory\Map.wz is none of
    // those three things — ALL THREE PASSED, File.Move promoted the finished merge straight onto
    // the live client, and the pinned CWD dropped a several-hundred-MB .TEMP in the client folder.
    // A guarantee that depends on what <target> happens to be is not a guarantee.
    //
    // So this is a property of the OUTPUT DIRECTORY alone, and it does not care about the other
    // arguments at all:
    //   * a directory containing an executable is a game install, not a staging directory.
    //   * a directory already holding OTHER .wz files is refused unless WzMerge itself marked it
    //     as staging. The live client holds 18; a fresh per-ticket staging directory holds none,
    //     and once WzMerge has written one it carries the marker, so a multi-file ticket works.
    // Ask it anything, in advance, without writing: WzMerge guard <outWz>.
    static string? OutDirRefusal(string outPath)
    {
        string full = Path.GetFullPath(outPath);
        string dir = Path.GetDirectoryName(full)!;

        // The .exe test is applied to the nearest EXISTING directory on the way up, not only to
        // <dir>. Otherwise `<client>\brandnew\Map.wz` slips through — the directory does not exist
        // yet, so there is nothing to inspect — and the marker written afterwards would whitelist
        // it permanently. Only the nearest existing ancestor, never the whole chain: staging lives
        // at D:\games\MapleStory\Server\wz-merge\, and D:\games\MapleStory IS a game install, so a
        // full ancestor walk would refuse the one layout this document prescribes.
        // ponytail: heuristic with a named ceiling — an existing, .exe-free subdirectory of a game
        // install still passes. Not worth more machinery; the marker rule below catches the shape
        // that actually matters (a directory full of somebody else's .wz).
        string probe = dir;
        while (!Directory.Exists(probe))
        {
            string? up = Path.GetDirectoryName(probe);
            if (up == null || up == probe) return null;
            probe = up;
        }
        var exes = Directory.GetFiles(probe, "*.exe");
        if (exes.Length > 0)
            return $"{probe} holds {exes.Length} executable(s) (e.g. {Path.GetFileName(exes[0])}). That is a game " +
                   "install, not a staging directory, and WzMerge never writes into one. Stage under " +
                   @"D:\games\MapleStory\Server\wz-merge\<ticket>\ and copy into place by hand (procedure 5.7).";
        if (!Directory.Exists(dir)) return null;              // a staging directory yet to be made
        var foreign = Directory.GetFiles(dir, "*.wz").Where(f => !SamePath(f, full)).ToArray();
        if (foreign.Length > 0 && !File.Exists(Path.Combine(dir, StageMarker)))
            return $"{dir} already holds {foreign.Length} .wz file(s) that WzMerge did not put there " +
                   $"(e.g. {Path.GetFileName(foreign[0])}) and carries no {StageMarker}. Merges stage into a " +
                   "directory of their own — a half-written .wz, or MapleLib's multi-hundred-MB .TEMP scratch " +
                   "file, must never appear beside files it did not make. See WZ-MERGE-PROCEDURE.md section 1.";
        return null;
    }

    // ============ T23: POSITIONAL-ARRAY CONTINUITY, the last thing before an install ============
    //
    // The gate in Merge() refuses to CREATE a hole. This refuses to INSTALL one, whoever made it —
    // a hand edit in HaRepacker, an older build of this tool, a merge run before the gate existed.
    // The hole that motivated it (`MapLogin.img/back` = {0..47,53,54}) sailed through `guard`
    // rc=0 and through post-write verification, because both answer "does this parse and resolve",
    // and a holed array parses and resolves perfectly. It just kills the client.
    //
    // ArrayRange() is useless here BY CONSTRUCTION: its definition of an array is "one consecutive
    // run", so a holed array is not an array and it returns null. Guard needs the converse test,
    // and the converse test has to separate a broken array from an ID TABLE — String.wz's
    // `Consume.img` holds 2,290 integer item ids with enormous gaps and every one is legitimate.
    //
    // Heuristic, with its ceiling stated:
    //   * every child name a canonical non-negative integer (the gate's own IsIndex), >= 2 of them
    //   * the run starts at 0 or 1 — a positional array is walked from the beginning; an id table
    //     starts wherever the ids start
    //   * at least half the span occupied (span <= 2*count) — a partial merge leaves a dense
    //     prefix plus a few stragglers; an id table is orders of magnitude sparser
    //   * and there is at least one missing index
    // ponytail: a genuinely SPARSE array that starts at 0 reads as an id table here and is missed
    // (`Quest.wz/Check.img/4940` = {0,1,4961} is exactly that shape and is legitimate — see
    // WZ-MERGE-PROCEDURE.md 4.4). Same blind spot the merge gate documents, same answer: the
    // deny-list stands in front of those. Tightening this instead would refuse real client data.
    static (int Min, int Max, int Count)? HoledArray(IReadOnlyList<string> childNames)
    {
        if (childNames.Count < 2) return null;
        var seen = new HashSet<int>();
        int min = int.MaxValue, max = int.MinValue;
        foreach (var n in childNames)
        {
            if (!IsIndex(n, out int v) || !seen.Add(v)) return null;
            if (v < min) min = v;
            if (v > max) max = v;
        }
        if (min > 1) return null;                       // an id table, not slot 0 of anything
        long span = (long)max - min + 1;
        if (span == seen.Count) return null;            // consecutive: this is a healthy array
        if (span > 2L * seen.Count) return null;        // too sparse to be a walked array
        return (min, max, seen.Count);
    }

    // Every container in <wzPath> that HoledArray flags, as "<path>\t<missing indices>".
    // Read-only: images are parsed, inspected and immediately unparsed, the same bounded walk
    // VerifyFile uses so Map.wz (629 MB) does not have to be materialised.
    // path -> the indices the run is MISSING. The missing SET, not just the path, because the
    // baseline discount has to tell "this hole was already here" from "this merge made it worse".
    static void ScanHoles(WzObject o, string path, Dictionary<string, SortedSet<int>> found, int depth = 0)
    {
        // A UOL's Kids() are the RESOLVED TARGET's children (the cycle that stack-overflowed
        // `hash`, see CanonMaxDepth). The target is scanned at its own path; do not follow.
        if (o is MapleLib.WzLib.WzProperties.WzUOLProperty || depth >= CanonMaxDepth) return;
        var kids = Kids(o).ToList();
        if (kids.Count == 0) return;
        if (HoledArray(kids.Select(k => k.Name).ToList()) is { } h)
        {
            var have = kids.Select(k => int.Parse(k.Name)).ToHashSet();
            found[path] = new SortedSet<int>(Enumerable.Range(h.Min, h.Max - h.Min + 1).Where(i => !have.Contains(i)));
        }
        foreach (var k in kids) ScanHoles(k, path + "/" + k.Name, found, depth + 1);
    }

    static Dictionary<string, SortedSet<int>> HoleReport(string wzPath)
    {
        var found = new Dictionary<string, SortedSet<int>>(StringComparer.OrdinalIgnoreCase);
        var (f, _ver) = Open(wzPath);
        using var _f = f;

        void Walk(WzDirectory d, string prefix)
        {
            foreach (var img in d.WzImages)
            {
                // An image that will not parse is verify's business, not continuity's.
                try { if (!img.Parsed && !img.ParseImage()) continue; }
                catch { continue; }
                ScanHoles(img, prefix + img.Name, found);
                img.UnparseImage();
            }
            foreach (var sub in d.WzDirectories) Walk(sub, prefix + sub.Name + "/");
        }
        Walk(f.WzDirectory, "");
        return found;
    }

    // Read-only: answers "would a merge be allowed to write here?" and writes nothing, ever.
    // This is how the guard gets tested against the real client directory without a merge that
    // could land on it if the guard were wrong. When something already EXISTS at that path it
    // also answers the second question — "and is what is there installable?" — which as of T23
    // means: no positional array in it has a hole.
    static int Guard(string[] rawArgs)
    {
        var argv = rawArgs.ToList();
        string? baselineFile = TakeFlag(argv, "--baseline");
        var args = argv.ToArray();
        if (args.Length < 2) { Usage(); return 2; }
        string full = Path.GetFullPath(args[1]);
        string? why = OutDirRefusal(full);
        if (why != null) return Refuse(why);
        Console.WriteLine($"ALLOWED: {full}  (directory {Path.GetDirectoryName(full)} is acceptable output staging)");

        if (!File.Exists(full))
        {
            Console.WriteLine("no file at that path yet — nothing to check for positional-array continuity.");
            return 0;
        }
        // --baseline is REQUIRED once there is a file to check, for the same reason --deny and
        // --live are required on a merge: without it this check has no safe default and would
        // report the wrong answer confidently. Measured on the known-good 11h UI.wz merge, the
        // LIVE tree carries NINE containers that trip the heuristic and are all legitimate
        // (`ChatBalloon.img/pet` is missing 16 of 52, `NameTag.img/medal` 43 of 125). Bare, this
        // refuses every real file; relaxed to "warn only" it would be a false green on exactly
        // the file it exists to stop. The pre-merge target is what separates the two.
        if (baselineFile == null)
            throw new BadArgs($"a file already exists at {full}, so guard also asserts POSITIONAL-ARRAY CONTINUITY on it — " +
                @"and that needs --baseline <the pre-merge target, e.g. <stage>\<T>\pre\<Name>.wz>. The live client tree " +
                "itself contains integer containers with gaps that are perfectly legitimate (UI.wz alone has nine), so " +
                "'holed' is only meaningful as a DIFFERENCE from the file the merge started out from. Without a baseline " +
                "this check would either refuse every real file or have to be downgraded to a warning, and a warning on " +
                "the last step before an install is a false green. (Asking 'may I write here?' about a path that does not " +
                "exist yet still takes no flags.)");
        Console.WriteLine($"array continuity: scanning {full} …");
        var holes = HoleReport(full);
        // The heuristic above is only a PREFILTER. The verdict is the merge gate's own question,
        // asked of the pre-merge target: was this container a CONSECUTIVE RUN there — i.e. was it
        // an array at all by ArrayRange's definition?
        //   * not a consecutive run in the baseline -> it never was an array, it is an id table,
        //     and adding an id to an id table legitimately widens the gaps. Discount.
        //     `UI.wz/NameTag.img/medal` is exactly this: 82 medal ids scattered over 0..124, and
        //     the known-good 46-row 11h merge appends 96 and 124 to it. Any rule phrased as "did
        //     the missing set grow?" refuses that merge. The question is whether the container is
        //     an ARRAY, never whether its gaps grew.
        //   * a consecutive run in the baseline, holed now -> this merge broke a real positional
        //     array. That is MapLogin.img/back, and it is the entire point of this check.
        //   * absent from the baseline -> a container this merge brought in whole, arriving holed.
        //     Nothing to discount it against, so it stands.
        // Only the handful of candidate paths are resolved in the baseline, so this costs a few
        // image parses rather than a second full-tree walk.
        int bad = 0, discounted = 0;
        if (holes.Count > 0)
        {
            var (bf, _bver) = Open(Path.GetFullPath(baselineFile));
            using var _bf = bf;
            foreach (var (p, miss) in holes.OrderBy(k => k.Key, StringComparer.Ordinal))
            {
                var segs = p.Split('/', StringSplitOptions.RemoveEmptyEntries);
                var was = Resolve(bf, segs, segs.Length);
                var run = was == null ? null : ArrayRange(Kids(was).Select(k => k.Name));
                string msg = $"{p}: missing {miss.Count} index(es) — {string.Join(",", miss)}";
                if (was != null && run == null)
                { discounted++; Console.WriteLine($"  not an array: {msg} — this container was ALREADY not a consecutive run in the baseline, so it is an id table, not a positional array; discounted"); }
                else
                {
                    Console.Error.WriteLine($"  HOLED ARRAY {msg} — " + (was == null
                        ? "this container is not in the baseline at all, so it arrived holed"
                        : $"the baseline held the consecutive run {run!.Value.Min}..{run.Value.Min + run.Value.Count - 1}, and this file breaks it"));
                    bad++;
                }
            }
        }
        if (bad == 0)
        {
            Console.WriteLine($"array continuity OK: no holed positional array{(discounted > 0 ? $" ({discounted} gapped id table(s) discounted — not consecutive runs in the baseline either)" : "")}.");
            return 0;
        }
        Console.Error.WriteLine($"REFUSED TO CLEAR {full}: {bad} positional array(s) have a HOLE this merge is responsible for. " +
            "A client that walks an array stops at the first missing index — this is what turned UI.wz/MapLogin.img/back into a " +
            "crash before the login screen, past a green `guard` and a green post-write verification. DO NOT INSTALL THIS FILE. " +
            $"Supply every missing index or drop that container's rows entirely. ({discounted} further gap(s) were discounted: not consecutive runs in {baselineFile} either, so id tables rather than arrays.)");
        return 4;
    }

    // ================================ T23: selftest ================================
    //
    // Runs against nothing on disk, so it is safe anywhere and needs no client, no staging
    // directory and no fixture .wz. Same shape as tools\patch-evan-gate.ps1 -SelfTest: assert,
    // count failures, exit non-zero. `WzMerge selftest` is the check that fails if either half of
    // the T23 fix regresses.
    //
    // What it reproduces is the real incident, exactly: UI.wz/MapLogin.img/back held 0..47, the
    // manifest asked for 48..54, v84 had inserted five entries earlier so 48..52 are the target's
    // own 0..4 shifted, and 53/54 are new. Before T23 the gate refused 48..52 and ALLOWED 53/54.
    static int SelfTest()
    {
        int fail = 0;
        void Check(bool ok, string what)
        {
            Console.WriteLine($"  {(ok ? "PASS" : "FAIL")}  {what}");
            if (!ok) fail++;
        }

        // ---- 1. the merge gate: MapLogin.img/back, baseline 0..47, manifest 48..54 ----
        // A slot is a WzSubProperty holding one int, which is what SlotDigest actually digests
        // (Canon walks the children; the slot's own NAME is excluded, which is what lets slot 48
        // of the source compare equal to slot 0 of the target).
        static WzImageProperty Slot(string name, int payload)
        {
            var s = new MapleLib.WzLib.WzProperties.WzSubProperty(name);
            s.AddProperty(new MapleLib.WzLib.WzProperties.WzIntProperty("x", payload));
            return s;
        }
        const string wz = "UI.wz", container = "UI.wz/MapLogin.img/back";
        var baseline = new Dictionary<string, ArrayBase?>(StringComparer.OrdinalIgnoreCase)
        {
            // pre-seeded, so PositionalRefusal never touches a WzFile and this test needs no disk
            [container] = new ArrayBase
            {
                Min = 0,
                Count = 48,
                Digests = Enumerable.Range(0, 48).Select(i => SlotDigest(Slot(i.ToString(), i))).ToHashSet()
            }
        };
        var manifest = Enumerable.Range(48, 7).Select(i => $"{container}/{i}").ToList();
        var requested = RequestedSlots(manifest);
        Check(requested[container].SetEquals(Enumerable.Range(48, 7)), "manifest requests slots 48..54");

        RefusedSlots.Clear(); Conflicts.Clear();
        var verdicts = new Dictionary<int, string?>();
        foreach (var row in manifest)
        {
            int idx = int.Parse(row[(row.LastIndexOf('/') + 1)..]);
            // 48..52 duplicate the target's own 0..4 (v84 inserted five entries earlier);
            // 53/54 carry content the target does not have.
            var src = Slot(idx.ToString(), idx <= 52 ? idx - 48 : 1000 + idx);
            var rel = Rel(row, wz);
            string? why = PositionalRefusal(null!, wz, rel, src, baseline, requested);
            verdicts[idx] = why;
            if (why != null) Conflict(row, why);       // the one funnel that records refused slots
        }
        // 48 is the first refusal and it is the content-duplicate rule. From 49 up the RUNNING
        // state has already been poisoned, so every later slot is refused for the stronger reason
        // — including 53 and 54, which are the two the broken build let through onto the hole.
        Check(verdicts[48]?.Contains("content-identical") == true, "slot 48 refused as a content-duplicate");
        for (int i = 49; i <= 54; i++)
            Check(verdicts[i]?.Contains("slot 48 was REFUSED earlier in this run") == true,
                  $"slot {i} refused because slot 48 was refused (running state, not baseline count){(i >= 53 ? " — THE T23 REGRESSION" : "")}");
        Check(verdicts.Values.All(v => v != null), "no slot of a partially-refused array is allowed through");

        // Same array, nothing refused: the gate must still permit a clean full append, or the fix
        // has been made by breaking every legitimate merge instead.
        RefusedSlots.Clear(); Conflicts.Clear();
        var clean = Enumerable.Range(48, 7).Select(i => PositionalRefusal(
            null!, wz, Rel($"{container}/{i}", wz), Slot(i.ToString(), 1000 + i), baseline, requested)).ToList();
        Check(clean.All(v => v == null), "48..54 all-new content is still a clean append (the gate did not get blunt)");

        // ---- 2. guard's continuity assertion ----
        // The exact holed array the broken merge installed, built as real WzObjects and scanned
        // by the same ScanHoles() `guard` runs over a finished .wz.
        var img = new WzImage("MapLogin.img");
        var back = new MapleLib.WzLib.WzProperties.WzSubProperty("back");
        foreach (int i in Enumerable.Range(0, 48).Concat(new[] { 53, 54 })) back.AddProperty(Slot(i.ToString(), i));
        img.AddProperty(back);
        var holes = new Dictionary<string, SortedSet<int>>(StringComparer.OrdinalIgnoreCase);
        ScanHoles(img, "MapLogin.img", holes);
        Check(holes.ContainsKey("MapLogin.img/back"), "guard finds the hole in {0..47,53,54}");
        Check(holes.TryGetValue("MapLogin.img/back", out var m) && m.SetEquals(new[] { 48, 49, 50, 51, 52 }),
              "guard names the missing indices 48,49,50,51,52");
        // The verdict is ArrayRange asked of the BASELINE, not the size of the gap. `back` was
        // 0..47 there — a consecutive run, a real array — so the hole stands. `NameTag.img/medal`
        // was already scattered there, so it is an id table and its widening gaps are discounted;
        // without that, guard refuses the known-good 46-row 11h merge, which appends medal ids
        // 96 and 124 to a container whose highest id was 87.
        Check(ArrayRange(Enumerable.Range(0, 48).Select(i => i.ToString())) != null,
              "the baseline's back = 0..47 IS a consecutive run, so its hole is real damage");
        Check(ArrayRange(new[] { "0", "35", "87" }) == null,
              "a scattered id table (NameTag.img/medal) is NOT a run in the baseline, so guard discounts it");

        var healthy = new WzImage("MapLogin.img");
        var backOk = new MapleLib.WzLib.WzProperties.WzSubProperty("back");
        foreach (int i in Enumerable.Range(0, 55)) backOk.AddProperty(Slot(i.ToString(), i));
        healthy.AddProperty(backOk);
        var none = new Dictionary<string, SortedSet<int>>(StringComparer.OrdinalIgnoreCase);
        ScanHoles(healthy, "MapLogin.img", none);
        Check(none.Count == 0, "guard passes the repaired array {0..54}");

        // The blind spot and the false positives, pinned so a future tightening has to face them.
        Check(HoledArray(new[] { "0", "1", "2" }) == null, "a consecutive run is not a hole");
        Check(HoledArray(new[] { "1", "2" }) == null, "an array that starts at 1 is not a hole (03i)");
        Check(HoledArray(new[] { "0" }) == null, "a single child is not a hole");
        Check(HoledArray(new[] { "0", "04" }) == null, "a non-canonical name ('04') is not an index");
        Check(HoledArray(new[] { "0", "1", "4961" }) == null, "Quest.wz/Check.img/4940 = {0,1,4961} reads as an id table, not a hole (documented ceiling)");
        Check(HoledArray(new[] { "2000000", "2000001", "2000003" }) == null, "an id table that starts high is not a hole");
        Check(HoledArray(new[] { "0", "3" }) is { } && HoledArray(new[] { "0", "3" })!.Value.Count == 2,
              "a dense low run WITH a gap is a hole (Glove/01082262.img/swingOF = {0,3}; discount it with guard --baseline)");

        Console.WriteLine(fail == 0 ? "selftest: all checks passed" : $"selftest: {fail} CHECK(S) FAILED");
        return fail == 0 ? 0 : 4;
    }

    static string Sha256File(string path)
    {
        using var s = File.OpenRead(path);
        return Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(s)).ToLowerInvariant();
    }

    // Named flags are pulled out first so the positional signature below is exactly what it
    // always was and Usage() still describes it.
    static string? TakeFlag(List<string> a, string name)
    {
        int i = a.FindIndex(x => string.Equals(x, name, StringComparison.OrdinalIgnoreCase));
        if (i < 0) return null;
        if (i + 1 >= a.Count) throw new BadArgs($"{name} needs a value");
        string v = a[i + 1];
        a.RemoveRange(i, 2);
        return v;
    }

    static int Merge(string[] rawArgs)
    {
        var argv = rawArgs.ToList();
        string? denyFile = TakeFlag(argv, "--deny");
        string? forceFile = TakeFlag(argv, "--force");
        string? liveFile = TakeFlag(argv, "--live");
        var args = argv.ToArray();
        if (args.Length < 6) { Usage(); return 2; }
        bool dry = args[3] == "-";
        // Everything absolute up front: the save block below changes the process working
        // directory (see there for why), and a relative conflicts path would then land
        // somewhere else entirely.
        string srcPath = Path.GetFullPath(args[1]), tgtPath = Path.GetFullPath(args[2]);
        string outPath = dry ? "-" : Path.GetFullPath(args[3]);
        string conflictsPath = Path.GetFullPath(args[5]);

        // B1: --deny is REQUIRED, on dry runs too. A dry run is what the operator reads before
        // deciding, so a dry run that cannot see the deny-list produces the wrong decision just
        // as surely as a real merge that cannot. The list was inert for the whole of tickets
        // 04-06 precisely because nothing forced it to be passed.
        if (denyFile == null)
            throw new BadArgs(@"merge requires --deny <file> (use docs\wz-baseline\merge-lists\COLLISION-DENY.txt). " +
                "The additive-only gate only refuses paths that already exist, so it is structurally blind to a " +
                "harmful v84 ADDITION — a server-allocated NPC id, a positional-array slot spliced into a list " +
                "the server owns. There is no safe default for that; the list has to be supplied.");
        var deny = LoadRoots("deny", denyFile);
        var force = forceFile == null ? new List<(string path, string reason)>() : LoadRoots("force", forceFile);
        AssertNoOverlap(deny, force);
        Console.WriteLine($"deny-list {denyFile}: {deny.Count} roots" +
                          (forceFile == null ? "; no force-list (nothing may be overwritten)" : $"; force-list {forceFile}: {force.Count} roots"));

        var paths = ReadPaths(args[4]);

        if (!dry)
        {
            if (OutDirRefusal(outPath) is string why) return Refuse(why);
            // Kept: these two say something the directory rule does not, and they name the exact
            // mistake rather than describing a policy.
            if (SamePath(outPath, tgtPath))
                return Refuse($"<outWz> is the target itself ({outPath}). SaveToDisk truncates the destination before it reads the images it needs out of it. Write to a staging directory and copy afterwards.");
            if (SamePath(outPath, srcPath))
                return Refuse($"<outWz> is the v84 source ({outPath}). v84 is read-only input.");

            // H1: <stage>\pre\ used to be one directory shared by every ticket, and two tickets
            // that touch the same .wz cannot share it. If 06 installs its merged Map.wz and 07
            // then merges onto the stale pre snapshot, 07's output silently REVERTS 06 — both
            // runs exit 0, and section 6.2's diff compares against that same stale snapshot and
            // reports clean. Nothing detects it later, so detect it here: the target has to still
            // be a byte-identical copy of the live file it was snapshotted from.
            if (liveFile == null)
                throw new BadArgs(@"a real merge requires --live <path to the live .wz that <targetWz> was copied from>. " +
                    @"<stage>\<T>\pre\<Name>.wz is a per-ticket snapshot; a stale one silently reverts whatever another " +
                    "ticket installed in the meantime, with both runs exiting 0. The tool hashes the two and refuses if " +
                    "they differ. (Dry runs do not need it.)");
            string livePath = Path.GetFullPath(liveFile);
            if (!File.Exists(livePath)) throw new BadArgs($"--live {livePath} does not exist");
            // Otherwise `--live <the target itself>` hashes equal trivially and the check that
            // makes a stale snapshot impossible quietly becomes a check of nothing.
            if (SamePath(livePath, tgtPath))
                return Refuse($"--live is the target itself ({livePath}). It must name the LIVE .wz in the client " +
                    "directory that <targetWz> was copied from; comparing the target with itself proves nothing.");
            Console.WriteLine($"snapshot check: hashing {tgtPath} and {livePath} …");
            string th = Sha256File(tgtPath), lh = Sha256File(livePath);
            if (th != lh)
                return Refuse($"STALE SNAPSHOT. Target {tgtPath} ({th}) is not a byte-identical copy of the live {livePath} ({lh}). " +
                    "Either the live file changed after the snapshot was taken (another ticket installed) or the snapshot is not of that file. " +
                    "Re-take the snapshot (procedure 5.1) and re-read your dry run — merging onto a stale copy reverts whatever landed in between.");
            Console.WriteLine($"snapshot check OK: {th}");
        }

        var (src, srcVer) = Open(srcPath);
        using var _s = src;
        var (tgt, tgtVer) = Open(tgtPath);
        using var _t = tgt;

        // H4: BOTH subcommands derive the manifest root from the SOURCE. `merge` used to take
        // it from the target, so renaming the staging copy of the target silently broke every
        // Rel() lookup here while the identical `xml` run kept working.
        string wzName = Path.GetFileName(srcPath);
        if (!string.Equals(wzName, Path.GetFileName(tgtPath), StringComparison.OrdinalIgnoreCase))
            Console.WriteLine($"note: target is named {Path.GetFileName(tgtPath)}; manifest rows are matched against the source name {wzName}");
        Console.WriteLine($"source {srcPath}  iv={srcVer} patchVersion={src.Version}");
        Console.WriteLine($"target {tgtPath}  iv={tgtVer} patchVersion={tgt.Version}");
        Console.WriteLine($"{paths.Count} paths requested");

        int added = 0, forced = 0;
        var touched = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);   // images to content-check (M2)
        var noContentCheck = new List<string>();
        // B5: state for the positional-array gate. Both are read-only once built / memoised on
        // first use, and both describe the target as it was BEFORE this run — see ArrayBase.
        var arrayBase = new Dictionary<string, ArrayBase?>(StringComparer.OrdinalIgnoreCase);
        var slotsWanted = RequestedSlots(paths);
        // T23: slots this run actually ADDED, per array container. The running check inside
        // PositionalRefusal only sees refusals that happened EARLIER in the manifest, and manifest
        // rows are sorted as TEXT ('back/10' before 'back/9'), so a refusal can arrive after an
        // append that depended on it. This is swept after the loop and before the save.
        // slot -> the manifest row VERBATIM. Not reconstructed as "container/slot": conflicts.txt
        // and the post-write `expect` list are both keyed on the row exactly as the manifest wrote
        // it, and an undo logged under a normalised spelling would leave the row in `expect` and
        // fail verification for the wrong reason.
        var slotsGranted = new Dictionary<string, SortedDictionary<int, string>>(StringComparer.OrdinalIgnoreCase);
        foreach (var manifestPath in paths)
        {
            // ===================== B1: THE DENY-LIST =====================
            // Checked BEFORE the additive-only gate, because every deny hazard is a v84 ADDITION
            // that the target lacks: the gate sees no collision, conflicts.txt stays empty, and
            // the row is written. Ten server-allocated NPC ids, 36 monster-book reward slots that
            // splice into 17 Cosmic drop lists, one NPC that merges half-v83/half-v84.
            // GateRefusal also handles the foreign-root case: `deps` emits rows for every .wz a
            // map depends on, so a Map.wz merge is legitimately handed Sound.wz rows. Refusing
            // them by name beats throwing out of Rel(), which killed the run on the first one.
            if (GateRefusal(deny, wzName, manifestPath) is string refusal)
            { Conflict(manifestPath, refusal); continue; }

            var rel = Rel(manifestPath, wzName);

            // Resolved BEFORE anything is removed: a force-list row deletes the live node and puts
            // v84's in its place, so every reason this row might fail has to be known while the
            // live node is still there. Otherwise "MISSING IN SOURCE" means "deleted, nothing put
            // back" — an additive-only tool silently performing a deletion.
            var srcObj = Resolve(src, rel, rel.Length);
            if (srcObj == null) { Conflict(manifestPath, "MISSING IN SOURCE — manifest is stale"); continue; }

            // ADDITIVE-ONLY GATE. Nothing below this can overwrite, EXCEPT through the
            // force-list: the only mutation performed is AddProperty/AddImage onto a parent that
            // does not already hold this name, or onto one the operator explicitly authorised
            // clearing first. The force-list is the ONLY way past this; there is no flag that
            // turns the gate off wholesale.
            var existing = Resolve(tgt, rel, rel.Length);
            (string path, string reason)? fHit = null;
            if (existing != null)
            {
                fHit = RootOver(force, manifestPath);
                if (fHit == null) { Conflict(manifestPath, "already exists in target"); continue; }
            }

            var parent = Resolve(tgt, rel, rel.Length - 1);
            if (parent == null) { Conflict(manifestPath, "parent path absent in target — import the parent first"); continue; }

            // B5: THE POSITIONAL-ARRAY GATE, reported as its own reason so an operator can tell
            // "this index would land on a different entry" from "already exists". A force-list row
            // is an explicit operator decision about a named node and is left alone; deny the row
            // instead if the decision was wrong.
            if (fHit == null && PositionalRefusal(tgt, wzName, rel, srcObj, arrayBase, slotsWanted) is string aRefusal)
            { Conflict(manifestPath, aRefusal); continue; }

            // The `existing?.Remove()` calls live INSIDE the branches, immediately before the add
            // that replaces the removed node, and never on the `default:` path. Removing before
            // the switch meant an unsupported (parent, source) shape deleted the live node and
            // then `continue`d — and the row is excluded from post-write verification precisely
            // because it landed in conflicts.txt, so nothing downstream would have noticed.
            switch (parent, srcObj)
            {
                case (WzDirectory pd, WzImage si):
                    existing?.Remove();
                    pd.AddImage(si.DeepClone());
                    break;
                // whole new sub-directory, e.g. v84's Skill.wz/Dragon (Evan's dragon
                // animations). ponytail: DeepClone materialises every image beneath it, so
                // this is memory-bound — Skill.wz/Dragon is tens of MB. Fine so far; if a
                // bigger directory ever OOMs, expand the manifest to per-image rows instead.
                case (WzDirectory pd2, WzDirectory sd):
                    existing?.Remove();
                    pd2.AddDirectory(sd.DeepClone());
                    break;
                // The AddProperty below only lands because the gate above already walked
                // through `WzImage img => img[s]`, whose indexer parses the image on demand.
                // Short-circuit or reorder that gate and adds are silently dropped onto an
                // unparsed image. The coupling is real; do not "optimise" the gate away.
                case (WzImage pi, WzImageProperty sp):
                    existing?.Remove();
                    pi.AddProperty(sp.DeepClone());
                    pi.Changed = true;
                    break;
                case (WzImageProperty pp, WzImageProperty sp2) when pp is MapleLib.WzLib.IPropertyContainer pc:
                    existing?.Remove();
                    pc.AddProperty(sp2.DeepClone());
                    if (pp.ParentImage != null) pp.ParentImage.Changed = true;
                    break;
                default:
                    // nothing has been removed at this point, and nothing will be
                    Conflict(manifestPath, $"unsupported shape: parent={parent.GetType().Name} source={srcObj.GetType().Name}");
                    continue;
            }
            if (fHit is { } f)
            {
                Console.WriteLine($"  FORCE {manifestPath}  (authorised overwrite [{f.path}]: {f.reason})");
                forced++;
            }
            // The gate is the ONLY protection for three of the four branches above:
            // WzImage.AddProperty throws on a duplicate name, but AddImage, AddDirectory and
            // WzSubProperty.AddProperty all append blindly. Assert what the gate promised.
            int dupes = Kids(parent).Count(k => string.Equals(k.Name, rel[^1], StringComparison.OrdinalIgnoreCase));
            if (dupes != 1)
                throw new Exception($"INTERNAL: '{manifestPath}' appears {dupes}x under its parent after the add — the additive-only gate did not hold. Nothing was written.");

            added++;
            Console.WriteLine($"  ADD   {manifestPath}");

            // T23: record the append against the container the array gate recognised. Only
            // containers arrayBase says ARE arrays are tracked — everything else has names, not
            // positions, and a missing name is not a hole.
            {
                string container = string.Join('/', new[] { wzName }.Concat(rel.Take(rel.Length - 1)));
                if (IsIndex(rel[^1], out int slot) && arrayBase.TryGetValue(container, out var ab) && ab != null)
                {
                    if (!slotsGranted.TryGetValue(container, out var g)) slotsGranted[container] = g = new SortedDictionary<int, string>();
                    g[slot] = manifestPath;
                }
            }

            // M2: remember which image this landed in. That image is the only one SaveToDisk
            // re-serializes (Changed=true), so it is the only place a serializer bug can live —
            // and the only place a content check is worth paying for.
            int imgIdx = Array.FindIndex(rel, s => s.EndsWith(".img", StringComparison.OrdinalIgnoreCase));
            if (imgIdx >= 0) touched.Add(string.Join('/', new[] { wzName }.Concat(rel.Take(imgIdx + 1))));
            else noContentCheck.Add(manifestPath);   // a whole-directory row: no single image to digest
        }

        // T23: CONTINUITY SWEEP — the running check, completed. PositionalRefusal can only see
        // refusals that already happened; add-list rows are sorted as TEXT, so a container can be
        // appended at 10 and refused at 9 in that order. Nothing has been written yet (SaveToDisk
        // is below), so an append that turns out to sit above a hole is simply UNDONE here and
        // reported like any other refusal. "If any index of an array is refused, every later index
        // of that same array is refused too" — enforced on the result, not on the reading order.
        foreach (var (container, granted) in slotsGranted)
        {
            var b = arrayBase[container]!;
            int next = b.Max + 1;
            var orphans = new List<(int slot, string row)>();
            foreach (var (slot, row) in granted) { if (slot == next) next++; else orphans.Add((slot, row)); }
            if (orphans.Count == 0) continue;
            Console.Error.WriteLine($"  HOLE in '{container}': the array ran {b.Min}..{b.Max} and this run fills it only to {next - 1}; " +
                $"slot(s) {string.Join(",", orphans.Select(o => o.slot))} sit ABOVE the hole and are being UNDONE.");
            foreach (var (slot, row) in orphans)
            {
                var r = Rel(row, wzName);
                var node = Resolve(tgt, r, r.Length);
                // The add is this run's own; not finding it back means the bookkeeping is wrong,
                // and shipping a holed array is exactly what must never happen. Stop.
                if (node == null) throw new Exception($"INTERNAL: '{row}' was added by this run but cannot be resolved for the continuity undo. Nothing was written.");
                node.Remove();
                added--;
                Conflict(row, $"POSITIONAL ARRAY: UNDONE — '{container}' ran {b.Min}..{b.Max} and this run could only fill it up to {next - 1} (the slot(s) in between were refused; their reasons are elsewhere in this file). Appending {slot} on top of that hole would leave the array discontinuous, which is what broke UI.wz/MapLogin.img/back. Either supply every intermediate slot or drop this container's rows entirely.");
            }
        }

        // THE VERSION TRAP, resolved structurally rather than by remembering a menu option.
        // We never open, edit or re-save a v84 file: v84 is read-only source. The file being
        // written is the LIVE client's own file, so both the encryption IV (MapleVersion)
        // and the patch-version hash (Version) are the ones MapleLib parsed off that file,
        // and SaveToDisk(path) with no override re-emits exactly those. There is no
        // conversion step to get wrong.
        // <outWz> of "-" = dry run: answer "what would additive-only refuse?" without
        // repacking 600 MB. Resolve() only parses the images a listed path touches, so a
        // dry run over a whole add-list is seconds, not minutes. Run this BEFORE a real
        // merge on every file — conflicts.txt is the point, the .wz is the by-product.
        bool verified = true;
        if (dry)
        {
            Console.WriteLine("dry run: not saving");
        }
        else
        {
            string outDir = Path.GetDirectoryName(outPath)!;
            Directory.CreateDirectory(outDir);
            // Claim the directory as staging so the next merge of this ticket is allowed to add a
            // second .wz beside this one (see OutDirRefusal).
            File.WriteAllText(Path.Combine(outDir, StageMarker),
                "Written by WzMerge. Marks this directory as merge staging, which is the only kind of\r\n" +
                "directory WzMerge will write a .wz into when other .wz files are already present.\r\n" +
                "Never create one in a game client directory.\r\n");

            // M2: the pre-save content digest of every image this run inserted into, taken from
            // the in-memory merged tree. Compared against the same images re-read off the written
            // file below. This is the check that would notice a corrupted canvas payload — the
            // documented hole that path re-resolution and ParseImage both walk straight past.
            var digests = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            foreach (var imgPath in touched)
            {
                var r = Rel(imgPath, wzName);
                var o = Resolve(tgt, r, r.Length);
                // Not "skip it": this image is one the merge itself says it wrote into, so failing
                // to find it in the merged tree means the bookkeeping is wrong. Silently dropping
                // it would remove the row from the content check without saying so.
                if (o == null) throw new Exception($"INTERNAL: '{imgPath}' was inserted into but cannot be resolved in the merged tree. Nothing was written.");
                digests[imgPath] = Digest(o);
            }
            Console.WriteLine($"content digests taken for {digests.Count} inserted-into image(s)" +
                (noContentCheck.Count > 0 ? $"; {noContentCheck.Count} whole-directory row(s) are NOT content-checked: {string.Join(", ", noContentCheck)}" : ""));

            // Write beside the destination, verify, and only then move into place. The move is
            // the only step that touches <outWz>, so an OOM / disk-full / Ctrl-C leaves a
            // .partial nobody will mistake for a finished file, instead of a truncated .wz
            // that looks exactly like a good one.
            string partial = outPath + ".partial";
            if (File.Exists(partial)) File.Delete(partial);

            // MapleLib's scratch file is relative to the process CWD (WzFile.cs:664). Run
            // WzMerge from D:\games\MapleStory\ and it drops a several-hundred-MB Item.TEMP
            // into the live client directory. Pin the CWD to the staging directory for the
            // duration of the save rather than trusting the operator's shell.
            string cwd = Directory.GetCurrentDirectory();
            Console.WriteLine($"saving {partial} at iv={tgt.MapleVersion} patchVersion={tgt.Version} (inherited from target); scratch .TEMP in {outDir}");
            try
            {
                Directory.SetCurrentDirectory(outDir);
                tgt.SaveToDisk(partial);
            }
            finally { Directory.SetCurrentDirectory(cwd); }

            // POST-WRITE VERIFICATION. Re-open what we just wrote, as a stranger would, and
            // re-resolve every path we claim to have added. Nothing else in this pipeline ever
            // re-read the tool's own output; the first reader used to be the game client.
            var expect = paths.Where(p => !Conflicts.Any(c => c.StartsWith(p + "\t", StringComparison.Ordinal))).ToList();
            // VerifyFile handles a file that will not OPEN, but everything after that point can
            // still throw — Digest() decodes canvases, and a corrupted payload is exactly what
            // this check exists to find. Unhandled, that reached Main and exited 1, whose contract
            // reads "nothing installable was produced" — while a plausible-looking .partial sat on
            // disk with no DO-NOT-INSTALL line anywhere. A verification that throws IS a failed
            // verification: exit 4, and say where the file is.
            try { verified = VerifyFile(partial, wzName, expect, digests, tgtPath); }
            catch (Exception ex)
            {
                Console.Error.WriteLine($"  VERIFICATION THREW: {ex.GetType().Name} {ex.Message}");
                verified = false;
            }
            if (verified)
            {
                File.Move(partial, outPath, true);
                Console.WriteLine($"verified OK -> {outPath}");
            }
            else
            {
                Console.Error.WriteLine($"VERIFICATION FAILED. Output left at {partial} and NOT promoted to {outPath}. Do not install it.");
            }
        }

        WriteConflicts(conflictsPath, $"{wzName}: additive-only merge{(dry ? " — DRY RUN, nothing was written" : "")}," +
            $" {srcPath} -> {tgtPath}, {added} nodes {(dry ? "would be added" : "added")} ({forced} by force-list), {Conflicts.Count} refused");
        Console.WriteLine($"added {added} (forced {forced}), refused {Conflicts.Count}");
        if (!verified) return 4;
        // M1: "added 5000, refused 1" and "added 0, refused 1" were the same exit code, and only
        // the second one means "this run accomplished nothing" — the state a stale manifest, a
        // wrong bucket or the wrong <targetWz> produces, and the one that reads as "nothing owed".
        if (added == 0) { Console.Error.WriteLine("NOTHING WAS ADDED. Every requested row was refused — read conflicts.txt before assuming the target already had this content."); return 5; }
        return Conflicts.Count > 0 ? 3 : 0;
    }

    // ---------- xml (server tree, Cosmic\wz\) ----------

    // Cosmic reads plain "Private Server" XML: WzClassicXmlSerializer(2, Windows, base64=OFF).
    // Confirmed by inspecting wz/Item.wz/Consume/0200.img.xml — 2-space indent, CRLF, no BOM,
    // <canvas .../> carrying width/height but no basedata. So the server XML is produced by
    // RE-EXPORTING with base64 off, never by exporting with it on and stripping afterwards.
    sealed class FragmentSerializer : WzClassicXmlSerializer
    {
        public FragmentSerializer() : base(2, LineBreak.Windows, false) { }

        public string Fragment(WzImageProperty prop, string depth, string xmlPath)
        {
            var sw = new StringWriter();
            WritePropertyToXML(sw, depth, prop, xmlPath);
            return sw.ToString();
        }
    }

    static int Xml(string[] rawArgs)
    {
        var argv = rawArgs.ToList();
        string? denyFile = TakeFlag(argv, "--deny");
        string? forceFile = TakeFlag(argv, "--force");
        var args = argv.ToArray();
        if (args.Length < 5) { Usage(); return 2; }
        string srcPath = args[1], xmlRoot = args[2];
        // H4: the XML side had no dry run at all, while the procedure said "dry run before
        // every real merge". A trailing "-" mirrors merge's "-" in the <outWz> slot: every
        // check below still runs (including the splice position), nothing is written.
        bool dry = args.Length > 5 && args[5] == "-";

        // B1, same two lists and the same parser as the binary side. The server tree is exposed
        // to exactly the same hazards — NpcLocation ids and MonsterBook reward slots are read by
        // the server out of wz/, not by the client — so the deny-list is required here too.
        if (denyFile == null)
            throw new BadArgs(@"xml requires --deny <file> (docs\wz-baseline\merge-lists\COLLISION-DENY.txt). " +
                "The XML gate refuses overwrites; like the binary gate it cannot see a harmful ADDITION.");
        var deny = LoadRoots("deny", denyFile);
        var force = forceFile == null ? new List<(string path, string reason)>() : LoadRoots("force", forceFile);
        AssertNoOverlap(deny, force);

        // M4: the XML side had no path guards at all and failed safe against the client only by
        // accident. Same absolute rule as the binary side: never write into a game install.
        // Only for a real run — a dry run writes nothing, and the exit-code contract says a dry
        // run's answer is its findings, not a refusal.
        if (!dry && OutDirRefusal(Path.Combine(Path.GetFullPath(xmlRoot), "x")) is string dirWhy) return Refuse(dirWhy);
        // xmlRoot is the tree root; the writes land several directories deeper (xmlRoot\<Name>.wz\
        // <sub>\<img>.xml), so guard each of those the first time it is written to as well.
        var dirsChecked = new HashSet<string>(StringComparer.OrdinalIgnoreCase);

        var paths = ReadPaths(args[3]);
        var (src, srcVer) = Open(srcPath);
        using var _s = src;
        string wzName = Path.GetFileName(srcPath);
        var ser = new FragmentSerializer();
        Console.WriteLine($"source {srcPath} iv={srcVer}; xml root {xmlRoot}{(dry ? "  [DRY RUN — nothing will be written]" : "")}");
        Console.WriteLine($"deny-list {denyFile}: {deny.Count} roots" +
                          (forceFile == null ? "; no force-list (nothing may be overwritten)" : $"; force-list {forceFile}: {force.Count} roots"));

        int added = 0, forced = 0, unverified = 0;
        var xmlArrayBase = new Dictionary<string, (int Min, int Count)?>(StringComparer.OrdinalIgnoreCase);   // B5
        var xmlSlots = RequestedSlots(paths);
        foreach (var manifestPath in paths)
        {
            string rowRoot = manifestPath.Split('/')[0];
            if (!rowRoot.Equals(wzName, StringComparison.OrdinalIgnoreCase))
            { Conflict(manifestPath, $"row is rooted at {rowRoot}, not {wzName} — export it in that file's own run"); continue; }

            if (RootOver(deny, manifestPath) is { } dHit)
            { Conflict(manifestPath, $"DENIED by deny-list [{dHit.path}]: {dHit.reason}"); continue; }
            if (RootInside(deny, manifestPath) is { } dIn)
            { Conflict(manifestPath, $"DENIED by deny-list: this row is a copy root containing the denied node '{dIn.path}' ({dIn.reason}), and a partial write is not possible"); continue; }

            var rel = Rel(manifestPath, wzName);
            int imgIdx = Array.FindIndex(rel, s => s.EndsWith(".img", StringComparison.OrdinalIgnoreCase));
            if (imgIdx < 0) { Conflict(manifestPath, "no .img segment — cannot map to an XML file"); continue; }

            string xmlFile = Path.Combine(new[] { xmlRoot, wzName }
                .Concat(rel.Take(imgIdx + 1)).ToArray()) + ".xml";
            var inImg = rel.Skip(imgIdx + 1).ToArray();

            if (!dry && dirsChecked.Add(Path.GetDirectoryName(Path.GetFullPath(xmlFile))!)
                     && OutDirRefusal(xmlFile) is string fileDirWhy)
                return Refuse(fileDirWhy);

            var srcObj = Resolve(src, rel, rel.Length);
            if (srcObj == null) { Conflict(manifestPath, "MISSING IN SOURCE — manifest is stale"); continue; }

            if (inImg.Length == 0)
            {
                // whole new .img -> whole new .xml file
                if (File.Exists(xmlFile)) { Conflict(manifestPath, "xml file already exists: " + xmlFile); continue; }
                if (srcObj is not WzImage si) { Conflict(manifestPath, "expected a WzImage"); continue; }
                if (!dry)
                {
                    Directory.CreateDirectory(Path.GetDirectoryName(xmlFile)!);
                    ser.SerializeImage(si, xmlFile);
                }
                added++;
                Console.WriteLine($"  ADD   {manifestPath} -> new file {xmlFile}");
                continue;
            }

            if (!File.Exists(xmlFile)) { Conflict(manifestPath, "target xml file missing: " + xmlFile); continue; }
            if (srcObj is not WzImageProperty sp) { Conflict(manifestPath, "expected a WzImageProperty"); continue; }

            // The splice rewrites the whole file, so assert the shape it assumes rather than
            // silently normalising 500 KB of someone else's XML: no BOM, CRLF throughout.
            // Both are true of every file Cosmic ships; a violation means this is not a tree
            // this tool wrote and must not be reformatted as a side effect of adding one node.
            byte[] raw = File.ReadAllBytes(xmlFile);
            if (raw.Length >= 3 && raw[0] == 0xEF && raw[1] == 0xBB && raw[2] == 0xBF)
            { Conflict(manifestPath, "target xml has a UTF-8 BOM — refusing to rewrite it: " + xmlFile); continue; }
            string text = Encoding.UTF8.GetString(raw);
            if (text.Replace("\r\n", "").Contains('\n'))
            { Conflict(manifestPath, "target xml is not CRLF throughout — refusing to rewrite it: " + xmlFile); continue; }

            var lines = text.Split("\r\n").ToList();
            if (lines.Count > 0 && lines[^1].Length == 0) lines.RemoveAt(lines.Count - 1); // trailing CRLF
            string name = inImg[^1];

            // ponytail: manifest rows are no longer one level below the .img — String.wz ids
            // live at Eqp.img/Eqp/<category>/<id> and Etc.img/Etc/<id>. Walk the ancestor
            // chain in the text the same way the binary side walks it in the tree, narrowing
            // to the parent's line range. Indentation IS the structure here (the serializer
            // emits exactly 2 spaces per level); the BOM/CRLF refusals above are what make
            // that assumption safe, because they establish this is a file that serializer wrote.
            int root = lines.FindIndex(l => l.StartsWith("<imgdir", StringComparison.Ordinal));
            int rootClose = lines.FindLastIndex(l => l == "</imgdir>");
            if (root < 0 || rootClose <= root) { Conflict(manifestPath, "no root <imgdir> in " + xmlFile); continue; }
            int start = root + 1, end = rootClose, indent = 2;
            bool located = true;
            foreach (var seg in inImg[..^1])
            {
                // B5: the positional-array gate, same rule as the binary side (see
                // PositionalRefusal) against the one structure this scan can see: the names of
                // the container's children at this indent. Descending THROUGH an integer segment
                // whose container is an array means the row writes a field into an existing slot,
                // and slot n here need not be slot n in the source.
                if (IsIndex(seg, out int segIdx) && ArrayRange(ChildNames(lines, start, end, indent)) is { } ac0)
                {
                    Conflict(manifestPath, $"POSITIONAL ARRAY: the container of '{seg}' holds exactly the consecutive integers {ac0.Min}..{ac0.Min + ac0.Count - 1}, so its children are SLOTS, not identities. This row writes into slot {segIdx}, which already EXISTS. TWO hazards, both covered by this refusal: (a) the source's slot {segIdx} need not be the same entry as this tree's; (b) even when it is, the row EDITS a record the target already has by adding a field to it — and unlike a named node, this record has only a position, so you cannot tell WHICH record you are editing without dumping it. Ticket 09's `Check.img/<id>/0/lvmax` caps 108 working quests at Lv.40 that way, with the indices lining up perfectly. Dump both slots, then re-author the row or deny it.");
                    located = false; break;
                }
                int i = FindChild(lines, start, end, indent, seg);
                if (i < 0) { Conflict(manifestPath, $"parent '{seg}' absent in {xmlFile} — import the parent first"); located = false; break; }
                if (!lines[i].AsSpan(indent).StartsWith("<imgdir"))
                { Conflict(manifestPath, $"parent '{seg}' is not an <imgdir> in {xmlFile}"); located = false; break; }
                int close = lines.FindIndex(i + 1, end - i - 1, l => l == new string(' ', indent) + "</imgdir>");
                if (close < 0) { Conflict(manifestPath, $"parent '{seg}' is never closed in {xmlFile}"); located = false; break; }
                start = i + 1; end = close; indent += 2;
            }
            if (!located) continue;

            // M1: the BOM and CRLF assertions above guard axes the splice does not actually
            // depend on; the gate below depends ENTIRELY on the file being indented 2 spaces
            // per level, and nothing asserted that. A file indented any other way presents
            // zero children at every level, so the gate sees an empty container, refuses
            // nothing, and duplicates a node that was already there. (A genuinely empty
            // container also trips this — it refuses, which is the safe direction.)
            if (!Enumerable.Range(start, end - start).Any(i => NameAt(lines[i], indent) != null))
            { Conflict(manifestPath, $"no child element at indent {indent} in {xmlFile} — the additive gate is an indentation scan and would be blind here; refusing"); continue; }

            // additive-only. Same INTENT as the binary gate, different MECHANISM: this is a
            // line-text scan, so it only sees elements written the way Cosmic's serializer
            // writes them (2 spaces per level, name="…" on the opening line). OrdinalIgnoreCase
            // to match the binary side — WZ node lookup is case-insensitive, and an Ordinal
            // compare here would let a case-differing id past the gate and duplicate it.
            int forcedAt = -1;
            int at = FindChild(lines, start, end, indent, name);
            if (at >= 0)
            {
                // B1: the force-list is the only way past this gate, exactly as on the binary
                // side. It deletes the existing element's whole line range and lets the fragment
                // below take its place — the 37 force rows are all name-table stubs
                // ("MISSING NAME") that the server reads out of this tree, so a client-only
                // overwrite would leave the two halves disagreeing.
                if (RootOver(force, manifestPath) is not { } fHit)
                { Conflict(manifestPath, "already exists in " + xmlFile); continue; }
                int last = ElementEnd(lines, at, indent, end);
                if (last < 0)
                { Conflict(manifestPath, $"force-list overwrite ABORTED: '{name}' is never closed at indent {indent} in {xmlFile}"); continue; }
                lines.RemoveRange(at, last - at + 1);
                end -= last - at + 1;
                forcedAt = at;      // put the replacement back where the original was, so the
                                    // git diff of a forced overwrite is N insertions / N deletions
                                    // in one place instead of a move
                Console.WriteLine($"  FORCE {manifestPath} -> {xmlFile} (replacing {last - at + 1} line(s); {fHit.reason})");
                forced++;
            }

            // B5, the append half. Only reached when the name is absent — an occupied slot is
            // already the additive gate's business and keeps its own wording. The count is
            // memoised per container so that a manifest listing `9..28` in TEXT order (`10`
            // before `9`) is judged as a set, exactly as the binary side judges it.
            if (at < 0 && IsIndex(name, out int leafIdx))
            {
                string container = manifestPath[..manifestPath.LastIndexOf('/')];
                if (!xmlArrayBase.TryGetValue(container, out var ac))
                    xmlArrayBase[container] = ac = ArrayRange(ChildNames(lines, start, end, indent));
                if (ac is { } r0)
                {
                    int lo = r0.Min, hi = r0.Min + r0.Count - 1;
                    string? why = null;
                    if (leafIdx >= lo && leafIdx <= hi) why = $"slot {leafIdx} is already occupied; this is not an append";
                    else if (leafIdx < lo) why = $"slot {leafIdx} sits BELOW the array's first index {lo} — that is a PREPEND, not an append, and means the source numbers this container from a different origin than the target";
                    // T23: same running-state rule as the binary side — a slot this run already
                    // REFUSED is not going to arrive, so every later slot of that container is a
                    // hole, not an append. ponytail: no undo sweep here, because this side writes
                    // file-by-file as it goes and cannot take a write back; that leaves the
                    // out-of-order case (append at 10 refused at 9, in that manifest order)
                    // uncovered on the XML side alone. Add a stage-and-promote pass if a manifest
                    // ever needs it — no add-list today lists a container's slots out of order.
                    else for (int k = hi + 1; k < leafIdx && why == null; k++)
                        if (!(xmlSlots.TryGetValue(container, out var w) && w.Contains(k)))
                            why = $"slot {leafIdx} would leave a GAP — the array ends at {hi} and nothing in this manifest supplies slot {k}";
                        else if (RefusedSlots.TryGetValue(container, out var gone) && gone.Contains(k))
                            why = $"slot {leafIdx} would leave a GAP — the array ends at {hi}, this manifest lists slot {k}, and slot {k} was REFUSED earlier in this run. Every slot from {k} up has to land, or none of them may";
                    if (why != null)
                    {
                        Conflict(manifestPath, $"POSITIONAL ARRAY: '{container}' holds exactly the consecutive integers {lo}..{hi}, so its children are SLOTS, not identities. {why}.");
                        continue;
                    }
                    // NOTE: the binary side additionally refuses an append whose CONTENT already
                    // exists elsewhere in the array (a source that inserted earlier shifts every
                    // later slot). That comparison needs decoded nodes on both sides; this scan
                    // has text. Deny-list the row — one deny-list serves both subcommands.
                }
            }

            // Insert in sorted position purely so the git diff reads naturally — the server
            // looks nodes up by name, so position is never load-bearing. CompareOrdinal only
            // orders correctly for equal-length ids (true of zero-padded Item.wz ids, not of
            // ragged String.wz ones); when it misjudges, the node still lands somewhere valid.
            int insertAt = forcedAt >= 0 ? forcedAt : lines.FindIndex(start, end - start,
                l => NameAt(l, indent) is string n && string.CompareOrdinal(n, name) > 0);
            if (insertAt < 0) insertAt = end; // last child of this container

            // Fragment() emits its own CRLF line breaks, so split them back out before splicing.
            var frag = ser.Fragment(sp, new string(' ', indent), xmlFile).Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
            lines.InsertRange(insertAt, frag);
            if (!dry)
            {
                // H5: the XML side wrote file-by-file with no verification of any kind and no
                // exit 4, so a disk-full or an interrupt part way through left a half-applied
                // server tree that the tool reported as success. Read each file back and compare
                // it with what was meant to be written. (Recovery is still `git checkout -- wz/`;
                // that is why this is a check and not a staging rewrite.)
                string want = string.Join("\r\n", lines) + "\r\n";
                File.WriteAllText(xmlFile, want, new UTF8Encoding(false));
                if (File.ReadAllText(xmlFile) != want)
                { Console.Error.WriteLine($"  UNVERIFIED: {xmlFile} does not read back as written"); unverified++; }
            }
            added++;
            Console.WriteLine($"  ADD   {manifestPath} -> {xmlFile}:{insertAt + 1} ({frag.Length} lines)");
        }

        WriteConflicts(args[4], $"{wzName}: additive-only XML export{(dry ? " — DRY RUN, nothing was written" : "")}" +
            $" -> {xmlRoot}, {added} nodes {(dry ? "would be added" : "added")} ({forced} by force-list), {Conflicts.Count} refused");
        Console.WriteLine($"added {added} (forced {forced}), refused {Conflicts.Count}");
        if (unverified > 0)
        {
            Console.Error.WriteLine($"{unverified} file(s) did not read back as written. The server tree is HALF-APPLIED: git checkout -- wz/ and re-run.");
            return 4;
        }
        if (added == 0) { Console.Error.WriteLine("NOTHING WAS ADDED. Every requested row was refused — read conflicts.txt."); return 5; }
        return Conflicts.Count > 0 ? 3 : 0;
    }

    // The line range [i..j] occupied by the element opened at line i at this indent, or -1 if it
    // cannot be established. Used ONLY by the force-list overwrite, which deletes that range —
    // so getting it wrong deletes somebody else's node, and -1 (refuse) is the right answer to
    // any doubt at all.
    //
    // The single-line assumption is NOT safe in this tree: `wz/String.wz/Cash.img.xml` really
    // contains `<string name="desc" value="…` whose value carries an embedded newline, so a
    // self-closing element can span two lines and `EndsWith("/>")` misses it. Left alone, the
    // scan below would run past it to the NEXT sibling's closing tag and delete both. So the
    // walk stops on either of two things, whichever comes first:
    //   * a closing tag at exactly this indent  -> that is our element's end
    //   * another element OPENING at exactly this indent -> we ran past our element without
    //     finding its close, i.e. it was self-closing in a shape we did not recognise. Refuse.
    static int ElementEnd(List<string> lines, int i, int indent, int limit)
    {
        if (lines[i].TrimEnd().EndsWith("/>", StringComparison.Ordinal)) return i;
        string close = new string(' ', indent) + "</";
        for (int k = i + 1; k < limit; k++)
        {
            if (lines[k].StartsWith(close, StringComparison.Ordinal)) return k;
            if (NameAt(lines[k], indent) != null) return -1;   // next sibling; ours never closed
        }
        return -1;
    }

    // name of the element opened at EXACTLY this indent, or null (deeper/shallower lines,
    // closing tags and attribute continuation lines all return null, which is what makes
    // the range scans below see only the container's own children).
    static string? NameAt(string line, int indent)
    {
        if (line.Length <= indent || line[indent] != '<') return null;
        for (int k = 0; k < indent; k++) if (line[k] != ' ') return null;
        const string marker = " name=\"";
        int i = line.IndexOf(marker, StringComparison.Ordinal);
        if (i < 0) return null;
        i += marker.Length;
        int j = line.IndexOf('"', i);
        return j < 0 ? null : line[i..j];
    }

    // names of the elements opened at EXACTLY this indent inside [start,end) — the container's
    // own children, which is all the B5 gate needs to recognise a positional array in text.
    static IEnumerable<string> ChildNames(List<string> lines, int start, int end, int indent)
    {
        for (int i = start; i < end; i++) if (NameAt(lines[i], indent) is string n) yield return n;
    }

    static int FindChild(List<string> lines, int start, int end, int indent, string name) =>
        lines.FindIndex(start, end - start,
            l => string.Equals(NameAt(l, indent), name, StringComparison.OrdinalIgnoreCase));
}
