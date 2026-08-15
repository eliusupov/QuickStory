using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using MapleLib.WzLib;

// ponytail: single-purpose one-off diff tool. No CLI framework, no config file -
// paths and wz-file list are hardcoded below because this runs exactly once.

static class Program
{
    // wz files with per-entity node granularity one level *inside* the top .img
    // (String.wz / Quest.wz / Skill.wz store many entities as sub-properties of a
    // handful of category images, unlike Item/Map/Mob/Npc/Character which use one
    // .img per entity already visible at the shallow directory-tree level).
    static readonly HashSet<string> ExpandOneLevel = new(StringComparer.OrdinalIgnoreCase)
    {
        // String/Quest/Skill: many entities are sub-properties of a handful of
        // category images. Item: category images are "bucket" images holding up
        // to 10,000 item ids each (e.g. Item.wz/Consume/0200.img holds ids
        // 02000000-02009999) - confirmed empirically: shallow diff found only 2
        // new Item.wz nodes when the 92-manifest lists 412 new items. Etc: the
        // MakeCharInfo.img Evan block (audit finding #5) is a new sub-property of
        // an existing image, invisible at the shallow level. UI: SkillEx/
        // SkillMacroEx already exist as containers in both trees; v84 enlarges
        // their contents rather than adding new top-level images.
        "String.wz", "Quest.wz", "Skill.wz", "Item.wz", "Etc.wz", "UI.wz"
    };

    static readonly string[] WzNames =
    {
        "Character.wz", "Etc.wz", "Item.wz", "Map.wz", "Mob.wz",
        "Npc.wz", "Quest.wz", "Skill.wz", "String.wz", "UI.wz", "Reactor.wz"
    };

    record NodeSet(HashSet<string> Paths, Dictionary<string, long> ImageSizes);

