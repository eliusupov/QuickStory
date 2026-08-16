using System.Text;
using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// ponytail: one throwaway question, one throwaway tool. Ticket 11a only.
//
//   WzCensus <aDir> <bDir> <outDir> <wz1,wz2,...>
//
// a = the tree the live client provably parses (the v83 backup)
// b = the merged staging tree
//
// Two questions, one pass:
//  1. KINDS. What data SHAPES does b contain that a does not? A "kind" is a shape a client
//     needs a parser branch for: property CLR type, canvas pixel format, sound header length,
//     a leading-underscore name, a UOL that walks above its image.
//  2. NODES. Inside every image whose bytes moved, what nodes did the merge REMOVE or
//     OVERWRITE? An additive merge must remove nothing. Nothing else in this pipeline checks
//     this: the merge's own M2 digest compares the image to ITSELF across the save, and the
//     removed-list manifests compare stock trees, not the installed artifact.

static class Program
{
    static readonly Dictionary<string, string> KindFirst = new();

    static WzFile Open(string path)
    {
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try
            {
                f = new WzFile(path, -1, ver);
                if (f.ParseWzFile() == WzFileParseStatus.Success) return f;
            }
            catch { }
            f?.Dispose();
        }
        throw new InvalidDataException("no IV parsed " + path);
    }

    record Img(int BlockSize, int Checksum);

    static void Kinds(WzObject o, string path, HashSet<string> into, int depth)
    {
        void Add(string k) { if (into.Add(k)) KindFirst.TryAdd(k, path); }

        Add("type:" + o.GetType().Name);
        if (o.Name.Length > 0 && o.Name[0] == '_') Add("uname:" + o.Name);

        switch (o)
        {
            case WzCanvasProperty c:
                var p = c.PngProperty;
                if (p == null) { Add("canvas:NULL-PNG"); break; }
                Add($"canvas:fmt={(int)p.Format}");
                Add($"canvas:listwz={p.ListWzUsed}");
                Add($"canvas:dim<={Bucket(Math.Max(p.Width, p.Height))}");
                if (p.Width <= 0 || p.Height <= 0) Add("canvas:ZERO-DIM");
                break;
            case WzBinaryProperty b:
                Add($"sound:hdrlen={b.Header?.Length ?? -1}");
                break;
            case WzUOLProperty u:
                int up = (u.Value ?? "").Split('/').Count(s => s == "..");
                if (up > depth) Add($"uol:escapes-image(up={up},depth={depth})");
                break;
            case WzStringProperty s:
                foreach (char ch in s.Value ?? "")
                    if (ch > 0x7F && ch < 0xA1) Add($"str:ctrlchar=U+{(int)ch:X4}");
                break;
        }

        if (o is WzUOLProperty) return;
        if (depth > 40) { Add("depth:>40"); return; }
        foreach (var k in Kids(o)) Kinds(k, path + "/" + k.Name, into, depth + 1);
    }

    static IEnumerable<WzImageProperty> Kids(WzObject o) =>
        (IEnumerable<WzImageProperty>?)(o as WzImageProperty)?.WzProperties
        ?? (IEnumerable<WzImageProperty>?)(o as WzImage)?.WzProperties
        ?? Array.Empty<WzImageProperty>();

    // Compact, comparable value of one node. Canvases hash their stored compressed bytes -
    // that is the payload, and it is what a silent re-encode would change.
    static string Val(WzObject o) => o switch
    {
        WzCanvasProperty c => $"Canvas {c.PngProperty?.Width}x{c.PngProperty?.Height} f{(int?)c.PngProperty?.Format} " +
                              Convert.ToHexString(System.Security.Cryptography.SHA256.HashData(
                                  c.PngProperty?.GetCompressedBytes(false) ?? Array.Empty<byte>()))[..16],
        WzUOLProperty u => "UOL " + u.Value,
        WzBinaryProperty b => $"Sound len={b.Length} hdr={b.Header?.Length}",
        WzImageProperty p when !Kids(p).Any() => $"{p.PropertyType} = {p}",
        _ => o.GetType().Name,
    };

    static void Flatten(WzObject o, string path, Dictionary<string, string> into, int depth)
    {
        into[path] = Val(o);
        if (o is WzUOLProperty || depth > 40) return;
        foreach (var k in Kids(o)) Flatten(k, path + "/" + k.Name, into, depth + 1);
    }

    static int Bucket(int n) { int b = 16; while (b < n && b < 1 << 16) b <<= 1; return b; }

    static (Dictionary<string, Img> imgs, HashSet<string> kinds, List<string> fails) Scan(string wzPath, string wzName)
    {
        var imgs = new Dictionary<string, Img>(StringComparer.OrdinalIgnoreCase);
        var kinds = new HashSet<string>(StringComparer.Ordinal);
        var fails = new List<string>();
        using var f = Open(wzPath);
        void Walk(WzDirectory d, string prefix)
        {
            foreach (var sub in d.WzDirectories) Walk(sub, prefix + "/" + sub.Name);
            foreach (var img in d.WzImages)
            {
                string p = prefix + "/" + img.Name;
                imgs[p] = new Img(img.BlockSize, img.Checksum);
                bool ok = false;
                try { ok = img.ParseImage(); } catch (Exception ex) { fails.Add($"{p}\t{ex.GetType().Name}: {ex.Message}"); }
                if (!ok) { fails.Add(p + "\tParseImage()==false"); img.UnparseImage(); continue; }
                Kinds(img, p, kinds, 0);
                img.UnparseImage();
            }
        }
        Walk(f.WzDirectory, wzName);
        return (imgs, kinds, fails);
    }

    static Dictionary<string, Dictionary<string, string>> Flat(string wzPath, string wzName, HashSet<string> want)
    {
        var res = new Dictionary<string, Dictionary<string, string>>(StringComparer.OrdinalIgnoreCase);
        using var f = Open(wzPath);
        void Walk(WzDirectory d, string prefix)
        {
            foreach (var sub in d.WzDirectories) Walk(sub, prefix + "/" + sub.Name);
            foreach (var img in d.WzImages)
            {
                string p = prefix + "/" + img.Name;
                if (!want.Contains(p)) continue;
                var m = new Dictionary<string, string>(StringComparer.Ordinal);
                try { if (img.ParseImage()) Flatten(img, p, m, 0); else m["<PARSE-FAILED>"] = "!"; }
                catch (Exception ex) { m["<PARSE-THREW>"] = ex.Message; }
                img.UnparseImage();
                res[p] = m;
            }
        }
        Walk(f.WzDirectory, wzName);
        return res;
    }

    static int Main(string[] args)
    {
        if (args[0] == "uol") return UolCheck.Run(args[1], args[2], args[3].Split(',', StringSplitOptions.RemoveEmptyEntries), args[4]);
        if (args[0] == "find") return SkillAudit.Find(args[1], args[2], args[3]);
        if (args[0] == "decode") return SkillAudit.Decode(args[1], args[2].Split(',', StringSplitOptions.RemoveEmptyEntries), args[3]);
        if (args[0] == "dump") return SkillAudit.Dump(args[1], args[2], int.Parse(args[3]));
        if (args[0] == "ls") return SkillAudit.Ls(args[1]);
        if (args[0] == "icons") return SkillAudit.Icons(args[1], args[2].Split(',', StringSplitOptions.RemoveEmptyEntries), args[3]);
        if (args[0] == "shape") return SkillAudit.Shape(args[1], args[2].Split(',', StringSplitOptions.RemoveEmptyEntries), args[3]);
        if (args[0] == "strskill") return SkillAudit.StrSkill(args[1], args[2].Split(',', StringSplitOptions.RemoveEmptyEntries), args[3], args[4]);
        if (args[0] == "links") return SkillAudit.Links(args[1], args[2].Split(',', StringSplitOptions.RemoveEmptyEntries), args[3]);
        string aDir = args[0], bDir = args[1], outDir = args[2];
        string[] wzs = args[3].Split(',', StringSplitOptions.RemoveEmptyEntries);
        Directory.CreateDirectory(outDir);

        foreach (var wz in wzs)
        {
            string name = wz + ".wz";
            var sb = new StringBuilder();
            sb.AppendLine($"# {name}: A={Path.Combine(aDir, name)}  B={Path.Combine(bDir, name)}");
            var t0 = DateTime.Now;

            KindFirst.Clear();
            var (ia, ka, fa) = Scan(Path.Combine(aDir, name), name);
            var firstA = new Dictionary<string, string>(KindFirst);
            KindFirst.Clear();
            var (ib, kb, fb) = Scan(Path.Combine(bDir, name), name);
            var firstB = new Dictionary<string, string>(KindFirst);

            var newImgs = ib.Keys.Where(k => !ia.ContainsKey(k)).OrderBy(s => s, StringComparer.Ordinal).ToList();
            var goneImgs = ia.Keys.Where(k => !ib.ContainsKey(k)).OrderBy(s => s, StringComparer.Ordinal).ToList();
            var chgImgs = ib.Keys.Where(k => ia.ContainsKey(k) && !ia[k].Equals(ib[k]))
                                 .OrderBy(s => s, StringComparer.Ordinal).ToList();

            var onlyB = kb.Except(ka).OrderBy(s => s, StringComparer.Ordinal).ToList();
            var onlyA = ka.Except(kb).OrderBy(s => s, StringComparer.Ordinal).ToList();

            sb.AppendLine($"# images A={ia.Count} B={ib.Count}  NEW={newImgs.Count} CHANGED={chgImgs.Count} REMOVED={goneImgs.Count}");
            sb.AppendLine($"# kinds  A={ka.Count} B={kb.Count}  parse-fails A={fa.Count} B={fb.Count}");
            sb.AppendLine();
            sb.AppendLine("## KINDS ONLY IN B (merged)");
            foreach (var k in onlyB) sb.AppendLine($"  {k}\tfirst-at {firstB.GetValueOrDefault(k, "?")}");
            if (onlyB.Count == 0) sb.AppendLine("  (none)");
            sb.AppendLine("## KINDS ONLY IN A (backup)");
            foreach (var k in onlyA) sb.AppendLine($"  {k}\tfirst-at {firstA.GetValueOrDefault(k, "?")}");
            if (onlyA.Count == 0) sb.AppendLine("  (none)");
            sb.AppendLine("## PARSE FAILS");
            foreach (var l in fa) sb.AppendLine("  A " + l);
            foreach (var l in fb) sb.AppendLine("  B " + l);
            sb.AppendLine("## REMOVED IMAGES");
            foreach (var p in goneImgs) sb.AppendLine("  " + p);
            if (goneImgs.Count == 0) sb.AppendLine("  (none)");
            sb.AppendLine($"## NEW IMAGES ({newImgs.Count})");
            foreach (var p in newImgs) sb.AppendLine($"  {p}\tsize={ib[p].BlockSize}");

            // ---- node-level diff of every image whose bytes moved ----
            int totRemoved = 0, totChangedVal = 0, totAdded = 0;
            var removedLines = new List<string>();
            var changedLines = new List<string>();
            var addedCount = new List<string>();
            if (chgImgs.Count > 0)
            {
                var set = new HashSet<string>(chgImgs, StringComparer.OrdinalIgnoreCase);
                var fA = Flat(Path.Combine(aDir, name), name, set);
                var fB = Flat(Path.Combine(bDir, name), name, set);
                foreach (var p in chgImgs)
                {
                    var a = fA.GetValueOrDefault(p) ?? new Dictionary<string, string>();
                    var b = fB.GetValueOrDefault(p) ?? new Dictionary<string, string>();
                    var rem = a.Keys.Where(k => !b.ContainsKey(k)).OrderBy(s => s, StringComparer.Ordinal).ToList();
                    var chg = a.Keys.Where(k => b.ContainsKey(k) && b[k] != a[k]).OrderBy(s => s, StringComparer.Ordinal).ToList();
                    int add = b.Keys.Count(k => !a.ContainsKey(k));
                    totRemoved += rem.Count; totChangedVal += chg.Count; totAdded += add;
                    addedCount.Add($"  {p}\tA={a.Count} B={b.Count} added={add} removed={rem.Count} valuechanged={chg.Count}");
                    foreach (var k in rem) removedLines.Add($"  {k}\t{a[k]}");
                    foreach (var k in chg) changedLines.Add($"  {k}\tA[{a[k]}]  ->  B[{b[k]}]");
                }
            }
            sb.AppendLine();
            sb.AppendLine($"## NODE DIFF over {chgImgs.Count} changed images: added={totAdded} REMOVED={totRemoved} VALUE-CHANGED={totChangedVal}");
            foreach (var l in addedCount) sb.AppendLine(l);
            sb.AppendLine($"### REMOVED NODES ({totRemoved}) — must be zero for an additive merge");
            foreach (var l in removedLines) sb.AppendLine(l);
            sb.AppendLine($"### VALUE-CHANGED NODES ({totChangedVal}) — should be only the authorised force-list");
            foreach (var l in changedLines) sb.AppendLine(l);

            File.WriteAllText(Path.Combine(outDir, wz + ".census.txt"), sb.ToString());
            File.WriteAllLines(Path.Combine(outDir, wz + ".imgs.txt"), newImgs.Concat(chgImgs));
            Console.WriteLine($"{name}: NEW={newImgs.Count} CHANGED={chgImgs.Count} REMOVEDimg={goneImgs.Count} onlyB-kinds={onlyB.Count} | nodes added={totAdded} REMOVED={totRemoved} VALCHG={totChangedVal}  [{(DateTime.Now - t0).TotalSeconds:F0}s]");
        }
        return 0;
    }
}
