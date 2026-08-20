using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using MapleLib.WzLib;

// ponytail: sibling of ../tool (WzDump). Same TryOpen, but a QUERY tool instead of a
// diff tool - it answers "what is actually in the pristine archive at this path" and
// "which images contain a leaf <name>=<value>". No CLI framework, positional args.
//
//   WzPeek dump   <file.wz> <Path/Inside/Wz> [maxDepth]
//   WzPeek scan   <file.wz> <leafName> <value> [pathFilterSubstring]
//   WzPeek digest <Map.wz>                     (every map, one line each)
//   WzPeek fh     <Map.wz> <mapId>...          (one line per foothold)
//   WzPeek drift  <v83 Map.wz> <v84 Map.wz> [out.tsv]
//
// dump: prints every descendant of the node with its leaf value.
// scan: walks EVERY image in the file and prints each property path whose leaf name is
//       <leafName> and whose value renders exactly as <value>. This is how you prove a
//       negative ("no map places mob X") - it looks at all of them.
// digest: the three sections the SERVER reads, hashed per map, so a whole-tree comparison
//       against our XML is one pass instead of one process per map. fhgeom hashes the
//       platform GEOMETRY (ids excluded) and fhid hashes id->geometry: geom equal + id
//       different is exactly the renumber-without-moving case that misplaces every NPC.

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

    static string G(WzObject n, string name)
    {
        var x = Props(n)?.FirstOrDefault(q => string.Equals(q.Name, name, StringComparison.Ordinal));
        return x == null ? "-" : Val(x);
    }

    static string Sha(IEnumerable<string> lines)
    {
        using var h = System.Security.Cryptography.SHA1.Create();
        var b = h.ComputeHash(System.Text.Encoding.UTF8.GetBytes(string.Join("\n", lines)));
        return Convert.ToHexString(b)[..12].ToLowerInvariant();
    }

    // foothold/<layer>/<group>/<id> -> (id, layer, group, x1,y1,x2,y2)
    static List<(int id, string geom, string layer, string group)> Footholds(WzObject img)
    {
        var outp = new List<(int, string, string, string)>();
        var root = Props(img)?.FirstOrDefault(p => string.Equals(p.Name, "foothold", StringComparison.Ordinal));
        if (root == null) return outp;
        foreach (var layer in Props(root)!)
            foreach (var grp in Props(layer)!)
                foreach (var fh in Props(grp)!)
                    if (int.TryParse(fh.Name, out int id))
                        outp.Add((id, $"{G(fh, "x1")},{G(fh, "y1")},{G(fh, "x2")},{G(fh, "y2")}", layer.Name, grp.Name));
        return outp;
    }

    static List<string> Section(WzObject img, string section, string[] fields)
    {
        var outp = new List<string>();
        var root = Props(img)?.FirstOrDefault(p => string.Equals(p.Name, section, StringComparison.Ordinal));
        if (root == null) return outp;
        foreach (var c in Props(root)!)
        {
            if (!int.TryParse(c.Name, out _)) continue;   // portal/life slots are numeric; skip stray leaves
            outp.Add(c.Name + "\t" + string.Join("\t", fields.Select(f => G(c, f))));
        }
        return outp;
    }

    static readonly string[] LifeFields = { "type", "id", "x", "y", "fh", "cy", "rx0", "rx1", "f", "hide", "mobTime" };
    static readonly string[] PortalFields = { "pn", "pt", "x", "y", "tm", "tn", "script" };

    sealed record Portal(string Slot, string Name, string X, string Y, string TargetMap, string TargetName);

    static Dictionary<int, Dictionary<string, Portal>> Portals(WzFile file)
    {
        var maps = new Dictionary<int, Dictionary<string, Portal>>();
        void Walk(WzDirectory dir)
        {
            foreach (var sub in dir.WzDirectories) Walk(sub);
            foreach (var img in dir.WzImages)
            {
                string id = img.Name.EndsWith(".img", StringComparison.Ordinal) ? img.Name[..^4] : img.Name;
                if (!int.TryParse(id, out int map) || !img.ParseImage()) continue;
                try
                {
                    var section = Props(img)?.FirstOrDefault(p => p.Name == "portal");
                    if (section == null) continue;
                    var ports = new Dictionary<string, Portal>();
                    foreach (var node in Props(section)!)
                    {
                        if (!int.TryParse(node.Name, out _)) continue;
                        ports[node.Name] = new Portal(node.Name, G(node, "pn"), G(node, "x"), G(node, "y"),
                            G(node, "tm"), G(node, "tn"));
                    }
                    maps[map] = ports;
                }
                finally { img.UnparseImage(); }
            }
        }
        Walk(file.WzDirectory);
        return maps;
    }

    static bool Direct(Portal p) => int.TryParse(p.TargetMap, out int id) && id != 999999999 && p.TargetName is not ("" or "-");

    static string Route(Portal p) => $"{p.Name}->{p.TargetMap}/{p.TargetName}";

    static Portal? Named(Dictionary<int, Dictionary<string, Portal>> maps, int map, string name) =>
        maps.TryGetValue(map, out var ports) ? ports.Values.FirstOrDefault(p => p.Name == name) : null;

    static string Distance(Portal a, Portal b) =>
        int.TryParse(a.X, out int ax) && int.TryParse(a.Y, out int ay) && int.TryParse(b.X, out int bx) && int.TryParse(b.Y, out int by)
            ? Math.Sqrt((long)(ax - bx) * (ax - bx) + (long)(ay - by) * (ay - by)).ToString("F1") : "-";

    static void Drift(string v83Path, string v84Path, TextWriter output)
    {
        var (f83, e83) = TryOpen(v83Path);
        var (f84, e84) = TryOpen(v84Path);
        if (f83 == null || f84 == null) throw new InvalidOperationException($"open failed: v83={e83}; v84={e84}");
        try
        {
            var v83 = Portals(f83);
            var v84 = Portals(f84);
            output.WriteLine("#kind\tmap\tportal\tv83_route\tv84_route\tv83_destination\tv84_destination\tdistance");
            foreach (var (map, newPorts) in v84.OrderBy(x => x.Key))
            {
                if (!v83.TryGetValue(map, out var oldPorts)) continue; // v84-only map: no V83 route to preserve
                foreach (var newPortal in newPorts.Values.OrderBy(p => int.Parse(p.Slot)))
                {
                    if (!Direct(newPortal)) continue;
                    var oldPortal = Named(v83, map, newPortal.Name);
                    if (oldPortal == null || !Direct(oldPortal)) continue; // no V83 direct route to compare
                    if (Route(oldPortal) != Route(newPortal))
                        output.WriteLine($"ROUTE_CHANGED\t{map}\t{newPortal.Name}\t{Route(oldPortal)}\t{Route(newPortal)}\t-\t-\t-");
                    var oldLanding = Named(v83, int.Parse(oldPortal.TargetMap), oldPortal.TargetName);
                    if (oldLanding == null || !v84.TryGetValue(int.Parse(oldPortal.TargetMap), out var destination)) continue;
                    string oldDestination = $"{oldPortal.TargetMap}/{oldLanding.Name}[{oldLanding.Slot}]@{oldLanding.X},{oldLanding.Y}";
                    if (!destination.TryGetValue(oldLanding.Slot, out var actual))
                        output.WriteLine($"DESTINATION_SLOT_MISSING\t{map}\t{newPortal.Name}\t{Route(oldPortal)}\t{Route(newPortal)}\t{oldDestination}\t-\t-");
                    else if (actual.Name != oldLanding.Name)
                        output.WriteLine($"DESTINATION_SLOT_CHANGED\t{map}\t{newPortal.Name}\t{Route(oldPortal)}\t{Route(newPortal)}\t{oldDestination}\t{oldPortal.TargetMap}/{actual.Name}[{actual.Slot}]@{actual.X},{actual.Y}\t{Distance(oldLanding, actual)}");
                }
            }
        }
        finally { f83.Dispose(); f84.Dispose(); }
    }

    static void Digest(WzFile file)
    {
        Console.WriteLine("#map\tfh\tfhgeom\tfhid\tlife\tlifekey\tlifefh\tportal\tportalkey");
        var dirs = new List<WzDirectory>();
        void Collect(WzDirectory d) { dirs.Add(d); foreach (var s in d.WzDirectories) Collect(s); }
        Collect(file.WzDirectory);
        foreach (var dir in dirs)
            foreach (var img in dir.WzImages.OrderBy(i => i.Name, StringComparer.Ordinal))
            {
                string id = img.Name.EndsWith(".img", StringComparison.Ordinal) ? img.Name[..^4] : img.Name;
                if (!long.TryParse(id, out _)) continue;
                try { if (!img.ParseImage()) { Console.Error.WriteLine("[PARSE-FAIL] " + img.Name); continue; } }
                catch { Console.Error.WriteLine("[PARSE-THROW] " + img.Name); continue; }

                var fh = Footholds(img);
                var life = Section(img, "life", LifeFields);
                var portal = Section(img, "portal", PortalFields);
                // life rows cite foothold ids; that citation is what the client resolves, so hash it apart
                var lifeFh = life.Select(l => l.Split('\t') is var p && p.Length > 5 ? p[0] + ":" + p[5] : l);

                Console.WriteLine(string.Join("\t",
                    id,
                    fh.Count,
                    Sha(fh.Select(f => $"{f.layer}|{f.group}|{f.geom}").OrderBy(s => s, StringComparer.Ordinal)),
                    Sha(fh.OrderBy(f => f.id).Select(f => $"{f.id}|{f.layer}|{f.group}|{f.geom}")),
                    life.Count, Sha(life), Sha(lifeFh),
                    portal.Count, Sha(portal)));
                img.UnparseImage();
            }
    }

    static int Main(string[] args)
    {
        if ((args.Length == 3 || args.Length == 4) && args[0] == "drift")
        {
            if (args.Length == 4) Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(args[3]))!);
            using var output = args.Length == 4 ? new StreamWriter(args[3], false) : null;
            Drift(args[1], args[2], output ?? Console.Out);
            return 0;
        }
        if (args.Length == 2 && args[0] == "digest")
        {
            var (f2, e2) = TryOpen(args[1]);
            if (f2 == null) { Console.Error.WriteLine(e2); return 1; }
            try { Digest(f2); } finally { f2.Dispose(); }
            return 0;
        }
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
            else if (args[0] == "fh")
            {
                foreach (var id in args.Skip(2))
                {
                    var node = Nav(file, $"Map/Map{id[0]}/{id}.img");
                    if (node == null) { Console.WriteLine($"{id}\tMISSING"); continue; }
                    foreach (var f in Footholds(node).OrderBy(f => f.id))
                        Console.WriteLine($"{id}\t{f.id}\t{f.layer}\t{f.group}\t{f.geom}");
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
