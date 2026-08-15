using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.Serializer;

// WzMerge — additive-only node importer for the v83 -> v84 upgrade.
//
//   WzMerge dump   <wz> <path/under/wz> [depth]
//   WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt>
//   WzMerge xml    <sourceWz> <xmlRoot>            <pathsFile> <conflictsTxt> [-]
//   WzMerge verify <wz> <pathsFile>
//   WzMerge hash   <wz> <path/under/wz>
//   WzMerge deps   <mapWz> <Map/MapN/<id>.img>
//
// EXIT CODE CONTRACT (scripted callers depend on it; see WZ-MERGE-PROCEDURE.md):
//   0  success — every requested path was written (or, on a dry run, would be)
//   1  unexpected failure (exception); nothing installable was produced
//   2  bad invocation — usage error OR a safety guard refused the arguments
//   3  completed, but >=1 manifest row was REFUSED. conflicts.txt is non-empty.
//      A dry run that finds collisions exits 3 by design; that is the answer, not a fault.
//   4  post-write verification FAILED. The output is not trustworthy: DO NOT install it.
// "added 0, refused N" used to exit 0, which let a scripted 04-09 loop report green
// having imported nothing. It exits 3 now.
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
        "WzMerge dump   <wz> <path/under/wz> [depth]\n" +
        "WzMerge merge  <sourceWz> <targetWz> <outWz|-> <pathsFile> <conflictsTxt>\n" +
        "WzMerge xml    <sourceWz> <xmlRoot> <pathsFile> <conflictsTxt> [-]\n" +
        "WzMerge verify <wz> <pathsFile>\n" +
        "WzMerge hash   <wz> <path/under/wz>\n" +
        "WzMerge deps   <mapWz> <Map/MapN/<id>.img>\n" +
        "  '-' in the <outWz> slot (merge) or as a trailing arg (xml) = DRY RUN.\n" +
        "exit: 0 ok | 1 error | 2 bad args/refused by a safety guard | 3 rows refused | 4 verification failed");

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
        foreach (var k in Kids(obj)) Print(k, ind + "  ", depth - 1);
    }

    static IEnumerable<WzObject> Kids(WzObject obj) => obj switch
    {
        WzDirectory d => d.WzDirectories.Cast<WzObject>().Concat(d.WzImages),
        WzImage i => i.WzProperties,
        WzImageProperty p => (IEnumerable<WzObject>?)p.WzProperties ?? Array.Empty<WzObject>(),
        _ => Array.Empty<WzObject>()
    };

    // ---------- verify (B1: the tool checks its own output) ----------

    // Re-OPEN a written .wz from disk and re-RESOLVE every manifest path in it. This is the
    // only thing that distinguishes "SaveToDisk returned" from "a file a client can read":
    // SaveToDisk truncates the destination up front (WzFile.cs:675) and then streams images
    // for minutes, so OOM, a full disk or Ctrl-C leaves a plausible-looking .wz that parses
    // partway and then stops. Before this existed, nothing in the pipeline ever re-parsed the
    // tool's own output — the first reader was the user's game client.
    static bool VerifyFile(string wzPath, string wzName, IReadOnlyList<string> expect)
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
        // Force EVERY image to parse, then immediately unparse it. A truncated tail is
        // invisible to a path lookup that never reaches it; ParseImage throws or returns
        // false on a short/garbled block, which is exactly the failure a half-written .wz has.
        // Unparsing keeps this bounded on Map.wz (629 MB) instead of materialising the file.
        int bad = 0, imgs = 0;
        void Walk(WzDirectory d)
        {
            foreach (var img in d.WzImages)
            {
                imgs++;
                try
                {
                    if (!img.Parsed && !img.ParseImage()) throw new Exception("ParseImage returned false");
                    img.UnparseImage();
                }
                catch (Exception ex) { Console.Error.WriteLine($"  UNPARSEABLE image {img.FullPath}: {ex.GetType().Name} {ex.Message}"); bad++; }
            }
            foreach (var sub in d.WzDirectories) Walk(sub);
        }
        Walk(f.WzDirectory);
        Console.WriteLine($"verify: {imgs} images parsed, {bad} unparseable, {missing} requested paths missing");
        return missing == 0 && bad == 0;
    }

    static int VerifyCmd(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        var expect = ReadPaths(args[2]);
        // The manifest declares its own "<Name>.wz" root, so a renamed or .partial copy of
        // the output still verifies. Falls back to the filename for an empty manifest.
        string wzName = expect.Count > 0 ? expect[0].Split('/')[0] : Path.GetFileName(args[1]);
        return VerifyFile(Path.GetFullPath(args[1]), wzName, expect) ? 0 : 4;
    }

    // ---------- hash (B3: content check for the one image that is re-serialized) ----------

    // BlockSize is a length, and for every image the merge did NOT touch it is not even a
    // measurement — MapleLib memcpy's those straight out of the source file and carries the
    // recorded size across (WzDirectory.cs:353-357), so pre/post BlockSize compares a number
    // to a copy of itself. The image that WAS re-serialized (Changed=true) is the only one
    // where the serializer could have got something wrong, and it is the one BlockSize says
    // least about. So: digest the DECODED values of each direct child, pre and post, and diff.
    static string Sha(ReadOnlySpan<byte> b) => Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(b)).ToLowerInvariant();

    static void Canon(WzObject o, string prefix, StringBuilder sb)
    {
        // ponytail: leaf value via ToString(), which is the decoded scalar for every property
        // type these manifests carry (int/short/long/float/double/string/uol/vector). Canvases
        // hash their compressed pixel bytes instead — that is the payload a broken merge loses.
        string val = o switch
        {
            MapleLib.WzLib.WzProperties.WzCanvasProperty c =>
                $"canvas {c.PngProperty?.Width}x{c.PngProperty?.Height} png:{Sha(c.PngProperty?.GetCompressedBytes(false) ?? Array.Empty<byte>())}",
            WzImageProperty p when (p.WzProperties?.Count ?? 0) == 0 => $"{p.PropertyType} = {p}",
            _ => o.GetType().Name
        };
        sb.Append(prefix).Append('\t').Append(val).Append('\n');
        foreach (var k in Kids(o).OrderBy(k => k.Name, StringComparer.Ordinal)) Canon(k, prefix + "/" + k.Name, sb);
    }

    static int Hash(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        var (file, ver) = Open(args[1]);
        using var _ = file;
        var segs = args[2].Split('/', StringSplitOptions.RemoveEmptyEntries);
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

    // A map .img names its scenery by SET NAME, not by path: back/<n>/bS -> Map.wz/Back/<bS>.img,
    // <layer>/obj/<n>/{oS,l0} -> Map.wz/Obj/<oS>.img/<l0>, <layer>/info/tS -> Map.wz/Tile/<tS>.img.
    // Those sets are separate manifest rows and the ordering rule does NOT catch them: nothing
    // about "Map/Map2/2400800xx.img" says it needs "Back/dragonDream.img". Merge a map without
    // its sets and the client renders a broken map or crashes. Output is manifest rows, so it
    // can be concatenated straight into the paths file.
    static int Deps(string[] args)
    {
        if (args.Length < 3) { Usage(); return 2; }
        var (file, ver) = Open(args[1]);
        using var _ = file;
        string wzName = Path.GetFileName(args[1]);
        var segs = args[2].Split('/', StringSplitOptions.RemoveEmptyEntries);
        var obj = Resolve(file, segs, segs.Length);
        if (obj == null) { Console.Error.WriteLine("NOT FOUND: " + args[2]); return 1; }

        var rows = new SortedSet<string>(StringComparer.OrdinalIgnoreCase);
        void Walk(WzObject o)
        {
            string? Child(string n) => Kids(o).FirstOrDefault(
                k => string.Equals(k.Name, n, StringComparison.OrdinalIgnoreCase))?.ToString();
            if (Child("bS") is string bs && bs.Length > 0) rows.Add($"{wzName}/Back/{bs}.img");
            if (Child("tS") is string ts && ts.Length > 0) rows.Add($"{wzName}/Tile/{ts}.img");
            if (Child("oS") is string os && os.Length > 0 && Child("l0") is string l0 && l0.Length > 0)
                rows.Add($"{wzName}/Obj/{os}.img/{l0}");
            foreach (var k in Kids(o)) Walk(k);
        }
        Walk(obj);
        Console.WriteLine($"# {args[2]} references {rows.Count} scenery sets ({args[1]} iv={ver} patchVersion={file.Version})");
        Console.WriteLine("# Each row must already exist in the target, or be merged BEFORE the map image.");
        foreach (var r in rows) Console.WriteLine(r);
        return 0;
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

    static int Merge(string[] args)
    {
        if (args.Length < 6) { Usage(); return 2; }
        bool dry = args[3] == "-";
        // Everything absolute up front: the save block below changes the process working
        // directory (see there for why), and a relative conflicts path would then land
        // somewhere else entirely.
        string srcPath = Path.GetFullPath(args[1]), tgtPath = Path.GetFullPath(args[2]);
        string outPath = dry ? "-" : Path.GetFullPath(args[3]);
        string conflictsPath = Path.GetFullPath(args[5]);
        var paths = ReadPaths(args[4]);

        // ===================== B1: WHERE THE OUTPUT MAY GO =====================
        // MapleLib's SaveToDisk is NOT atomic and NOT copy-on-write. It File.Create()s the
        // destination — truncating it instantly (WzFile.cs:675) — and only then spends the
        // next several minutes streaming unchanged images out of the TARGET's own open reader
        // (WzDirectory.cs:353-357). Item.wz is 200 MB, Map.wz is 629 MB. So:
        //   * out == target only fails safe by accident: WzFile opens with FileShare.Read
        //     (WzFile.cs:243), so File.Create hits the lock and throws. That is the OS, not
        //     a design, and it is not something to rely on. Refuse it explicitly.
        //   * out anywhere in the live client's directory means a crash, an OOM (DeepClone of
        //     a directory is memory-bound) or a Ctrl-C leaves a truncated, plausible-looking
        //     .wz sitting next to the real ones — and MapleLib's scratch file is RELATIVE
        //     (Path.GetFileNameWithoutExtension(path) + ".TEMP", WzFile.cs:664), so it lands
        //     in the process CWD too. Requiring a staging directory separate from the target's
        //     is the one rule that makes both of those impossible.
        if (!dry)
        {
            if (SamePath(outPath, tgtPath))
                return Refuse($"<outWz> is the target itself ({outPath}). SaveToDisk truncates the destination before it reads the images it needs out of it. Write to a staging directory and copy afterwards.");
            if (SamePath(outPath, srcPath))
                return Refuse($"<outWz> is the v84 source ({outPath}). v84 is read-only input.");
            if (SamePath(Path.GetDirectoryName(outPath), Path.GetDirectoryName(tgtPath)))
                return Refuse($"<outWz> is in the same directory as the target ({Path.GetDirectoryName(outPath)}). Merges stage into a directory of their own — a half-written .wz, or MapleLib's multi-hundred-MB .TEMP scratch file, must never appear beside the file it was made from. See WZ-MERGE-PROCEDURE.md 'Staging'.");
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
            // The gate is the ONLY protection for three of the four branches above:
            // WzImage.AddProperty throws on a duplicate name, but AddImage, AddDirectory and
            // WzSubProperty.AddProperty all append blindly. Assert what the gate promised.
            int dupes = Kids(parent).Count(k => string.Equals(k.Name, rel[^1], StringComparison.OrdinalIgnoreCase));
            if (dupes != 1)
                throw new Exception($"INTERNAL: '{manifestPath}' appears {dupes}x under its parent after the add — the additive-only gate did not hold. Nothing was written.");

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
        bool verified = true;
        if (dry)
        {
            Console.WriteLine("dry run: not saving");
        }
        else
        {
            string outDir = Path.GetDirectoryName(outPath)!;
            Directory.CreateDirectory(outDir);

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
            verified = VerifyFile(partial, wzName, expect);
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
            $" {srcPath} -> {tgtPath}, {added} nodes {(dry ? "would be added" : "added")}, {Conflicts.Count} refused");
        Console.WriteLine($"added {added}, refused {Conflicts.Count}");
        if (!verified) return 4;
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

    static int Xml(string[] args)
    {
        if (args.Length < 5) { Usage(); return 2; }
        string srcPath = args[1], xmlRoot = args[2];
        var paths = ReadPaths(args[3]);
        // H4: the XML side had no dry run at all, while the procedure said "dry run before
        // every real merge". A trailing "-" mirrors merge's "-" in the <outWz> slot: every
        // check below still runs (including the splice position), nothing is written.
        bool dry = args.Length > 5 && args[5] == "-";

        var (src, srcVer) = Open(srcPath);
        using var _s = src;
        string wzName = Path.GetFileName(srcPath);
        var ser = new FragmentSerializer();
        Console.WriteLine($"source {srcPath} iv={srcVer}; xml root {xmlRoot}{(dry ? "  [DRY RUN — nothing will be written]" : "")}");

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
            if (FindChild(lines, start, end, indent, name) >= 0) { Conflict(manifestPath, "already exists in " + xmlFile); continue; }

            // Insert in sorted position purely so the git diff reads naturally — the server
            // looks nodes up by name, so position is never load-bearing. CompareOrdinal only
            // orders correctly for equal-length ids (true of zero-padded Item.wz ids, not of
            // ragged String.wz ones); when it misjudges, the node still lands somewhere valid.
            int insertAt = lines.FindIndex(start, end - start,
                l => NameAt(l, indent) is string n && string.CompareOrdinal(n, name) > 0);
            if (insertAt < 0) insertAt = end; // last child of this container

            // Fragment() emits its own CRLF line breaks, so split them back out before splicing.
            var frag = ser.Fragment(sp, new string(' ', indent), xmlFile).Split("\r\n", StringSplitOptions.RemoveEmptyEntries);
            lines.InsertRange(insertAt, frag);
            if (!dry) File.WriteAllText(xmlFile, string.Join("\r\n", lines) + "\r\n", new UTF8Encoding(false));
            added++;
            Console.WriteLine($"  ADD   {manifestPath} -> {xmlFile}:{insertAt + 1} ({frag.Length} lines)");
        }

        WriteConflicts(args[4], $"{wzName}: additive-only XML export{(dry ? " — DRY RUN, nothing was written" : "")}" +
            $" -> {xmlRoot}, {added} nodes {(dry ? "would be added" : "added")}, {Conflicts.Count} refused");
        Console.WriteLine($"added {added}, refused {Conflicts.Count}");
        return Conflicts.Count > 0 ? 3 : 0;
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

    static int FindChild(List<string> lines, int start, int end, int indent, string name) =>
        lines.FindIndex(start, end - start,
            l => string.Equals(NameAt(l, indent), name, StringComparison.OrdinalIgnoreCase));
}
