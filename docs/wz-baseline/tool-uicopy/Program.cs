using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// WzCopy <inWz> <outWz> <pairsFile>
//
// Copies node subtrees to a DIFFERENT NAME within the SAME .wz file. That is the one thing the
// sibling WzMerge cannot express: WzMerge copies path -> the SAME path across two files, and its
// whole manifest format is "one path per line" precisely because source and destination names are
// always equal there. Renaming needs a second column, so it needs its own tool.
//
// Everything else is deliberately the same as WzMerge, because the lessons are the same file's:
// the SaveToDisk truncation footgun, the CWD-relative .TEMP scratch file, patchVersion inheritance,
// DeepClone for self-containment, the additive-only gate, and reopening the output before saying OK.
//
// exit: 0 ok | 1 error | 2 refused by a gate / bad args | 4 verification failed | 5 copied nothing
static class Program
{
    sealed class BadArgs(string m) : Exception(m);

    static int Main(string[] args)
    {
        try { return Run(args); }
        catch (BadArgs e) { Console.Error.WriteLine("REFUSED: " + e.Message); return 2; }
        catch (Exception e) { Console.Error.WriteLine("ERROR: " + e); return 1; }
    }

    static int Refuse(string why) { Console.Error.WriteLine("REFUSED: " + why); return 2; }

    // ===================== gate 3: WHERE THE OUTPUT MAY GO, ABSOLUTELY =====================
    // A property of the OUTPUT PATH ALONE. It does not compare the output to the input, because
    // that is the guard an earlier tool in this project already had when it wrote onto the live
    // client: out != target was true, and out was still D:\games\MapleStory\<Name>.wz.
    //
    // Two independent rules:
    //   1. Anything under the client root is refused unless it is under the staging root. Absolute
    //      directory containment, not string equality, not "is it the input".
    //   2. A directory holding an executable is a game install, wherever it is.
    // SaveToDisk File.Create()s the destination — truncating it INSTANTLY (WzFile.cs:675) — and
    // only then streams the images it is copying out of the input's own reader. There is no
    // "oops"; by the time it fails the old file is already gone.
    const string ClientRoot = @"D:\games\MapleStory";
    const string StageRoot  = @"D:\games\MapleStory\Server\wz-merge";

    static string Norm(string p) => Path.TrimEndingDirectorySeparator(Path.GetFullPath(p));
    static bool UnderDir(string path, string dir) =>
        Norm(path).StartsWith(Norm(dir) + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase);

    static string? OutRefusal(string outPath)
    {
        string full = Path.GetFullPath(outPath);
        string dir = Path.GetDirectoryName(full)!;

        if (UnderDir(full, ClientRoot) && !UnderDir(full, StageRoot))
            return $"{full} is inside the live client tree {ClientRoot} and is NOT under the staging root " +
                   $"{StageRoot}. The live client is read-only to this tool. SaveToDisk truncates its " +
                   "destination before it has read anything, so a wrong path here is not recoverable.";

        // Applied to the nearest EXISTING ancestor, not only to <dir>: `<client>\brandnew\UI.wz`
        // would otherwise slip past because there is nothing yet to inspect. Not the whole chain —
        // the staging root lives under a game install by design, and rule 1 above already covers it.
        // ponytail: heuristic with a named ceiling (an existing .exe-free subdirectory of some other
        // install still passes). Rule 1 is the one that matters for THIS client.
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
                   $"install, not a staging directory. Stage under {StageRoot}\\<ticket>\\.";
        return null;
    }

    // ---------- open (same three-IV fallback as WzMerge/WzDump) ----------
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

    // ---------- paths ----------
    static IEnumerable<WzObject> Kids(WzObject o) => o switch
    {
        WzDirectory d => d.WzDirectories.Cast<WzObject>().Concat(d.WzImages),
        WzImage i => i.WzProperties,
        WzImageProperty p => (IEnumerable<WzObject>?)p.WzProperties ?? Array.Empty<WzObject>(),
        _ => Array.Empty<WzObject>()
    };

    // Accepts both manifest form ("UI.wz/UIWindow.img/…") and root-relative form
    // ("UIWindow.img/…"), same as WzMerge's Segs(): a leading segment equal to the file's own
    // name is stripped.
    static string[] Segs(string path, string wzFileName)
    {
        var segs = path.Replace('\\', '/').Split('/', StringSplitOptions.RemoveEmptyEntries);
        return segs.Length > 0 && segs[0].Equals(wzFileName, StringComparison.OrdinalIgnoreCase)
            ? segs.Skip(1).ToArray() : segs;
    }

