using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// ponytail: ticket 11c. Third mode on the same tool - no new project, no new WZ parser.
//
//   WzCensus icons <wzPath> <img1,img2,...> <outFile>
//       For every <img>/skill/<id>, report presence + DECODABILITY of the four icon
//       variants the skill window blits. "Present" is not enough: a UOL that dangles or
//       a canvas whose zlib payload will not inflate is as fatal as an absent node.
//
//   WzCensus links <wzPath> <root1,root2,...> <outFile>
//       Walk the given subtrees (image path, or image path + '/' + inner path), resolve
//       every UOL / _inlink / _outlink, and report DANGLING links and CYCLES.
//
// Roots are given as full node paths with the "<Name>.wz" segment first, e.g.
//   UI.wz/UIWindow.img/SkillEx
//
// ponytail: the check for the resolver is the run itself, not a test file - it resolved all
// 186 links under UI.wz/UIWindow.img (incl. 48 relative '../' UOLs in SkillEx/Dragon) to real
// nodes, and correctly reported ROOT-NOT-FOUND for a path that does not exist (Skill.wz/0.img).
// Both arms exercised. Ceiling: cycle detection is a visited-set over resolved paths capped at
// 64 hops; raise the cap if a legitimate chain ever gets that long.

static class SkillAudit
{
    static readonly string[] IconNames = { "icon", "iconMouseOver", "iconDisabled", "iconMouseOverDisabled" };