    static (WzFile? file, WzMapleVersion? ver, string err) TryOpen(string path)
    {
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile f = new WzFile(path, -1, ver);
            WzFileParseStatus status;
            try
            {
                status = f.ParseWzFile();
            }
            catch (Exception ex)
            {
                f.Dispose();
                return (null, null, $"exception under {ver}: {ex.Message}");
            }
            if (status == WzFileParseStatus.Success)
                return (f, ver, "");
            f.Dispose();
        }
        return (null, null, "no encryption version (GMS/BMS/EMS) produced a valid parse");
    }

    static NodeSet Walk(WzFile file, string wzName, bool expandOneLevel)
    {
        var paths = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var sizes = new Dictionary<string, long>(StringComparer.OrdinalIgnoreCase);

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

                if (expandOneLevel)
                {
                    try
                    {
                        var props = img.WzProperties; // triggers ParseImage()
                        if (props != null)
                        {
                            foreach (var prop in props)
                                paths.Add(p + "/" + prop.Name);
                        }
                    }
                    catch (Exception ex)
                    {
                        Console.Error.WriteLine($"  [warn] failed to expand {wzName}{p}: {ex.Message}");
                    }
                }
            }
        }

        WalkDir(file.WzDirectory, wzName);
        return new NodeSet(paths, sizes);
    }

    static NodeSet? OpenAndWalk(string dir, string wzName, out string status)
    {
        string path = Path.Combine(dir, wzName);
        if (!File.Exists(path))
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
            var set = Walk(file, wzName, ExpandOneLevel.Contains(wzName));
            status = $"OK ({ver}, {set.Paths.Count} nodes)";
            return set;
        }
        finally
        {
            file.Dispose();
        }
    }

    static void WriteList(string outFile, IEnumerable<string> paths)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(outFile)!);
        File.WriteAllLines(outFile, paths.OrderBy(p => p, StringComparer.OrdinalIgnoreCase));
    }

    static void Main(string[] args)
    {
        string v83Dir = @"D:\games\MapleStory\Server\porting-resources\wz-data\v83-stock";
        string v84Dir = @"D:\games\MapleStory\Server\porting-resources\wz-data\v84";
        string liveDir = @"D:\games\MapleStory";
        string outRoot = args.Length > 0 ? args[0] : @"D:\games\MapleStory\Server\Cosmic\.claude\worktrees\evan-dualblade\docs\wz-baseline";

        var summary = new List<string>();
        summary.Add("# WZ baseline diff — machine-generated summary");
        summary.Add("");
        summary.Add($"Generated {DateTime.Now:yyyy-MM-dd HH:mm:ss}");
        summary.Add("");
        summary.Add("| wz | v83-stock | v84 | live client | add (v84-v83) | protect (live - (v83 u v84)) | add bytes (BlockSize sum) | protect bytes (BlockSize sum) |");
        summary.Add("|---|---|---|---|---|---|---|---|");

        foreach (var wz in WzNames)
        {
            Console.WriteLine($"=== {wz} ===");

            var set83 = OpenAndWalk(v83Dir, wz, out var st83);
            Console.WriteLine($"  v83-stock: {st83}");
            var set84 = OpenAndWalk(v84Dir, wz, out var st84);
            Console.WriteLine($"  v84:       {st84}");
            var setLive = OpenAndWalk(liveDir, wz, out var stLive);
            Console.WriteLine($"  live:      {stLive}");

            HashSet<string> addPaths = new(StringComparer.OrdinalIgnoreCase);
            long addBytes = 0;
            if (set83 != null && set84 != null)
            {
                addPaths = new HashSet<string>(set84.Paths, StringComparer.OrdinalIgnoreCase);
                addPaths.ExceptWith(set83.Paths);
                addBytes = addPaths.Sum(p => set84.ImageSizes.TryGetValue(p, out var s) ? s : 0);
                WriteList(Path.Combine(outRoot, "add-list", wz.Replace(".wz", "") + ".txt"), addPaths);
            }

            HashSet<string> protectPaths = new(StringComparer.OrdinalIgnoreCase);
            long protectBytes = 0;
            if (setLive != null)
            {
                var union = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
                if (set83 != null) union.UnionWith(set83.Paths);
                if (set84 != null) union.UnionWith(set84.Paths);
                protectPaths = new HashSet<string>(setLive.Paths, StringComparer.OrdinalIgnoreCase);
                protectPaths.ExceptWith(union);
                protectBytes = protectPaths.Sum(p => setLive.ImageSizes.TryGetValue(p, out var s) ? s : 0);
                WriteList(Path.Combine(outRoot, "protect-list", wz.Replace(".wz", "") + ".txt"), protectPaths);
            }

            summary.Add($"| {wz} | {(set83 == null ? st83 : set83.Paths.Count.ToString())} | {(set84 == null ? st84 : set84.Paths.Count.ToString())} | {(setLive == null ? stLive : setLive.Paths.Count.ToString())} | {addPaths.Count} | {protectPaths.Count} | {addBytes:N0} | {protectBytes:N0} |");

            Console.WriteLine($"  add: {addPaths.Count} nodes ({addBytes:N0} bytes), protect: {protectPaths.Count} nodes ({protectBytes:N0} bytes)");
        }

        File.WriteAllLines(Path.Combine(outRoot, "SUMMARY.md"), summary);
        Console.WriteLine("Done. Summary at " + Path.Combine(outRoot, "SUMMARY.md"));

        // ponytail: one-off follow-up audit for the Map.wz v83->v84 node-count drop
        // (orchestrator asked: genuine removal, structural repack, or damaged copy?).
        // Reuses OpenAndWalk rather than a new tool.
        var absentMapIds = MapAudit(v83Dir, v84Dir, outRoot);
        NpcSpotCheck(v83Dir);
        MapNameLookup(v83Dir, outRoot, absentMapIds);
    }

    // ponytail: identify what the "missing" map ids actually are, by name, so we can
    // tell "seasonal event maps genuinely dropped" from "damaged archive". Map.img is
    // organized by world/region name (maple, event, jp, singapore...), not by leading
    // digit, so this recursively searches for id-shaped keys anywhere under it rather
    // than guessing the region layout.
    static void MapNameLookup(string v83Dir, string outRoot, List<string> absentPaths)
    {
        Console.WriteLine("=== missing-map name lookup ===");
        var wantedIds = absentPaths
            .Select(p => Path.GetFileNameWithoutExtension(p[(p.LastIndexOf('/') + 1)..]))
            .ToHashSet(StringComparer.Ordinal);

        string path = Path.Combine(v83Dir, "String.wz");
        var (file, ver, err) = TryOpen(path);
        if (file == null) { Console.WriteLine("  could not open v83 String.wz: " + err); return; }

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
        file.Dispose();
        Console.WriteLine($"  {found.Count}/{wantedIds.Count} names resolved, wrote " + Path.Combine(outRoot, "map-missing-names-v83.txt"));
    }

    static List<string> MapAudit(string v83Dir, string v84Dir, string outRoot)
    {
        Console.WriteLine("=== Map.wz audit ===");
        var s83 = OpenAndWalk(v83Dir, "Map.wz", out var st83);
        var s84 = OpenAndWalk(v84Dir, "Map.wz", out var st84);
        if (s83 == null || s84 == null) { Console.WriteLine("  could not open both trees"); return new(); }

        var v84Sizes = s84.ImageSizes; // image paths only, no directory placeholders
        var v84Leaves = new HashSet<string>(v84Sizes.Keys.Select(p => p[(p.LastIndexOf('/') + 1)..]), StringComparer.OrdinalIgnoreCase);

        var missing = s83.ImageSizes.Keys.Where(p => !v84Sizes.ContainsKey(p)).ToList();
        int relocated = 0, absent = 0;
        var byContainer = new Dictionary<string, int>(StringComparer.OrdinalIgnoreCase);
        var absentSamples = new List<string>();
        foreach (var p in missing)
        {
            string leaf = p[(p.LastIndexOf('/') + 1)..];
            bool foundElsewhere = v84Leaves.Contains(leaf);
            if (foundElsewhere) relocated++; else { absent++; if (absentSamples.Count < 20) absentSamples.Add(p); }

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

    static void NpcSpotCheck(string v83Dir)
    {
        Console.WriteLine("=== Npc.wz 9000071 (Keroben) spot check ===");
        var s83 = OpenAndWalk(v83Dir, "Npc.wz", out var st83);
        if (s83 == null) { Console.WriteLine("  could not open v83 Npc.wz"); return; }
        bool found = s83.Paths.Contains("Npc.wz/9000071.img");
        Console.WriteLine($"  Npc.wz/9000071.img present in v83-stock: {found}");
    }
}