    // Not WzFile.GetObjectFromPath: that overload needs the WzFileManager singleton and returns
    // null without it. `count` lets the caller resolve the PARENT of a path that does not exist yet.
    static WzObject? Resolve(WzFile file, IReadOnlyList<string> segs, int count)
    {
        WzObject? cur = file.WzDirectory;
        for (int i = 0; i < count; i++)
        {
            cur = cur switch
            {
                WzDirectory d => d[segs[i]],
                WzImage img => img[segs[i]],                      // indexer parses on demand
                WzImageProperty p => p.WzProperties?.FirstOrDefault(
                    c => string.Equals(c.Name, segs[i], StringComparison.OrdinalIgnoreCase)),
                _ => null
            };
            if (cur == null) return null;
        }
        return cur;
    }

    // ---------- the node census (gates 4 and 5) ----------
    //
    // A MULTISET, not a set: WZ permits two siblings with the same name, and a plain HashSet would
    // silently collapse them — under-counting the baseline by exactly as much as it under-counts
    // the output, so "zero removals" would pass while a node had in fact been dropped.
    //
    // UOLs are NOT descended into. WzProperties on a WzUOLProperty hands back the RESOLVED
    // TARGET's children, so a UOL pointing at its own ancestor walks forever (WzMerge hit exactly
    // that: Reactor.wz/1050000.img/0/hit/2). The node it points at is censused at its own path in
    // the same pass, so nothing is lost. The depth cap is a backstop for a cycle of another shape
    // and THROWS rather than truncating — a silently truncated census proves nothing.
    const int MaxDepth = 128;

    static Dictionary<string, int> Census(WzFile f, out long total)
    {
        var m = new Dictionary<string, int>(StringComparer.Ordinal);
        long n = 0;
        void Walk(WzObject o, string path, int depth)
        {
            if (depth > MaxDepth) throw new Exception($"census depth limit {MaxDepth} at {path} — cycle?");
            m[path] = m.TryGetValue(path, out int c) ? c + 1 : 1;
            n++;
            if (o is WzUOLProperty) return;
            foreach (var k in Kids(o)) Walk(k, path + "/" + k.Name, depth + 1);
        }
        foreach (var k in Kids(f.WzDirectory)) Walk(k, k.Name, 0);
        total = n;
        return m;
    }

    static long SubtreeCount(WzObject o)
    {
        long n = 1;
        if (o is WzUOLProperty) return n;
        foreach (var k in Kids(o)) n += SubtreeCount(k);
        return n;
    }

    // relative path -> (w,h) for every canvas under `o`, so the same walk over the copy in the
    // reopened output can be compared entry for entry.
    static Dictionary<string, (int W, int H)> Canvases(WzObject o)
    {
        var d = new Dictionary<string, (int, int)>(StringComparer.Ordinal);
        void Walk(WzObject x, string rel)
        {
            if (x is WzCanvasProperty c && c.PngProperty != null) d[rel] = (c.PngProperty.Width, c.PngProperty.Height);
            if (x is WzUOLProperty) return;
            foreach (var k in Kids(x)) Walk(k, rel + "/" + k.Name);
        }
        Walk(o, "");
        return d;
    }

    // ---------- pairs file ----------
    static List<(string Src, string Dst)> ReadPairs(string file)
    {
        if (!File.Exists(file)) throw new BadArgs($"pairs file does not exist: {file}");
        var rows = new List<(string, string)>();
        foreach (var raw in File.ReadAllLines(file))
        {
            var line = raw.Trim();
            if (line.Length == 0 || line[0] == '#') continue;
            int eq = line.IndexOf('=');
            if (eq < 0) throw new BadArgs($"pairs row is not '<srcPath>=<dstPath>': {line}");
            string s = line[..eq].Trim(), d = line[(eq + 1)..].Trim();
            if (s.Length == 0 || d.Length == 0) throw new BadArgs($"pairs row has an empty side: {line}");
            rows.Add((s, d));
        }
        // An empty pairs file used to be the shape that reads as "everything was already there".
        // Same rule as WzMerge's ReadPaths: a copy of nothing must never report success.
        if (rows.Count == 0)
            throw new BadArgs($"{file} holds 0 pairs (empty, or nothing but comments). If it was produced " +
                              "by a shell redirect, the producing command failed after the shell truncated it.");
        return rows;
    }

