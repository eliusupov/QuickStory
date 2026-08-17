using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using MapleLib.WzLib;

// ponytail: sibling of ../tool (WzDump). Same TryOpen, but a QUERY tool instead of a
// diff tool - it answers "what is actually in the pristine archive at this path" and
// "which images contain a leaf <name>=<value>". No CLI framework, positional args.
//
//   WzPeek dump <file.wz> <Path/Inside/Wz> [maxDepth]
//   WzPeek scan <file.wz> <leafName> <value> [pathFilterSubstring]
//
// dump: prints every descendant of the node with its leaf value.
// scan: walks EVERY image in the file and prints each property path whose leaf name is
//       <leafName> and whose value renders exactly as <value>. This is how you prove a
//       negative ("no map places mob X") - it looks at all of them.

static class Program
{
    static (WzFile? file, string err) TryOpen(string path)
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
                f?.Dispose();
                errs.Add($"{ver}: {ex.GetType().Name} {ex.Message}");
                continue;
            }
            if (status == WzFileParseStatus.Success) return (f, "");
            f.Dispose();
            errs.Add($"{ver}: {status}");
        }
        return (null, "no encryption version parsed: " + string.Join(" | ", errs));
    }

    // WzProperties lives on WzImage and WzImageProperty separately, not on WzObject.
    static MapleLib.WzLib.WzPropertyCollection? Props(WzObject o) => o switch
    {
        WzImage im => im.WzProperties,
        WzImageProperty ip => ip.WzProperties,
        _ => null,
    };

    static string Val(WzObject o)
    {
        try
        {
            var v = o.WzValue;
            if (v == null) return "";
            if (v is MapleLib.WzLib.WzPropertyCollection) return "";
            if (v.GetType().IsArray) return "<" + v.GetType().Name + ">";
            string s = v.ToString() ?? "";
            return s.Length > 300 ? s[..300] + "..." : s;
        }
        catch (Exception ex) { return "<err " + ex.GetType().Name + ">"; }
    }

    // navigate Directory/Directory/.../Image.img/prop/prop
    static WzObject? Nav(WzFile file, string path)
    {
        var parts = path.Split('/', StringSplitOptions.RemoveEmptyEntries);
        WzObject cur = file.WzDirectory;
        int i = 0;
        // the first segment may be the wz root directory name (e.g. "Map")
        if (parts.Length > 0 && string.Equals(parts[0], file.WzDirectory.Name, StringComparison.OrdinalIgnoreCase)) i = 1;
        for (; i < parts.Length; i++)
        {
            string p = parts[i];
            if (cur is WzDirectory d)
            {
                WzObject? next = d.WzDirectories.FirstOrDefault(x => string.Equals(x.Name, p, StringComparison.OrdinalIgnoreCase));
                next ??= d.WzImages.FirstOrDefault(x => string.Equals(x.Name, p, StringComparison.OrdinalIgnoreCase));
                if (next == null) return null;
                if (next is WzImage im) im.ParseImage();
                cur = next;
            }
            else
            {
                var props = Props(cur);
                WzObject? next = props?.FirstOrDefault(x => string.Equals(x.Name, p, StringComparison.OrdinalIgnoreCase));
                if (next == null) return null;
                cur = next;
            }
        }
        return cur;
    }

    static void Print(WzObject o, string prefix, int depth, int maxDepth)
    {
        if (o is WzDirectory dir)
        {
            foreach (var sub in dir.WzDirectories) Console.WriteLine($"{prefix}/{sub.Name}\tWzDirectory\t");
            foreach (var img in dir.WzImages) Console.WriteLine($"{prefix}/{img.Name}\tWzImage\t");
            return;
        }
        var props = Props(o);
        if (props == null) return;
        foreach (var c in props)
        {
            string p = prefix + "/" + c.Name;
            string v = Val(c);
            Console.WriteLine($"{p}\t{c.GetType().Name}\t{v}");
            if (depth < maxDepth) Print(c, p, depth + 1, maxDepth);
        }
    }

    static void Scan(WzFile file, string leaf, string value, string filter)
    {
        long imgs = 0;
        void Rec(WzObject o, string prefix, string imgPath)
        {
            var props = Props(o);
            if (props == null) return;
            foreach (var c in props)
            {
                string p = prefix + "/" + c.Name;
                if (string.Equals(c.Name, leaf, StringComparison.OrdinalIgnoreCase))
                {
                    string v = Val(c);
                    if (v == value) Console.WriteLine($"HIT\t{p}\t{v}");
                }
                Rec(c, p, imgPath);
            }
        }
        void WalkDir(WzDirectory dir, string prefix)
        {
            foreach (var sub in dir.WzDirectories) WalkDir(sub, prefix + "/" + sub.Name);
            foreach (var img in dir.WzImages)
            {
                string p = prefix + "/" + img.Name;
                if (filter.Length > 0 && !p.Contains(filter, StringComparison.OrdinalIgnoreCase)) continue;
                bool ok;
                try { ok = img.ParseImage(); } catch { ok = false; }
                imgs++;
                if (ok) Rec(img, p, p);
                else Console.Error.WriteLine($"[PARSE-FAIL] {p}");
                img.UnparseImage();
                if (imgs % 2000 == 0) Console.Error.WriteLine($"  ...{imgs} images");
            }
        }
        WalkDir(file.WzDirectory, file.WzDirectory.Name);
        Console.Error.WriteLine($"scanned {imgs} images");
    }

    static int Main(string[] args)
    {
        if (args.Length < 3)
        {
            Console.Error.WriteLine("usage: WzPeek dump <file.wz> <path> [maxDepth]");
            Console.Error.WriteLine("       WzPeek scan <file.wz> <leafName> <value> [pathFilter]");
            return 2;
        }
        var (file, err) = TryOpen(args[1]);
        if (file == null) { Console.Error.WriteLine(err); return 1; }
        try
        {
            if (args[0] == "dump")
            {
                int maxDepth = args.Length > 3 ? int.Parse(args[3]) : 6;
                var node = Nav(file, args[2]);
                if (node == null) { Console.WriteLine("NOT-FOUND\t" + args[2]); return 0; }
                Console.WriteLine($"FOUND\t{args[2]}\t{node.GetType().Name}");
                Print(node, args[2], 1, maxDepth);
            }
            else if (args[0] == "portals")
            {
                // args[2..] = map ids. One condensed line per portal: idx pn pt x y tm tn script
                foreach (var id in args.Skip(2))
                {
                    string imgPath = $"Map/Map{id[0]}/{id}.img";
                    var node = Nav(file, imgPath + "/portal");
                    if (node == null) { Console.WriteLine($"{id}\tNO-PORTAL-NODE (image {(Nav(file, imgPath) == null ? "MISSING" : "present")})"); continue; }
                    foreach (var c in Props(node)!)
                    {
                        string G(string n) { var x = Props(c)!.FirstOrDefault(q => q.Name == n); return x == null ? "-" : Val(x); }
                        Console.WriteLine($"{id}\t{c.Name}\tpn={G("pn")}\tpt={G("pt")}\tx={G("x")}\ty={G("y")}\ttm={G("tm")}\ttn={G("tn")}\tscript={G("script")}");
                    }
                }
            }
            else if (args[0] == "life")
            {
                foreach (var id in args.Skip(2))
                {
                    string imgPath = $"Map/Map{id[0]}/{id}.img";
                    var node = Nav(file, imgPath + "/life");
                    if (node == null) { Console.WriteLine($"{id}\tNO-LIFE-NODE (image {(Nav(file, imgPath) == null ? "MISSING" : "present")})"); continue; }
                    foreach (var c in Props(node)!)
                    {
                        string G(string n) { var x = Props(c)!.FirstOrDefault(q => q.Name == n); return x == null ? "-" : Val(x); }
                        Console.WriteLine($"{id}\t{c.Name}\ttype={G("type")}\tid={G("id")}\tx={G("x")}\ty={G("y")}\tf={G("f")}\thide={G("hide")}\tmobTime={G("mobTime")}");
                    }
                }
            }
            else if (args[0] == "scan")
            {
                Scan(file, args[2], args[3], args.Length > 4 ? args[4] : "");
            }
            else { Console.Error.WriteLine("unknown mode " + args[0]); return 2; }
        }
        finally { file.Dispose(); }
        return 0;
    }
}
