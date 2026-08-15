using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.Serializer;

// WzMerge — additive-only node importer for the v83 -> v84 upgrade.
//
//   WzMerge dump  <wz> <path/under/wz> [depth]
//   WzMerge merge <sourceWz> <targetWz> <outWz>  <pathsFile> <conflictsTxt>
//   WzMerge xml   <sourceWz> <xmlRoot>           <pathsFile> <conflictsTxt>
//
// Paths in <pathsFile> are manifest lines, exactly as docs/wz-baseline/add-list/*.txt
// writes them: "Item.wz/Consume/0200.img/02001500". '#' and blanks ignored.
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
                default: Usage(); return 2;
            }
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine("FAILED: " + ex.Message);
            return 1;
        }
    }

    static void Usage() => Console.Error.WriteLine(
        "WzMerge dump  <wz> <path/under/wz> [depth]\n" +
        "WzMerge merge <sourceWz> <targetWz> <outWz> <pathsFile> <conflictsTxt>\n" +
        "WzMerge xml   <sourceWz> <xmlRoot> <pathsFile> <conflictsTxt>");

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

    static List<string> ReadPaths(string file) =>
        File.ReadAllLines(file)
            .Select(l => l.Trim())
            .Where(l => l.Length > 0 && !l.StartsWith('#'))
            .ToList();

    static void Conflict(string path, string reason)
    {
        Conflicts.Add($"{path}\t{reason}");
        Console.WriteLine($"  SKIP  {path}  ({reason})");
    }

    static void WriteConflicts(string outFile, string header)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outFile))!);
        var lines = new List<string>
        {
            "# " + header,
            "# Every row is a v84 node this merge REFUSED to write because the path already",
            "# existed in the target. Additive-only is enforced in the write path, so this file",
            "# is the exhaustive list of v84 changes that were dropped. Read it before shipping:",
            "# a v84 EDIT to an existing node (renamed mob, portal added to an existing map)",
            "# looks exactly like this and is silently lost unless someone decides otherwise.",
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

        var segs = args[2].Split('/', StringSplitOptions.RemoveEmptyEntries);
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
        IEnumerable<WzObject> kids = obj switch
        {
            WzDirectory d => d.WzDirectories.Cast<WzObject>().Concat(d.WzImages),
            WzImage i => i.WzProperties,
            WzImageProperty p => (IEnumerable<WzObject>?)p.WzProperties ?? Array.Empty<WzObject>(),
            _ => Array.Empty<WzObject>()
        };
        foreach (var k in kids) Print(k, ind + "  ", depth - 1);
    }

    // ---------- merge (client binary .wz) ----------

    static int Merge(string[] args)
    {
        if (args.Length < 6) { Usage(); return 2; }
        string srcPath = args[1], tgtPath = args[2], outPath = args[3];
        var paths = ReadPaths(args[4]);

        var (src, srcVer) = Open(srcPath);
        using var _s = src;
        var (tgt, tgtVer) = Open(tgtPath);
        using var _t = tgt;

        string wzName = Path.GetFileName(tgtPath);
        Console.WriteLine($"source {srcPath}  iv={srcVer} patchVersion={src.Version}");
        Console.WriteLine($"target {tgtPath}  iv={tgtVer} patchVersion={tgt.Version}");
        Console.WriteLine($"{paths.Count} paths requested");

        int added = 0;
        foreach (var manifestPath in paths)
        {
            var rel = Rel(manifestPath, wzName);

            // ADDITIVE-ONLY GATE. Nothing below this can overwrite: the only mutation
            // performed is AddProperty/AddImage onto a parent that does not already
            // hold this name.
            if (Resolve(tgt, rel, rel.Length) != null) { Conflict(manifestPath, "already exists in target"); continue; }

            var srcObj = Resolve(src, rel, rel.Length);
            if (srcObj == null) { Conflict(manifestPath, "MISSING IN SOURCE — manifest is stale"); continue; }

            var parent = Resolve(tgt, rel, rel.Length - 1);
            if (parent == null) { Conflict(manifestPath, "parent path absent in target — import the parent first"); continue; }

            switch (parent, srcObj)
            {
                case (WzDirectory pd, WzImage si):
                    pd.AddImage(si.DeepClone());
                    break;
                // whole new sub-directory, e.g. v84's Skill.wz/Dragon (Evan's dragon
                // animations). ponytail: DeepClone materialises every image beneath it, so
                // this is memory-bound — Skill.wz/Dragon is tens of MB. Fine so far; if a
                // bigger directory ever OOMs, expand the manifest to per-image rows instead.
                case (WzDirectory pd2, WzDirectory sd):
                    pd2.AddDirectory(sd.DeepClone());
                    break;
                // The AddProperty below only lands because the gate above already walked
                // through `WzImage img => img[s]`, whose indexer parses the image on demand.
                // Short-circuit or reorder that gate and adds are silently dropped onto an
                // unparsed image. The coupling is real; do not "optimise" the gate away.
                case (WzImage pi, WzImageProperty sp):
                    pi.AddProperty(sp.DeepClone());
                    pi.Changed = true;
                    break;
                case (WzImageProperty pp, WzImageProperty sp2) when pp is MapleLib.WzLib.IPropertyContainer pc:
                    pc.AddProperty(sp2.DeepClone());
                    if (pp.ParentImage != null) pp.ParentImage.Changed = true;
                    break;
                default:
                    Conflict(manifestPath, $"unsupported shape: parent={parent.GetType().Name} source={srcObj.GetType().Name}");
                    continue;
            }
            added++;
            Console.WriteLine($"  ADD   {manifestPath}");
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
        bool dry = outPath == "-";
        if (dry)
        {
            Console.WriteLine("dry run: not saving");
        }
        else
        {
            Console.WriteLine($"saving {outPath} at iv={tgt.MapleVersion} patchVersion={tgt.Version} (inherited from target)");
            Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outPath))!);
            tgt.SaveToDisk(outPath);
        }

        WriteConflicts(args[5], $"{wzName}: additive-only merge{(dry ? " — DRY RUN, nothing was written" : "")}," +
            $" {srcPath} -> {tgtPath}, {added} nodes {(dry ? "would be added" : "added")}, {Conflicts.Count} refused");
        Console.WriteLine($"added {added}, refused {Conflicts.Count}");
        return 0;
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

    static int Xml(string[] args)
    {
        if (args.Length < 5) { Usage(); return 2; }
        string srcPath = args[1], xmlRoot = args[2];
        var paths = ReadPaths(args[3]);

        var (src, srcVer) = Open(srcPath);
        using var _s = src;
        string wzName = Path.GetFileName(srcPath);
        var ser = new FragmentSerializer();
        Console.WriteLine($"source {srcPath} iv={srcVer}; xml root {xmlRoot}");

        int added = 0;
        foreach (var manifestPath in paths)
        {
            var rel = Rel(manifestPath, wzName);
            int imgIdx = Array.FindIndex(rel, s => s.EndsWith(".img", StringComparison.OrdinalIgnoreCase));
            if (imgIdx < 0) { Conflict(manifestPath, "no .img segment — cannot map to an XML file"); continue; }

            string xmlFile = Path.Combine(new[] { xmlRoot, wzName }
                .Concat(rel.Take(imgIdx + 1)).ToArray()) + ".xml";
            var inImg = rel.Skip(imgIdx + 1).ToArray();

            var srcObj = Resolve(src, rel, rel.Length);
            if (srcObj == null) { Conflict(manifestPath, "MISSING IN SOURCE — manifest is stale"); continue; }

            if (inImg.Length == 0)
            {
                // whole new .img -> whole new .xml file
                if (File.Exists(xmlFile)) { Conflict(manifestPath, "xml file already exists: " + xmlFile); continue; }
                if (srcObj is not WzImage si) { Conflict(manifestPath, "expected a WzImage"); continue; }
                Directory.CreateDirectory(Path.GetDirectoryName(xmlFile)!);
                ser.SerializeImage(si, xmlFile);
                added++;
                Console.WriteLine($"  ADD   {manifestPath} -> new file {xmlFile}");
                continue;
            }

            // ponytail: the add-list manifests expand images exactly one level, so an entry is
            // either "<x>.img" or "<x>.img/<child>". Deeper nesting would need a real XML walker;
            // refuse loudly instead of guessing.
            if (inImg.Length > 1) { Conflict(manifestPath, "more than one level below .img — unsupported, needs an XML walker"); continue; }
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
            string name = inImg[0];
            // additive-only. Same INTENT as the binary gate, different MECHANISM: this is a
            // line-text scan, so it only sees elements written the way Cosmic's serializer
            // writes them (indent 2, name="…" on the opening line). OrdinalIgnoreCase to match
            // the binary side — WZ node lookup is case-insensitive, and an Ordinal compare here
            // would let a case-differing id past the gate and duplicate it into the file.
            if (lines.Any(l => IsTopLevelNamed(l, name))) { Conflict(manifestPath, "already exists in " + xmlFile); continue; }

            // Insert in sorted position purely so the git diff reads naturally — the server
            // looks nodes up by name, so position is never load-bearing. CompareOrdinal only
            // orders correctly for equal-length ids (true of zero-padded Item.wz ids, not of
            // ragged String.wz ones); when it misjudges, the node still lands somewhere valid.
            int insertAt = lines.FindIndex(l => TopLevelName(l) is string n && string.CompareOrdinal(n, name) > 0);
            if (insertAt < 0) insertAt = lines.FindLastIndex(l => l.StartsWith("</imgdir>", StringComparison.Ordinal));
            if (insertAt < 0) { Conflict(manifestPath, "no closing </imgdir> in " + xmlFile); continue; }

            // Fragment() emits its own CRLF line breaks, so split them back out before splicing.
            var frag = ser.Fragment(sp, "  ", xmlFile).Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
            lines.InsertRange(insertAt, frag);
            File.WriteAllText(xmlFile, string.Join("\r\n", lines) + "\r\n", new UTF8Encoding(false));
            added++;
            Console.WriteLine($"  ADD   {manifestPath} -> {xmlFile}:{insertAt + 1} ({frag.Length} lines)");
        }

        WriteConflicts(args[4], $"{wzName}: additive-only XML export -> {xmlRoot}, {added} nodes added, {Conflicts.Count} refused");
        Console.WriteLine($"added {added}, refused {Conflicts.Count}");
        return 0;
    }

    // an element opened at indent 2 (a direct child of the root <imgdir>)
    static string? TopLevelName(string line)
    {
        if (!line.StartsWith("  <", StringComparison.Ordinal) || line.StartsWith("   ", StringComparison.Ordinal)) return null;
        const string marker = " name=\"";
        int i = line.IndexOf(marker, StringComparison.Ordinal);
        if (i < 0) return null;
        i += marker.Length;
        int j = line.IndexOf('"', i);
        return j < 0 ? null : line[i..j];
    }

    static bool IsTopLevelNamed(string line, string name) =>
        string.Equals(TopLevelName(line), name, StringComparison.OrdinalIgnoreCase);
}
