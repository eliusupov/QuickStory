using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// ponytail: second pass, same tool. A v83 client resolves a UOL through ResMan; an
// unresolvable one is a failed IWzNameSpace::GetItem, which surfaces as a _com_error
// ("Unknown error 0x%0lX") - the exact dialog this ticket is chasing. The merge is
// selective, so a v84 subtree can arrive with links pointing at v84 nodes that were
// never brought across. Nothing else in the pipeline checks that.
//
//   WzCensus uol <bDir> <outFile> <wz1,wz2,...> <imageListDir>
//
// imageListDir holds <Wz>.imgs.txt, one image path per line (the NEW+CHANGED set from
// the main pass). Only those images are walked; their link targets are resolved against
// the whole merged file, parsing target images on demand.

static class UolCheck
{
    public static int Run(string bDir, string outFile, string[] wzs, string listDir)
    {
        var report = new List<string>();
        foreach (var wz in wzs)
        {
            string name = wz + ".wz";
            string listFile = Path.Combine(listDir, wz + ".imgs.txt");
            if (!File.Exists(listFile)) { report.Add($"# {name}: no image list, skipped"); continue; }
            var want = new HashSet<string>(File.ReadAllLines(listFile).Where(l => l.Length > 0), StringComparer.OrdinalIgnoreCase);

            using var f = OpenFile(Path.Combine(bDir, name));
            int checkedUols = 0, dangling = 0;
            var bad = new List<string>();

            void Walk(WzDirectory d, string prefix)
            {
                foreach (var sub in d.WzDirectories) Walk(sub, prefix + "/" + sub.Name);
                foreach (var img in d.WzImages)
                {
                    string p = prefix + "/" + img.Name;
                    if (!want.Contains(p)) continue;
                    if (!img.ParseImage()) { bad.Add($"{p}\tPARSE-FAILED"); continue; }
                    Scan(img, p);
                }
            }

            void Scan(WzObject o, string path)
            {
                if (o is WzUOLProperty u)
                {
                    checkedUols++;
                    if (!Resolve(f, u, path, out string why)) { dangling++; bad.Add($"{path}\t-> {u.Value}\t{why}"); }
                    return;
                }
                foreach (var k in Kids(o)) Scan(k, path + "/" + k.Name);
            }

            Walk(f.WzDirectory, name);
            report.Add($"# {name}: {checkedUols} UOLs in {want.Count} new/changed images, {dangling} UNRESOLVABLE");
            report.AddRange(bad.Select(b => "  " + b));
            Console.WriteLine($"{name}: {checkedUols} UOLs checked, {dangling} unresolvable");
        }
        File.WriteAllLines(outFile, report);
        return 0;
    }

    static IEnumerable<WzImageProperty> Kids(WzObject o) =>
        (IEnumerable<WzImageProperty>?)(o as WzImageProperty)?.WzProperties
        ?? (IEnumerable<WzImageProperty>?)(o as WzImage)?.WzProperties
        ?? Array.Empty<WzImageProperty>();

    static WzFile OpenFile(string path)
    {
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try { f = new WzFile(path, -1, ver); if (f.ParseWzFile() == WzFileParseStatus.Success) return f; } catch { }
            f?.Dispose();
        }
        throw new InvalidDataException("no IV parsed " + path);
    }

    // Walk the link exactly as a client would: relative to the UOL's PARENT, '..' goes up,
    // and a step that leaves the image continues into the file's directory tree.
    static bool Resolve(WzFile file, WzUOLProperty u, string uolPath, out string why)
    {
        why = "";
        string v = u.Value ?? "";
        // path of the UOL's parent, as segments
        var segs = uolPath.Split('/').ToList();
        segs.RemoveAt(segs.Count - 1);                 // drop the UOL's own name -> parent
        foreach (var s in v.Split('/'))
        {
            if (s == "..") { if (segs.Count == 0) { why = "walked above the file root"; return false; } segs.RemoveAt(segs.Count - 1); }
            else if (s.Length > 0) segs.Add(s);
        }
        // resolve segs[1..] under the file (segs[0] is "<Wz>.wz")
        WzObject cur = file.WzDirectory;
        for (int i = 1; i < segs.Count; i++)
        {
            WzObject? next = cur switch
            {
                WzDirectory d => (WzObject?)d.WzDirectories.FirstOrDefault(x => string.Equals(x.Name, segs[i], StringComparison.OrdinalIgnoreCase))
                                 ?? d.WzImages.FirstOrDefault(x => string.Equals(x.Name, segs[i], StringComparison.OrdinalIgnoreCase)),
                WzImage im => Parsed(im)?.FirstOrDefault(x => string.Equals(x.Name, segs[i], StringComparison.OrdinalIgnoreCase)),
                WzImageProperty ip => Kids(ip).FirstOrDefault(x => string.Equals(x.Name, segs[i], StringComparison.OrdinalIgnoreCase)),
                _ => null,
            };
            if (next == null) { why = "missing segment '" + segs[i] + "' at " + string.Join('/', segs.Take(i)); return false; }
            cur = next;
        }
        return true;
    }

    static WzPropertyCollection? Parsed(WzImage im)
    {
        if (!im.Parsed) { try { if (!im.ParseImage()) return null; } catch { return null; } }
        return im.WzProperties;
    }
}
