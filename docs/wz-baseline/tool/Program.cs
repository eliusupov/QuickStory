using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using MapleLib.WzLib;

// ponytail: single-purpose diff tool. No CLI framework, no config file - the three
// input roots and the output root come from args (positional), everything else is
// derived from what is on disk.
//
//   WzDump [outRoot] [v83Dir] [v84Dir] [liveDir]
//
// "v83Dir/v84Dir/liveDir" are just names for "baseline / new / local"; to verify a
// merge, pass (pre-merge-tree, post-merge-tree) as v83Dir/v84Dir and read
// modified-list/*.txt - that is the change-proof mode, no extra code.

static class Program
{
    // ponytail: every image is expanded EXPAND_DEPTH levels, always, in every file.
    // The old per-file allowlist was a guess about which files are "bucket images" and
    // it got Map.wz wrong; one level then turned out to be too shallow for the buckets
    // that nest (String.wz/Eqp.img/Eqp/<category>/<id>, Etc.img/Etc/<id>,
    // Map.img/<region>/<mapid>, Skill.wz/<job>.img/skill/<id>). You cannot know how deep
    // an image's entities sit without parsing it, so parse them all and unparse right
    // after to keep memory flat.
    //
    // Why 3 and not "all the way down": 3 is where entity ids bottom out in this era —
    // the deepest is Eqp.img/Eqp/<category>/<id>. Below 3 you are looking at animation
    // frames, foothold vertices and canvas origins, which are intra-image detail that
    // modified-list/*.txt (BlockSize) already reports and that no manifest row should
    // ever be a copy root for. Ceiling: an id nested 4+ deep would be invisible again;
    // the upgrade path is to raise this constant and re-measure the cost.
    const int EXPAND_DEPTH = 3;

    record NodeSet(HashSet<string> Paths, Dictionary<string, long> ImageSizes);

    // H1: image parse failures are hard errors, collected here and printed in SUMMARY.md.
    static readonly List<string> Failures = new();
    static long ImagesParsed = 0;