    public static WzFile OpenFile(string path)
    {
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try { f = new WzFile(path, -1, ver); if (f.ParseWzFile() == WzFileParseStatus.Success) return f; } catch { }
            f?.Dispose();
        }
        throw new InvalidDataException("no IV parsed " + path);
    }

    static IEnumerable<WzImageProperty> Kids(WzObject? o) =>
        (IEnumerable<WzImageProperty>?)(o as WzImageProperty)?.WzProperties
        ?? (IEnumerable<WzImageProperty>?)(o as WzImage)?.WzProperties
        ?? Array.Empty<WzImageProperty>();

    static WzObject? Child(WzObject? cur, string name) => cur switch
    {
        WzDirectory d => (WzObject?)d.WzDirectories.FirstOrDefault(x => Eq(x.Name, name))
                         ?? d.WzImages.FirstOrDefault(x => Eq(x.Name, name)),
        WzImage im => Parsed(im)?.FirstOrDefault(x => Eq(x.Name, name)),
        WzImageProperty ip => Kids(ip).FirstOrDefault(x => Eq(x.Name, name)),
        _ => null,
    };

    static bool Eq(string a, string b) => string.Equals(a, b, StringComparison.OrdinalIgnoreCase);

    static WzPropertyCollection? Parsed(WzImage im)
    {
        if (!im.Parsed) { try { if (!im.ParseImage()) return null; } catch { return null; } }
        return im.WzProperties;
    }

    // segs[0] is "<Name>.wz"; everything after resolves under the file root.
    static WzObject? ByPath(WzFile file, List<string> segs, out string why)
    {
        why = "";
        WzObject cur = file.WzDirectory;
        for (int i = 1; i < segs.Count; i++)
        {
            var next = Child(cur, segs[i]);
            if (next == null) { why = $"missing segment '{segs[i]}' under {string.Join('/', segs.Take(i))}"; return null; }
            cur = next;
        }
        return cur;
    }

    static List<string> Walk(string basePath, string link)
    {
        var segs = basePath.Split('/').ToList();
        foreach (var s in link.Split('/'))
        {
            if (s == "..") { if (segs.Count > 0) segs.RemoveAt(segs.Count - 1); }
            else if (s.Length > 0 && s != ".") segs.Add(s);
        }
        return segs;
    }

    static string PathOf(WzObject o, string wzName)
    {
        var parts = new List<string>();
        WzObject? cur = o;
        while (cur != null && cur is not WzFile && !(cur is WzDirectory dd && dd.Parent is WzFile))
        { parts.Add(cur.Name); cur = cur.Parent; }
        parts.Add(wzName);
        parts.Reverse();
        return string.Join('/', parts);
    }

    // ---------------------------------------------------------------- link resolution

    enum LinkKind { Uol, Inlink, Outlink }

    // Returns the resolved node, or null with a reason. imgPath = path of the owning WzImage.
    static WzObject? ResolveLink(WzFile file, string wzName, LinkKind kind, string srcPath, string imgPath, string value, out string why)
    {
        why = "";
        if (string.IsNullOrEmpty(value)) { why = "empty link value"; return null; }

        List<string> segs;
        switch (kind)
        {
            case LinkKind.Uol:
                // relative to the UOL node's PARENT
                var parent = srcPath.Split('/').ToList();
                parent.RemoveAt(parent.Count - 1);
                segs = Walk(string.Join('/', parent), value);
                break;
            case LinkKind.Inlink:
                // relative to the owning WzImage root
                segs = Walk(imgPath, value);
                break;
            default:
                // _outlink: "<WzNameNoExt>/<img>/..." from the file root
                var v = value.Split('/', StringSplitOptions.RemoveEmptyEntries).ToList();
                if (v.Count == 0) { why = "empty _outlink"; return null; }
                string head = v[0];
                if (!Eq(head + ".wz", wzName) && !Eq(head, wzName))
                { why = $"_outlink targets a different wz file '{head}' (not {wzName}) - out of scope for this file"; return null; }
                v[0] = wzName;
                segs = v;
                break;
        }
        if (segs.Count == 0 || !Eq(segs[0], wzName)) { why = "link walked above the file root"; return null; }
        return ByPath(file, segs, out why);
    }

    // Follow UOL/_inlink/_outlink chains until a concrete node. Detects cycles.
    static (WzObject? node, string status, string chain) Follow(WzFile file, string wzName, WzObject start, string startPath, string imgPath)
    {
        var seen = new HashSet<string>(StringComparer.OrdinalIgnoreCase) { startPath };
        var chain = new List<string>();
        WzObject cur = start;
        string curPath = startPath, curImg = imgPath;

        for (int hop = 0; hop < 64; hop++)
        {
            LinkKind kind; string value;
            if (cur is WzUOLProperty u) { kind = LinkKind.Uol; value = u.Value ?? ""; }
            else if (cur is WzCanvasProperty c && (c[WzCanvasProperty.InlinkPropertyName] as WzStringProperty)?.Value is string il && il.Length > 0)
            { kind = LinkKind.Inlink; value = il; }
            else if (cur is WzCanvasProperty c2 && (c2[WzCanvasProperty.OutlinkPropertyName] as WzStringProperty)?.Value is string ol && ol.Length > 0)
            { kind = LinkKind.Outlink; value = ol; }
            else return (cur, "OK", string.Join(" -> ", chain));

            chain.Add($"{kind}:{value}");
            var next = ResolveLink(file, wzName, kind, curPath, curImg, value, out string why);
            if (next == null) return (null, "DANGLING(" + why + ")", string.Join(" -> ", chain));

            string np = PathOf(next, wzName);
            if (!seen.Add(np)) return (null, "CYCLE(back to " + np + ")", string.Join(" -> ", chain));
            cur = next; curPath = np;
            // owning image of the new node, for a subsequent _inlink
            var ip = np.Split('/').ToList();
            int idx = ip.FindIndex(s => s.EndsWith(".img", StringComparison.OrdinalIgnoreCase));
            curImg = idx >= 0 ? string.Join('/', ip.Take(idx + 1)) : curImg;
        }
        return (null, "TOO-DEEP(>64 hops)", string.Join(" -> ", chain));
    }

    public static int Ls(string wzPath)
    {
        using var f = OpenFile(wzPath);
        foreach (var i in f.WzDirectory.WzImages.OrderBy(x => x.Name, StringComparer.Ordinal)) Console.WriteLine(i.Name);
        foreach (var d in f.WzDirectory.WzDirectories) Console.WriteLine("[dir] " + d.Name);
        return 0;
    }

    // WzCensus find <wz> <root> <substring>   - node NAMES containing substring, anywhere below root
    public static int Find(string wzPath, string nodePath, string needle)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var segs = nodePath.Split('/', StringSplitOptions.RemoveEmptyEntries).ToList();
        if (!Eq(segs[0], wzName)) segs.Insert(0, wzName);
        var node = ByPath(f, segs, out string why);
        if (node == null) { Console.WriteLine("NOT-FOUND: " + why); return 1; }
        int hits = 0;
        void Rec(WzObject o, string p)
        {
            if (o.Name.Contains(needle, StringComparison.OrdinalIgnoreCase)) { Console.WriteLine(p); hits++; }
            if (o is WzUOLProperty) return;
            foreach (var k in Kids(o)) Rec(k, p + "/" + k.Name);
        }
        Rec(node, string.Join('/', segs));
        Console.WriteLine($"# {hits} hits for '{needle}'");
        return 0;
    }

    public static int Dump(string wzPath, string nodePath, int maxDepth)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var segs = nodePath.Split('/', StringSplitOptions.RemoveEmptyEntries).ToList();
        if (!Eq(segs[0], wzName)) segs.Insert(0, wzName);
        var node = ByPath(f, segs, out string why);
        if (node == null) { Console.WriteLine("NOT-FOUND: " + why); return 1; }
        void Rec(WzObject o, string ind, int d)
        {
            string v = o switch
            {
                WzCanvasProperty c => $"Canvas {c.PngProperty?.Width}x{c.PngProperty?.Height} f{(int?)c.PngProperty?.Format} " +
                                      Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(
                                          c.PngProperty?.GetCompressedBytes(false) ?? Array.Empty<byte>()))[..16],
                WzUOLProperty u => "UOL -> " + u.Value,
                WzImageProperty p when !Kids(p).Any() => $"{p.PropertyType} = {p}",
                _ => o.GetType().Name,
            };
            Console.WriteLine($"{ind}{o.Name}\t{v}");
            if (d >= maxDepth || o is WzUOLProperty) return;
            foreach (var k in Kids(o)) Rec(k, ind + "  ", d + 1);
        }
        Rec(node, "", 0);
        return 0;
    }

    // Inflate EVERY canvas under the given roots. A canvas whose zlib payload is truncated
    // or whose declared w*h disagrees with the decoded buffer throws here - and throws in
    // the client too, at blit time, with no dialog.
    public static int Decode(string wzPath, string[] roots, string outFile)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var sb = new StringBuilder();
        int tot = 0, bad = 0;
        foreach (var root in roots)
        {
            var segs = root.Split('/', StringSplitOptions.RemoveEmptyEntries).ToList();
            if (!Eq(segs[0], wzName)) segs.Insert(0, wzName);
            var node = ByPath(f, segs, out string why);
            if (node == null) { sb.AppendLine($"## {root}\tROOT-NOT-FOUND: {why}"); bad++; continue; }
            int n = 0, b = 0;
            var typeCensus = new Dictionary<string, int>(StringComparer.Ordinal);
            var underscore = new List<string>();
            void Rec(WzObject o, string path)
            {
                typeCensus[o.GetType().Name] = typeCensus.GetValueOrDefault(o.GetType().Name) + 1;
                if (o.Name.Length > 0 && o.Name[0] == '_') underscore.Add(path);
                if (o is WzCanvasProperty c)
                {
                    n++;
                    var png = c.PngProperty;
                    string fk = "fmt=" + (png == null ? "NULL" : ((int)png.Format).ToString());
                    typeCensus[fk] = typeCensus.GetValueOrDefault(fk) + 1;
                    if (png == null) { b++; sb.AppendLine($"  {path}\tNULL-PNG"); }
                    else
                        try
                        {
                            var bmp = png.GetImage(false);
                            if (bmp == null) { b++; sb.AppendLine($"  {path}\tDECODE-NULL {png.Width}x{png.Height} f{(int)png.Format}"); }
                            else
                            {
                                if (bmp.Width != png.Width || bmp.Height != png.Height)
                                { b++; sb.AppendLine($"  {path}\tDIM-MISMATCH decl {png.Width}x{png.Height} got {bmp.Width}x{bmp.Height}"); }
                                bmp.Dispose();
                            }
                        }
                        catch (Exception ex) { b++; sb.AppendLine($"  {path}\tTHREW {ex.GetType().Name}: {ex.Message}"); }
                }
                if (o is WzUOLProperty) return;
                foreach (var k in Kids(o)) Rec(k, path + "/" + k.Name);
            }
            Rec(node, string.Join('/', segs));
            tot += n; bad += b;
            sb.AppendLine($"## {root}\t{n} canvases, {b} BAD");
            sb.AppendLine("   types: " + string.Join(", ", typeCensus.OrderBy(k => k.Key, StringComparer.Ordinal).Select(k => k.Key + "=" + k.Value)));
            sb.AppendLine($"   leading-underscore names: {underscore.Count}" + (underscore.Count > 0 ? " " + string.Join(", ", underscore.Take(20)) : ""));
        }
        sb.AppendLine($"# TOTAL {tot} canvases, {bad} BAD");
        File.WriteAllText(outFile, sb.ToString());
        Console.WriteLine($"decode -> {outFile}: {tot} canvases, {bad} bad");
        return bad == 0 ? 0 : 1;
    }

    // ---------------------------------------------------------------- icons mode

    public static int Icons(string wzPath, string[] imgs, string outFile)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var sb = new StringBuilder();
        sb.AppendLine($"# ICON COMPLETENESS  file={wzPath}");
        sb.AppendLine();

        foreach (var imgName in imgs)
        {
            var img = f.WzDirectory.WzImages.FirstOrDefault(i => Eq(i.Name, imgName));
            if (img == null) { sb.AppendLine($"## {imgName}\tIMAGE-NOT-PRESENT"); sb.AppendLine(); continue; }
            string imgPath = wzName + "/" + img.Name;
            if (Parsed(img) == null) { sb.AppendLine($"## {imgName}\tPARSE-FAILED"); sb.AppendLine(); continue; }

            var skillRoot = Child(img, "skill");
            if (skillRoot == null) { sb.AppendLine($"## {imgName}\tNO 'skill' NODE"); sb.AppendLine(); continue; }

            var skills = Kids(skillRoot).OrderBy(s => s.Name, StringComparer.Ordinal).ToList();
            sb.AppendLine($"## {imgPath}/skill  ({skills.Count} skills)");
            sb.AppendLine("id\t" + string.Join("\t", IconNames) + "\tnotes");

            int incomplete = 0, badDecode = 0;
            var problems = new List<string>();
            var levelShapes = new Dictionary<string, List<string>>(StringComparer.Ordinal);

            foreach (var sk in skills)
            {
                var cells = new List<string>();
                var notes = new List<string>();
                bool anyBad = false;
                foreach (var n in IconNames)
                {
                    var node = Kids(sk).FirstOrDefault(x => Eq(x.Name, n));
                    string skPath = $"{imgPath}/skill/{sk.Name}";
                    if (node == null) { cells.Add("ABSENT"); anyBad = true; notes.Add($"{n}=ABSENT"); continue; }
                    var (res, status, chain) = Follow(f, wzName, node, skPath + "/" + n, imgPath);
                    if (res == null) { cells.Add(status); anyBad = true; notes.Add($"{n}={status} [{chain}]"); continue; }

                    // must be a canvas that actually inflates
                    if (res is not WzCanvasProperty cv) { cells.Add("NOT-CANVAS:" + res.GetType().Name); anyBad = true; notes.Add($"{n}=NOT-CANVAS {res.GetType().Name}"); continue; }
                    var png = cv.PngProperty;
                    if (png == null) { cells.Add("NULL-PNG"); anyBad = true; badDecode++; notes.Add($"{n}=NULL-PNG"); continue; }
                    string dim = $"{png.Width}x{png.Height}f{(int)png.Format}";
                    try
                    {
                        var bmp = png.GetImage(false);
                        if (bmp == null) { cells.Add("DECODE-NULL " + dim); anyBad = true; badDecode++; notes.Add($"{n}=DECODE-NULL"); continue; }
                        if (bmp.Width <= 0 || bmp.Height <= 0) { cells.Add("ZERO-DIM " + dim); anyBad = true; badDecode++; notes.Add($"{n}=ZERO-DIM"); bmp.Dispose(); continue; }
                        bmp.Dispose();
                    }
                    catch (Exception ex)
                    { cells.Add("DECODE-THREW " + dim); anyBad = true; badDecode++; notes.Add($"{n}=DECODE-THREW {ex.GetType().Name}: {ex.Message}"); continue; }
                    cells.Add(dim + (chain.Length > 0 ? "@link" : ""));
                }

                if (anyBad) { incomplete++; problems.Add($"  {sk.Name}\t{string.Join("; ", notes)}"); }
                sb.AppendLine(sk.Name + "\t" + string.Join("\t", cells) + "\t" + (anyBad ? "INCOMPLETE" : ""));

                // structural shape of level children
                var lvl = Kids(sk).FirstOrDefault(x => Eq(x.Name, "level"));
                if (lvl != null)
                {
                    foreach (var lv in Kids(lvl))
                    {
                        var shape = Kids(lv).Select(k => k.Name + ":" + k.GetType().Name).OrderBy(s => s, StringComparer.Ordinal).ToList();
                        string key = string.Join(",", shape);
                        if (!levelShapes.TryGetValue(key, out var ex2)) levelShapes[key] = new List<string> { $"{sk.Name}/{lv.Name}" };
                        else if (ex2.Count < 4) ex2.Add($"{sk.Name}/{lv.Name}");
                    }
                }
                else problems.Add($"  {sk.Name}\tNO 'level' NODE");
            }

            sb.AppendLine($"### {imgName} SUMMARY: skills={skills.Count} incomplete={incomplete} undecodable={badDecode}");
            foreach (var p in problems) sb.AppendLine(p);
            sb.AppendLine($"### {imgName} LEVEL CHILD-SET SHAPES ({levelShapes.Count} distinct)");
            foreach (var kv in levelShapes.OrderByDescending(k => k.Value.Count))
                sb.AppendLine($"  [{kv.Value.Count}+ e.g. {string.Join(", ", kv.Value)}]\t{kv.Key}");
            sb.AppendLine();
            img.UnparseImage();
        }

        File.WriteAllText(outFile, sb.ToString());
        Console.WriteLine("icons -> " + outFile);
        return 0;
    }

    // ---------------------------------------------------------------- shape mode
    // Every distinct direct-child NAME under skill/<id>, with how many skills carry it.
    // The renderer reads fixed child names; a name that 000.img has on 100% of skills and
    // 2001.img has on fewer is exactly the "relied-on node" gap this ticket hunts.

    public static int Shape(string wzPath, string[] imgs, string outFile)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var sb = new StringBuilder();
        sb.AppendLine($"# SKILL-NODE SHAPE  file={wzPath}");

        foreach (var imgName in imgs)
        {
            var img = f.WzDirectory.WzImages.FirstOrDefault(i => Eq(i.Name, imgName));
            if (img == null) { sb.AppendLine($"## {imgName}\tIMAGE-NOT-PRESENT"); continue; }
            if (Parsed(img) == null) { sb.AppendLine($"## {imgName}\tPARSE-FAILED"); continue; }

            sb.AppendLine($"## {wzName}/{img.Name}");
            sb.AppendLine("   image root children: " + string.Join(", ",
                Kids(img).Select(k => k.Name + ":" + k.GetType().Name).OrderBy(s => s, StringComparer.Ordinal)));

            var skillRoot = Child(img, "skill");
            if (skillRoot == null) { sb.AppendLine("   NO 'skill' NODE"); continue; }
            var skills = Kids(skillRoot).OrderBy(s => s.Name, StringComparer.Ordinal).ToList();

            var count = new Dictionary<string, int>(StringComparer.Ordinal);
            var types = new Dictionary<string, HashSet<string>>(StringComparer.Ordinal);
            var missingBy = new Dictionary<string, List<string>>(StringComparer.Ordinal);
            foreach (var sk in skills)
                foreach (var k in Kids(sk))
                {
                    count[k.Name] = count.GetValueOrDefault(k.Name) + 1;
                    if (!types.TryGetValue(k.Name, out var t)) types[k.Name] = t = new HashSet<string>(StringComparer.Ordinal);
                    t.Add(k.GetType().Name);
                }
            foreach (var name in count.Keys)
                missingBy[name] = skills.Where(sk => !Kids(sk).Any(k => k.Name == name)).Select(sk => sk.Name).ToList();

            sb.AppendLine($"   skills={skills.Count}");
            foreach (var kv in count.OrderByDescending(k => k.Value).ThenBy(k => k.Key, StringComparer.Ordinal))
            {
                bool universal = kv.Value == skills.Count;
                sb.AppendLine($"   {kv.Key}\t{kv.Value}/{skills.Count}\t{(universal ? "UNIVERSAL" : "partial")}\t{string.Join("|", types[kv.Key])}" +
                              (universal ? "" : "\tabsent-on: " + string.Join(",", missingBy[kv.Key])));
            }
            img.UnparseImage();
        }
        File.WriteAllText(outFile, sb.ToString());
        Console.WriteLine("shape -> " + outFile);
        return 0;
    }

    // ---------------------------------------------------------------- strskill mode
    // The skill window draws a NAME and a DESCRIPTION per row, and those do not live in
    // Skill.wz - they live in String.wz/Skill.img/<id>. A skill id present in Skill.wz
    // with no String.wz row hands the renderer a null string. Same completeness question,
    // different file. Also reports level numbering, which the level-up arrow reads.
    //
    //   WzCensus strskill <skillWz> <img1,img2,...> <stringWz> <outFile>

    public static int StrSkill(string skillWz, string[] imgs, string stringWz, string outFile)
    {
        using var sf = OpenFile(skillWz);
        using var st = OpenFile(stringWz);
        var strImg = st.WzDirectory.WzImages.FirstOrDefault(i => Eq(i.Name, "Skill.img"));
        var sb = new StringBuilder();
        sb.AppendLine($"# STRING/LEVEL COMPLETENESS  skill={skillWz}  string={stringWz}");
        if (strImg == null || Parsed(strImg) == null) { sb.AppendLine("String.wz/Skill.img MISSING or unparsable"); File.WriteAllText(outFile, sb.ToString()); return 1; }

        foreach (var imgName in imgs)
        {
            var img = sf.WzDirectory.WzImages.FirstOrDefault(i => Eq(i.Name, imgName));
            if (img == null) { sb.AppendLine($"## {imgName}\tIMAGE-NOT-PRESENT"); continue; }
            if (Parsed(img) == null) { sb.AppendLine($"## {imgName}\tPARSE-FAILED"); continue; }
            var skillRoot = Child(img, "skill");
            if (skillRoot == null) { sb.AppendLine($"## {imgName}\tNO skill NODE"); continue; }
            var skills = Kids(skillRoot).OrderBy(s => s.Name, StringComparer.Ordinal).ToList();

            sb.AppendLine($"## {imgName}  ({skills.Count} skills)");
            sb.AppendLine("id\tString.wz row\tstring children\tlevels\tlevel-names\tmaxLevel(info)");
            var noRow = new List<string>();
            var noName = new List<string>();
            var strChildCount = new Dictionary<string, int>(StringComparer.Ordinal);

            foreach (var sk in skills)
            {
                string idNoPad = sk.Name.TrimStart('0');
                var row = Child(strImg, sk.Name) ?? Child(strImg, idNoPad);
                string rowState;
                string children = "-";
                if (row == null) { rowState = "ABSENT"; noRow.Add(sk.Name); }
                else
                {
                    var cn = Kids(row).Select(k => k.Name).OrderBy(s => s, StringComparer.Ordinal).ToList();
                    foreach (var c in cn) strChildCount[c] = strChildCount.GetValueOrDefault(c) + 1;
                    children = string.Join("+", cn);
                    bool hasName = cn.Any(c => Eq(c, "name"));
                    rowState = hasName ? "OK" : "NO-name";
                    if (!hasName) noName.Add(sk.Name);
                }

                var lvl = Child(sk, "level");
                var lvNames = lvl == null ? new List<string>() : Kids(lvl).Select(k => k.Name).ToList();
                var nums = lvNames.Select(n => int.TryParse(n, out var v) ? v : -1).ToList();
                string lvDesc = nums.Count == 0 ? "NONE"
                    : (nums.Contains(-1) ? "NON-NUMERIC:" + string.Join(",", lvNames)
                       : (nums.Min() == 1 && nums.Max() == nums.Count ? "1.." + nums.Max() : "GAPPY:" + string.Join(",", nums.OrderBy(x => x))));

                // every level's 'hs' names a child of the String.wz row; a missing one is a null string
                var hsBad = new List<string>();
                foreach (var lv in lvl == null ? Enumerable.Empty<WzImageProperty>() : Kids(lvl))
                    if ((Kids(lv).FirstOrDefault(k => Eq(k.Name, "hs")) as WzStringProperty)?.Value is string hv && hv.Length > 0)
                        if (row == null || Kids(row).All(k => !Eq(k.Name, hv))) hsBad.Add($"{lv.Name}->{hv}");

                sb.AppendLine($"{sk.Name}\t{rowState}\t{children}\t{lvNames.Count}\t{lvDesc}\t" +
                              (hsBad.Count == 0 ? "hs-ok" : "HS-DANGLING:" + string.Join(",", hsBad)));
            }
            sb.AppendLine($"### {imgName}: skills={skills.Count} noStringRow={noRow.Count} [{string.Join(",", noRow)}] noNameChild={noName.Count} [{string.Join(",", noName)}]");
            sb.AppendLine($"### {imgName} String.wz child coverage:");
            foreach (var kv in strChildCount.OrderByDescending(k => k.Value))
                sb.AppendLine($"   {kv.Key}\t{kv.Value}/{skills.Count}{(kv.Value == skills.Count ? "\tUNIVERSAL" : "")}");
            img.UnparseImage();
        }
        File.WriteAllText(outFile, sb.ToString());
        Console.WriteLine("strskill -> " + outFile);
        return 0;
    }

    // ---------------------------------------------------------------- links mode

    public static int Links(string wzPath, string[] roots, string outFile)
    {
        string wzName = Path.GetFileName(wzPath);
        using var f = OpenFile(wzPath);
        var sb = new StringBuilder();
        sb.AppendLine($"# LINK AUDIT  file={wzPath}");
        int totLinks = 0, totBad = 0;

        foreach (var root in roots)
        {
            var segs = root.Split('/', StringSplitOptions.RemoveEmptyEntries).ToList();
            if (!Eq(segs[0], wzName)) segs.Insert(0, wzName);
            var node = ByPath(f, segs, out string why);
            if (node == null) { sb.AppendLine($"## {root}\tROOT-NOT-FOUND: {why}"); totBad++; continue; }

            int idx = segs.FindIndex(s => s.EndsWith(".img", StringComparison.OrdinalIgnoreCase));
            string imgPath = idx >= 0 ? string.Join('/', segs.Take(idx + 1)) : string.Join('/', segs);

            int n = 0, bad = 0;
            var lines = new List<string>();

            void Scan(WzObject o, string path)
            {
                LinkKind? kind = null; string value = "";
                if (o is WzUOLProperty u) { kind = LinkKind.Uol; value = u.Value ?? ""; }
                else if (o is WzCanvasProperty c)
                {
                    if ((c[WzCanvasProperty.InlinkPropertyName] as WzStringProperty)?.Value is string il && il.Length > 0) { kind = LinkKind.Inlink; value = il; }
                    else if ((c[WzCanvasProperty.OutlinkPropertyName] as WzStringProperty)?.Value is string ol && ol.Length > 0) { kind = LinkKind.Outlink; value = ol; }
                }
                if (kind != null)
                {
                    n++;
                    var (res, status, chain) = Follow(f, wzName, o, path, imgPath);
                    if (res == null) { bad++; lines.Add($"  {path}\t{kind}={value}\t{status}\t[{chain}]"); }
                }
                if (o is WzUOLProperty) return;
                foreach (var k in Kids(o)) Scan(k, path + "/" + k.Name);
            }

            Scan(node, string.Join('/', segs));
            totLinks += n; totBad += bad;
            sb.AppendLine($"## {root}\t{n} links, {bad} BROKEN");
            foreach (var l in lines) sb.AppendLine(l);
        }

        sb.AppendLine($"# TOTAL: {totLinks} links, {totBad} BROKEN");
        File.WriteAllText(outFile, sb.ToString());
        Console.WriteLine($"links -> {outFile}: {totLinks} links, {totBad} broken");
        return totBad == 0 ? 0 : 1;
    }
}