    // ---------- positional-array invariant ----------
    // This project treats a container whose children are ALL non-negative integers forming ONE
    // CONSECUTIVE RUN as a positional array. Appending to one is legal; punching a hole in one, or
    // turning it into a non-run, is not. Evaluated AFTER every copy, so the order the pairs are
    // listed in cannot matter.
    static (int Min, int Max, int Count)? Run(IEnumerable<WzObject> kids)
    {
        var ints = new List<int>();
        foreach (var k in kids)
        {
            if (!int.TryParse(k.Name, out int v) || v < 0) return null;
            ints.Add(v);
        }
        if (ints.Count == 0) return null;
        ints.Sort();
        for (int i = 1; i < ints.Count; i++) if (ints[i] != ints[i - 1] + 1) return null;
        return (ints[0], ints[^1], ints.Count);
    }

    static int Run(string[] argv)
    {
        if (argv.Length != 3)
        {
            Console.Error.WriteLine("WzCopy <inWz> <outWz> <pairsFile>\n" +
                "  pairsFile: one '<srcPath>=<dstPath>' per line; '#' comments and blank lines ignored.\n" +
                "  Paths are relative to the .wz root; a leading '<Name>.wz/' is accepted and stripped.\n" +
                "  ADDITIVE ONLY: every dstPath must NOT already exist. There is no --force.\n" +
                "exit: 0 ok | 1 error | 2 refused | 4 verification failed | 5 copied nothing");
            return 2;
        }
        string inPath = Path.GetFullPath(argv[0]), outPath = Path.GetFullPath(argv[1]);
        var pairs = ReadPairs(argv[2]);

        // ---- gate 3, before anything is opened or read ----
        if (!File.Exists(inPath)) throw new BadArgs($"input does not exist: {inPath}");
        if (File.Exists(outPath))
            return Refuse($"<outWz> already exists: {outPath}. The output must be a NEW file — this tool never " +
                          "overwrites, and SaveToDisk would truncate whatever is there before reading anything.");
        if (OutRefusal(outPath) is string why) return Refuse(why);
        if (Norm(inPath) == Norm(outPath)) return Refuse("<inWz> and <outWz> are the same file.");

        var (f, iv) = Open(inPath);
        using var _f = f;
        string wzName = Path.GetFileName(inPath);
        Console.WriteLine($"input  {inPath}  iv={iv} patchVersion={f.Version}");
        Console.WriteLine($"output {outPath}");
        Console.WriteLine($"{pairs.Count} pairs");

        // BASELINE CENSUS, before any mutation. Parses every image in the file; that sets Parsed,
        // NOT Changed, and SaveToDisk keys off Changed (WzDirectory.cs:347) — so the census does
        // not cause a single extra image to be re-serialized.
        Console.WriteLine("censusing input …");
        var before = Census(f, out long beforeTotal);
        Console.WriteLine($"baseline: {beforeTotal} nodes ({before.Count} distinct paths)");

        // ---- gates 1 and 2, resolved for ALL pairs before ANY mutation ----
        // Whole-batch, not per-row: a batch that is going to be refused must not have half of it
        // already applied to the tree by the time it is refused.
        var plan = new List<(string Src, string Dst, WzObject SrcObj, WzObject Parent, string Leaf)>();
        var refusals = new List<string>();
        foreach (var (s, d) in pairs)
        {
            var sr = Segs(s, wzName); var dr = Segs(d, wzName);
            if (sr.Length == 0 || dr.Length < 1) { refusals.Add($"{s}={d}: empty path"); continue; }
            var srcObj = Resolve(f, sr, sr.Length);
            if (srcObj == null) { refusals.Add($"{s}: srcPath DOES NOT EXIST"); continue; }
            if (Resolve(f, dr, dr.Length) != null) { refusals.Add($"{d}: dstPath ALREADY EXISTS — additive-only, no overwrite, no --force"); continue; }
            var parent = Resolve(f, dr, dr.Length - 1);
            if (parent == null) { refusals.Add($"{d}: parent '{string.Join('/', dr[..^1])}' does not exist"); continue; }
            if (srcObj is not WzImageProperty)
                { refusals.Add($"{s}: source is {srcObj.GetType().Name}; this tool renames PROPERTIES only " +
                               "(a renamed image or directory has never been needed — add the branch when one is)"); continue; }
            if (parent is not WzImage && parent is not MapleLib.WzLib.IPropertyContainer)
                { refusals.Add($"{d}: parent is {parent.GetType().Name}, which cannot hold a property"); continue; }
            plan.Add((s, d, srcObj, parent, dr[^1]));
        }
        if (refusals.Count > 0)
        {
            foreach (var r in refusals) Console.Error.WriteLine("  REFUSED " + r);
            return Refuse($"{refusals.Count} of {pairs.Count} pairs failed a gate. NOTHING was written — this tool " +
                          "applies a batch whole or not at all, so a partially-applied tree can never reach disk.");
        }
        // Two pairs writing the same destination: the first lands, the second then sees an existing
        // dst — but only if it were re-checked, and the gate above ran before either. Catch it here.
        var dup = plan.GroupBy(p => string.Join('/', Segs(p.Dst, wzName)), StringComparer.OrdinalIgnoreCase)
                      .FirstOrDefault(g => g.Count() > 1);
        if (dup != null) return Refuse($"dstPath '{dup.Key}' appears {dup.Count()} times in the pairs file.");

        // Baseline shape of every destination parent, sampled BEFORE the adds.
        var parents = plan.Select(p => p.Parent).Distinct().ToList();
        var wasRun = parents.ToDictionary(p => p, p => Run(Kids(p)));

        // ---- the copy ----
        long added = 0;
        var srcCanvases = new Dictionary<string, Dictionary<string, (int W, int H)>>(StringComparer.Ordinal);
        foreach (var (s, d, srcObj, parent, leaf) in plan)
        {
            // DeepClone, then rename. The clone is self-contained: WzPngProperty.DeepClone copies
            // compressedImageBytes rather than keeping the source's wzReader, so the copy survives
            // the save independently of the node it came from.
            var clone = ((WzImageProperty)srcObj).DeepClone();
            clone.Name = leaf;
            switch (parent)
            {
                case WzImage pi: pi.AddProperty(clone); pi.Changed = true; break;
                case MapleLib.WzLib.IPropertyContainer pc:
                    pc.AddProperty(clone);
                    if (((WzImageProperty)parent).ParentImage is WzImage owner) owner.Changed = true;
                    break;
                default: throw new Exception("INTERNAL: unreachable, the gate above already filtered this");
            }
            // AddProperty on a WzSubProperty APPENDS BLINDLY — only WzImage.AddProperty throws on a
            // duplicate. The gate is the sole protection for the container branch, so assert what
            // the gate promised rather than trusting it.
            int dupes = Kids(parent).Count(k => string.Equals(k.Name, leaf, StringComparison.OrdinalIgnoreCase));
            if (dupes != 1)
                throw new Exception($"INTERNAL: '{d}' appears {dupes}x under its parent after the add — the " +
                                    "additive-only gate did not hold. Nothing was written.");

            long n = SubtreeCount(clone);
            added += n;
            srcCanvases[d] = Canvases(srcObj);
            Console.WriteLine($"  COPY  {s}  ->  {d}   ({n} nodes, {srcCanvases[d].Count} canvases)");
        }
        if (added == 0) { Console.Error.WriteLine("NOTHING WAS COPIED."); return 5; }

        // ---- positional-array invariant, after every add ----
        foreach (var p in parents)
        {
            string pn = p.FullPath;
            var was = wasRun[p]; var now = Run(Kids(p));
            if (was == null) { Console.WriteLine($"  array-check {pn}: not a positional array before the copy (children are not all integers) — nothing to preserve"); continue; }
            if (now == null)
                return Refuse($"{pn} was a positional array (one consecutive run {was.Value.Min}..{was.Value.Max}) and after the " +
                              "copy its children are no longer one consecutive run of non-negative integers. Nothing was written.");
            Console.WriteLine($"  array-check {pn}: was {was.Value.Min}..{was.Value.Max} ({was.Value.Count}), now " +
                              $"{now.Value.Min}..{now.Value.Max} ({now.Value.Count}) — still ONE consecutive run, appended at the end");
        }

        // ---- save ----
        // .partial first: the move is the only step that touches <outWz>, so an OOM / disk-full /
        // Ctrl-C leaves something nobody will mistake for a finished file.
        string outDir = Path.GetDirectoryName(outPath)!;
        Directory.CreateDirectory(outDir);
        string partial = outPath + ".partial";
        if (File.Exists(partial)) File.Delete(partial);

        // MapleLib's scratch file is CWD-relative (WzFile.cs:664). Pin the CWD to the staging
        // directory rather than trusting the operator's shell — run from the client folder and it
        // drops a <Name>.TEMP straight into the live install.
        // SaveToDisk(path) with no version override re-emits the iv and patchVersion MapleLib
        // parsed off the input. The live UI.wz is patchVersion 83 and the output must be too;
        // there is no conversion step here to get wrong, but it is asserted after the reopen below.
        string cwd = Directory.GetCurrentDirectory();
        Console.WriteLine($"saving {partial} at iv={f.MapleVersion} patchVersion={f.Version} (inherited from input); scratch .TEMP in {outDir}");
        try { Directory.SetCurrentDirectory(outDir); f.SaveToDisk(partial); }
        finally { Directory.SetCurrentDirectory(cwd); }

        // ---- gates 4 and 5: reopen the output and prove it, as a stranger would ----
        bool ok = true;
        void Fail(string m) { Console.Error.WriteLine("  FAIL " + m); ok = false; }

        WzFile o; WzMapleVersion oiv;
        try { (o, oiv) = Open(partial); }
        catch (Exception ex) { Console.Error.WriteLine($"  UNREADABLE output: {ex.Message}"); return 4; }
        using (o)
        {
            Console.WriteLine($"verify {partial}  iv={oiv} patchVersion={o.Version}");
            if (o.Version != f.Version) Fail($"patchVersion drifted: input {f.Version}, output {o.Version}");
            if (oiv != iv) Fail($"encryption IV drifted: input {iv}, output {oiv}");

            foreach (var (s, d, _, _, _) in plan)
            {
                var sr = Segs(s, wzName); var dr = Segs(d, wzName);
                if (Resolve(o, dr, dr.Length) == null) Fail($"dstPath MISSING in output: {d}");
                if (Resolve(o, sr, sr.Length) == null) Fail($"srcPath NO LONGER PRESENT in output: {s}");
            }

            // Canvas decode. A path lookup and ParseImage both walk straight past a corrupted pixel
            // payload; actually decoding it to a Bitmap is the only thing here that would not.
            int decoded = 0;
            foreach (var (_, d, _, _, _) in plan)
            {
                var dr = Segs(d, wzName);
                var dst = Resolve(o, dr, dr.Length);
                if (dst == null) continue;                 // already reported above
                var want = srcCanvases[d];
                var got = new Dictionary<string, (int, int)>(StringComparer.Ordinal);
                void Walk(WzObject x, string rel)
                {
                    if (x is WzCanvasProperty c)
                    {
                        var png = c.PngProperty;
                        using var bmp = png?.GetImage(false);
                        if (bmp == null) Fail($"{d}{rel}: canvas did NOT decode to a bitmap");
                        else { got[rel] = (bmp.Width, bmp.Height); decoded++; }
                    }
                    if (x is WzUOLProperty) return;
                    foreach (var k in Kids(x)) Walk(k, rel + "/" + k.Name);
                }
                Walk(dst, "");
                foreach (var (rel, wh) in want)
                {
                    if (!got.TryGetValue(rel, out var g)) { Fail($"{d}{rel}: canvas present in source, absent (or undecodable) in the copy"); continue; }
                    if (g != wh) Fail($"{d}{rel}: decoded {g.Item1}x{g.Item2}, source is {wh.W}x{wh.H}");
                }
                foreach (var rel in got.Keys) if (!want.ContainsKey(rel)) Fail($"{d}{rel}: canvas in the copy that the source does not have");
            }
            Console.WriteLine($"  {decoded} copied canvas(es) decoded to a bitmap");

            // ZERO REMOVALS + exact delta, COMPUTED. Not "the paths I asked for are there" — every
            // path the input had, at the multiplicity it had it.
            Console.WriteLine("censusing output …");
            var after = Census(o, out long afterTotal);
            long removed = 0, shrunk = 0;
            foreach (var (path, n) in before)
            {
                if (!after.TryGetValue(path, out int m)) { removed++; if (removed <= 20) Fail($"REMOVED from output: {path}"); }
                else if (m < n) { shrunk++; if (shrunk <= 20) Fail($"multiplicity dropped for {path}: {n} -> {m}"); }
            }
            if (removed > 20 || shrunk > 20) Console.Error.WriteLine($"  (… {removed} removed, {shrunk} shrunk in total; first 20 of each shown)");
            long delta = afterTotal - beforeTotal;
            Console.WriteLine($"nodes: before {beforeTotal}, after {afterTotal}, delta {delta}, expected +{added}");
            if (delta != added) Fail($"node delta is {delta}, expected exactly {added}");
            if (removed == 0 && shrunk == 0) Console.WriteLine($"ZERO REMOVALS: all {before.Count} distinct input paths ({beforeTotal} nodes) still present at the same multiplicity");
        }

        if (!ok)
        {
            Console.Error.WriteLine($"VERIFICATION FAILED. Output left at {partial} and NOT promoted to {outPath}. Do not install it.");
            return 4;
        }
        File.Move(partial, outPath);
        using (var s = File.OpenRead(outPath))
            Console.WriteLine($"sha256 {Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(s)).ToLowerInvariant()}  {outPath}");
        Console.WriteLine($"verified OK -> {outPath}  ({plan.Count} pairs, {added} nodes added)");
        return 0;
    }
}