    static (WzFile? file, WzMapleVersion? ver, string err) TryOpen(string path)
    {
        var errs = new List<string>();
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            WzFileParseStatus status;
            try
            {
                f = new WzFile(path, -1, ver);
                status = f.ParseWzFile();
            }
            catch (Exception ex)
            {
                // M1: a wrong IV produces InvalidDataException (truncated read / bad offset).
                // That is a failed candidate, not a failed file - try the next IV.
                f?.Dispose();
                errs.Add($"{ver}: {ex.GetType().Name} {ex.Message}");
                continue;
            }
            if (status == WzFileParseStatus.Success)
                return (f, ver, "");
            f.Dispose();
            errs.Add($"{ver}: {status}");
        }
        return (null, null, "no encryption version parsed: " + string.Join(" | ", errs));
    }

    static NodeSet Walk(WzFile file, string wzName, string tree)
    {
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var sizes = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);

        void AddProps(MapleLib.WzLib.WzPropertyCollection? props, string prefix, int depth)
        {
            if (props == null) return;
            foreach (var prop in props)
            {
                string p = prefix + "/" + prop.Name;
                paths.Add(p);
                if (depth < EXPAND_DEPTH) AddProps(prop.WzProperties, p, depth + 1);
            }
        }

        void WalkDir(WzDirectory dir, string prefix)
        {
            foreach (var sub in dir.WzDirectories)
            {
                string p = prefix + "/" + sub.Name;
                paths.Add(p);
                WalkDir(sub, p);
            }
            foreach (var img in dir.WzImages)
            {
                string p = prefix + "/" + img.Name;
                paths.Add(p);
                sizes[p] = img.BlockSize;

                // H1: ParseImage() signals failure by returning false (unknown header
                // byte / bad "Property" marker / lua-in-non-lua image) WITHOUT throwing,
                // and the WzProperties getter discards that bool and hands back an empty
                // non-null collection. Reading the bool is the whole fix.
                bool ok;
                string err = "returned false (unknown header byte / bad Property marker / lua image)";
                try { ok = img.ParseImage(); }
                catch (Exception ex) { ok = false; err = $"{ex.GetType().Name}: {ex.Message}"; }

                ImagesParsed++;
                if (!ok)
                {
                    Failures.Add($"{tree}\t{p}\t{err}");
                    Console.Error.WriteLine($"  [PARSE-FAIL] {tree} {p}: {err}");
                    img.UnparseImage(); // a partial parse still allocated properties
                    continue;
                }
                AddProps(img.WzProperties, p, 1);
                img.UnparseImage(); // keep memory flat across ~60k images
            }
        }

        WalkDir(file.WzDirectory, wzName);
        return new NodeSet(paths, sizes);
    }

    // dirs: a tree may be spread over several directories (the v83 Reactor.wz baseline
    // lives outside v83-stock/); first directory that has the file wins.
    static NodeSet? OpenAndWalk(string[] dirs, string wzName, string tree, out string status)
    {
        string? path = dirs.Select(d => Path.Combine(d, wzName)).FirstOrDefault(File.Exists);
        if (path == null)
        {
            status = "MISSING";
            return null;
        }
        var (file, ver, err) = TryOpen(path);
        if (file == null)
        {
            status = "OPEN-FAILED: " + err;
            return null;
        }
        try
        {
            int before = Failures.Count;
            var set = Walk(file, wzName, tree);
            int failed = Failures.Count - before;
            status = $"{set.Paths.Count}" + (failed > 0 ? $" ({failed} PARSE-FAIL)" : "");
            return set;
        }
        finally
        {
            file.Dispose();
        }
    }

    // M3: manifests are copy instructions. Collapse any path whose parent is also listed
    // so every listed path is a copy root and nobody double-copies an image's children.
    static List<string> Collapse(HashSet<string> paths) =>
        paths.Where(p => { int i = p.LastIndexOf('/'); return i < 0 || !paths.Contains(p[..i]); })
             .OrderBy(p => p, StringComparer.OrdinalIgnoreCase).ToList();

    static void WriteList(string outFile, string header, IEnumerable<string> lines, bool copyRoots = true)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(outFile)!);
        var head = new List<string> { "# " + header };
        if (copyRoots)
        {
            head.Add("# each path is a copy root: no listed path is an ancestor of another, so copying");
            head.Add("# a listed path already covers everything under it. Do not re-copy children.");
        }
        head.Add("");
        File.WriteAllLines(outFile, head.Concat(lines));
    }

    // H3: paths present in both trees whose WzImage.BlockSize differs = edited image.
    // Presence-only diffing cannot see edits (a destructive overwrite preserves paths).
    static List<string> ModifiedImages(NodeSet? a, NodeSet? b) =>
        a == null || b == null
            ? new List<string>()
            : a.ImageSizes.Where(kv => b.ImageSizes.TryGetValue(kv.Key, out var s) && s != kv.Value)
               .Select(kv => $"{kv.Key}\t{kv.Value}\t{b.ImageSizes[kv.Key]}")
               .OrderBy(s => s, StringComparer.OrdinalIgnoreCase).ToList();

    static void Main(string[] args)
    {
        // M7: all four roots overridable. Defaults are this machine's layout.
        // A root may be several ';'-separated directories (v83's Reactor.wz baseline was
        // extracted outside v83-stock/ and that tree is owned by another agent).
        string[] Root(int i, string def) => (args.Length > i ? args[i] : def).Split(';', StringSplitOptions.RemoveEmptyEntries);
        string outRoot = args.Length > 0 ? args[0] : @"D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\wz-baseline";
        string[] v83Dir = Root(1, @"D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock;D:\games\MapleStory\Server\porting-resources\wz-data\v83-reactor");
        string[] v84Dir = Root(2, @"D:\games\MapleStory\Server\porting-resources\wz-data\v84");
        string[] liveDir = Root(3, @"D:\games\MapleStory");

        // H5: every .wz present in ANY tree gets a row, so an unbaselined file
        // (TamingMob/Sound/Effect/Morph/Base/List/...) is visible instead of absent.
        var wzNames = v83Dir.Concat(v84Dir).Concat(liveDir)
            .Where(Directory.Exists)
            .SelectMany(d => Directory.GetFiles(d, "*.wz").Select(Path.GetFileName))
            .Where(n => n != null).Select(n => n!)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .OrderBy(n => n, StringComparer.OrdinalIgnoreCase)
            .ToArray();

        var summary = new List<string>();
        summary.Add("# WZ baseline diff — machine-generated summary");
        summary.Add("");
        summary.Add($"Generated {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
        summary.Add($"Roots: v83=`{string.Join(";", v83Dir)}` v84=`{string.Join(";", v84Dir)}` live=`{string.Join(";", liveDir)}`");
        summary.Add("");
        summary.Add("`—` = not measurable (a required tree lacks this file). It never means zero.");
        summary.Add($"Node counts are paths (directories + images + {EXPAND_DEPTH} levels of sub-properties).");
        summary.Add("");
        summary.Add("| wz | v83-stock | v84 | live client | add (v84−v83) | removed (v83−v84) | protect (live − (v83 ∪ v84)) | modified v83→v84 | modified v83→live | add bytes | protect bytes |");
        summary.Add("|---|---|---|---|---|---|---|---|---|---|---|");

        var mapSets = new Dictionary<string, NodeSet?>(StringComparer.OrdinalIgnoreCase);

        foreach (var wz in wzNames)
        {
            Console.WriteLine($"=== {wz} ===");
            string stem = Path.GetFileNameWithoutExtension(wz);

            var set83 = OpenAndWalk(v83Dir, wz, "v83", out var st83);
            Console.WriteLine($"  v83-stock: {st83}");
            var set84 = OpenAndWalk(v84Dir, wz, "v84", out var st84);
            Console.WriteLine($"  v84:       {st84}");
            var setLive = OpenAndWalk(liveDir, wz, "live", out var stLive);
            Console.WriteLine($"  live:      {stLive}");
            if (wz.Equals("Map.wz", StringComparison.OrdinalIgnoreCase))
            {
                mapSets["v83"] = set83; mapSets["v84"] = set84;
            }

            // ---- add-list: needs BOTH stock trees. H4: no baseline => "—", not 0.
            string addCell = "—", addBytesCell = "—";
            if (set83 != null && set84 != null)
            {
                var addPaths = new HashSet<string>(set84.Paths, StringComparer.OrdinalIgnoreCase);
                addPaths.ExceptWith(set83.Paths);
                long addBytes = addPaths.Sum(p => set84.ImageSizes.TryGetValue(p, out var s) ? s : 0);
                var rows = Collapse(addPaths);
                WriteList(Path.Combine(outRoot, "add-list", stem + ".txt"),
                    $"{wz}: nodes present in v84 and absent from v83-stock ({rows.Count} copy roots, {addPaths.Count} paths)", rows);
                addCell = rows.Count.ToString();
                addBytesCell = addBytes.ToString("N0");
            }

            // ---- removed-list: v84 genuinely deletes content (832 Map.wz maps, confirmed
            // against maplestory.io). Never wholesale-swap a wz file; this is what you lose.
            string removedCell = "—";
            if (set83 != null && set84 != null)
            {
                var gone = new HashSet<string>(set83.Paths, StringComparer.OrdinalIgnoreCase);
                gone.ExceptWith(set84.Paths);
                var rows = Collapse(gone);
                WriteList(Path.Combine(outRoot, "removed-list", stem + ".txt"),
                    $"{wz}: nodes present in v83-stock and absent from v84 — deleted by the patch ({rows.Count} roots, {gone.Count} paths). A wholesale file swap destroys any of these the live client still has. Each row is a root: everything under it went too.", rows, copyRoots: false);
                removedCell = rows.Count.ToString();
            }

            // ---- protect-list: live minus both stocks. Without a v83 term it is weak.
            string protectCell = "—", protectBytesCell = "—";
            if (setLive != null && (set83 != null || set84 != null))
            {
                var union = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                if (set83 != null) union.UnionWith(set83.Paths);
                if (set84 != null) union.UnionWith(set84.Paths);
                var protectPaths = new HashSet<string>(setLive.Paths, StringComparer.OrdinalIgnoreCase);
                protectPaths.ExceptWith(union);
                long protectBytes = protectPaths.Sum(p => setLive.ImageSizes.TryGetValue(p, out var s) ? s : 0);
                var rows = Collapse(protectPaths);
                string caveat = set83 == null ? " WARNING: no v83 baseline — entries may be stock v83 content, not custom." : "";
                WriteList(Path.Combine(outRoot, "protect-list", stem + ".txt"),
                    $"{wz}: nodes in the live client present in neither stock tree ({rows.Count} copy roots).{caveat}", rows);
                protectCell = rows.Count + (set83 == null ? " (no v83)" : "");
                protectBytesCell = protectBytes.ToString("N0");
            }

            // ---- modified-list: same path, different BlockSize. H3.
            string modV84 = "—", modLive = "—";
            var m84 = ModifiedImages(set83, set84);
            if (set83 != null && set84 != null)
            {
                WriteList(Path.Combine(outRoot, "modified-list", stem + ".txt"),
                    $"{wz}: images present in BOTH v83-stock and v84 whose WzImage.BlockSize differs (edited, not added). Columns: path, v83 bytes, v84 bytes.", m84, copyRoots: false);
                modV84 = m84.Count.ToString();
            }
            var mLive = ModifiedImages(set83, setLive);
            if (set83 != null && setLive != null)
            {
                WriteList(Path.Combine(outRoot, "modified-list", stem + ".live.txt"),
                    $"{wz}: images present in BOTH v83-stock and the live client whose BlockSize differs (live-side edits — treat as custom content, do not overwrite). Columns: path, v83 bytes, live bytes.", mLive, copyRoots: false);
                modLive = mLive.Count.ToString();
            }

            // status strings can contain '|' (parser errors) - don't break the table row
            string Cell(NodeSet? s, string st) => s == null ? st.Replace("|", "/") : s.Paths.Count.ToString("N0");
            summary.Add($"| {wz} | {Cell(set83, st83)} | {Cell(set84, st84)} | {Cell(setLive, stLive)} | {addCell} | {removedCell} | {protectCell} | {modV84} | {modLive} | {addBytesCell} | {protectBytesCell} |");
            Console.WriteLine($"  add: {addCell}, removed: {removedCell}, protect: {protectCell}, modified v84: {modV84}, modified live: {modLive}");
        }

        // H1: a zero-failure run must be provable, not assumed.
        summary.Add("");
        summary.Add("## image parse status");
        summary.Add("");
        summary.Add($"{ImagesParsed:N0} images parsed, **{Failures.Count} parse failures**.");
        if (Failures.Count > 0)
        {
            summary.Add("");
            summary.Add("A failed image contributes zero sub-nodes: in v84 it silently drops content,");
            summary.Add("in live it leaves custom content unprotected, in v83 it manufactures false adds.");
            summary.Add("Every manifest touching these files is suspect until they parse.");
            summary.Add("");
            summary.Add("| tree | path | error |");
            summary.Add("|---|---|---|");
            foreach (var f in Failures)
            {
                var c = f.Split('\t');
                summary.Add($"| {c[0]} | `{c[1]}` | {c[2]} |");
            }
        }

        File.WriteAllLines(Path.Combine(outRoot, "SUMMARY.md"), summary);
        Console.WriteLine($"Done. {ImagesParsed:N0} images parsed, {Failures.Count} parse failures. Summary at " + Path.Combine(outRoot, "SUMMARY.md"));

        // ponytail: one-off follow-up audit for the Map.wz v83->v84 node-count drop
        // (orchestrator asked: genuine removal, structural repack, or damaged copy?).
        // Reuses the sets already walked above rather than re-opening 600 MB of Map.wz.
        var absentMapIds = MapAudit(mapSets.GetValueOrDefault("v83"), mapSets.GetValueOrDefault("v84"), outRoot);
        if (absentMapIds.Count > 0) MapNameLookup(v83Dir, outRoot, absentMapIds);
    }

    // ponytail: identify what the "missing" map ids actually are, by name, so we can
    // tell "seasonal event maps genuinely dropped" from "damaged archive". Map.img is
    // organized by world/region name (maple, event, jp, singapore...), not by leading
    // digit, so this recursively searches for id-shaped keys anywhere under it rather
    // than guessing the region layout.
    static void MapNameLookup(string[] v83Dir, string outRoot, List<string> absentPaths)
    {
        Console.WriteLine("=== deleted-map name lookup ===");
        var wantedIds = absentPaths
            .Select(p => Path.GetFileNameWithoutExtension(p[(p.LastIndexOf('/') + 1)..]))
            // OrdinalIgnoreCase like every other set in this file: WZ node lookup is
            // case-insensitive, and a case-differing id must not read as "not found".
            .ToHashSet(StringComparer.OrdinalIgnoreCase);

        string? path = v83Dir.Select(d => Path.Combine(d, "String.wz")).FirstOrDefault(File.Exists);
        if (path == null) { Console.WriteLine("  no v83 String.wz"); return; }
        var (file, ver, err) = TryOpen(path);
        if (file == null) { Console.WriteLine("  could not open v83 String.wz: " + err); return; }
        using var _ = file;

        var mapImg = file.WzDirectory.WzImages.FirstOrDefault(i => i.Name.Equals("Map.img", StringComparison.OrdinalIgnoreCase));
        var found = new Dictionary<string, (string region, string street, string name)>();
        if (mapImg != null)
        {
            void Walk(MapleLib.WzLib.WzPropertyCollection props, string region, int depth)
            {
                if (depth > 6) return;
                foreach (var p in props)
                {
                    if (wantedIds.Contains(p.Name) && p.WzProperties != null)
                    {
                        string name = p.WzProperties.FirstOrDefault(c => c.Name.Equals("mapName", StringComparison.OrdinalIgnoreCase))?.ToString() ?? "";
                        string street = p.WzProperties.FirstOrDefault(c => c.Name.Equals("streetName", StringComparison.OrdinalIgnoreCase))?.ToString() ?? "";
                        found[p.Name] = (region, street, name);
                    }
                    if (p.WzProperties != null)
                        Walk(p.WzProperties, region == "" ? p.Name : region, depth + 1);
                }
            }
            // H1 again, in the one place it survived the rewrite: reading .WzProperties
            // without checking ParseImage()'s return value turns a failed parse into a
            // silent "0 names found". Same defect, same fix — read the bool.
            if (!mapImg.ParseImage())
                Console.Error.WriteLine("  [PARSE-FAIL] v83 String.wz/Map.img — name lookup skipped, ids will read as unresolved");
            else
                Walk(mapImg.WzProperties, "", 0);
        }

        var lines = new List<string>
        {
            "# missing map id -> name lookup (from v83-stock String.wz/Map.img, recursive)",
            "",
            $"{wantedIds.Count} ids searched for, {found.Count} names found",
            ""
        };
        foreach (var id in wantedIds.OrderBy(x => x, StringComparer.Ordinal))
        {
            lines.Add(found.TryGetValue(id, out var v)
                ? $"{id}\tregion={v.region}\tstreet={v.street}\tname={v.name}"
                : $"{id}\t(no String.wz entry found)");
        }
        File.WriteAllLines(Path.Combine(outRoot, "map-missing-names-v83.txt"), lines);
        Console.WriteLine($"  {found.Count}/{wantedIds.Count} names resolved, wrote " + Path.Combine(outRoot, "map-missing-names-v83.txt"));
    }

    static List<string> MapAudit(NodeSet? s83, NodeSet? s84, string outRoot)
    {
        Console.WriteLine("=== Map.wz audit ===");
        if (s83 == null || s84 == null) { Console.WriteLine("  need both stock Map.wz trees"); return new(); }

        var v84Sizes = s84.ImageSizes; // image paths only, no directory placeholders
        var v84Leaves = new HashSet<string>(v84Sizes.Keys.Select(p => p[(p.LastIndexOf('/') + 1)..]), StringComparer.OrdinalIgnoreCase);

        var missing = s83.ImageSizes.Keys.Where(p => !v84Sizes.ContainsKey(p)).ToList();
        int relocated = 0, absent = 0;
        var byContainer = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        foreach (var p in missing)
        {
            string leaf = p[(p.LastIndexOf('/') + 1)..];
            if (v84Leaves.Contains(leaf)) relocated++; else absent++;

            // group by path segment right after "Map.wz/" (Map, Back, Obj, Tile, WorldMap...),
            // and one level further for Map/MapN buckets.
            var parts = p.Split('/');
            string container = parts.Length > 2 ? string.Join("/", parts.Skip(1).Take(2)) : parts.ElementAtOrDefault(1) ?? "?";
            byContainer[container] = byContainer.GetValueOrDefault(container) + 1;
        }

        Console.WriteLine($"  v83 image nodes: {s83.ImageSizes.Count}, v84 image nodes: {v84Sizes.Count}");
        Console.WriteLine($"  missing from v84 (by exact path): {missing.Count}");
        Console.WriteLine($"  -> same leaf name found somewhere else in v84 (relocated/structural): {relocated}");
        Console.WriteLine($"  -> leaf name not found anywhere in v84 (genuinely absent): {absent}");

        var lines = new List<string>
        {
            "# Map.wz v83->v84 node-count drop audit",
            "",
            $"v83 image nodes: {s83.ImageSizes.Count}, v84 image nodes: {v84Sizes.Count}",
            $"missing from v84 by exact path: {missing.Count}",
            $"  relocated (leaf name exists elsewhere in v84): {relocated}",
            $"  genuinely absent (leaf name not found anywhere in v84): {absent}",
            "",
            "## missing paths grouped by container (top 30 by count)",
            ""
        };
        foreach (var kv in byContainer.OrderByDescending(kv => kv.Value).Take(30))
            lines.Add($"{kv.Value,6}  {kv.Key}");
        lines.Add("");
        lines.Add("## all genuinely-absent paths");
        var absentPaths = missing.Where(p => !v84Leaves.Contains(p[(p.LastIndexOf('/') + 1)..])).OrderBy(p => p, StringComparer.OrdinalIgnoreCase).ToList();
        lines.AddRange(absentPaths);
        File.WriteAllLines(Path.Combine(outRoot, "map-v83-only-audit.txt"), lines);
        Console.WriteLine("  wrote " + Path.Combine(outRoot, "map-v83-only-audit.txt"));
        return absentPaths;
    }
}
