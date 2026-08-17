using System;
using System.Collections.Generic;
using System.Globalization;
using System.IO;
using System.Linq;
using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// ponytail: measurement only. Emits one TSV row per map image so the VR / camera-bounds
// claims can be checked against the archive instead of trusted. It writes nothing back.
//
//   WzVR <Map.wz> [out.tsv]
//
// Columns (tab separated, header on line 1):
//   mapid  hasVR  vrL vrT vrR vrB  fhL fhT fhR fhB  nFh  nBack  backTypes  backNoImg
//   bgFlatW bgFlatH  bgTiled
//
// hasVR is 0..4 = how many of the four VR* leaves exist under info/. The client reads all
// four independently (each with its own default), so a partial set is legal and is why the
// count matters. Missing values are emitted as the empty string, never as 0 - 0 is a legal
// coordinate and conflating them is exactly the bug this tool exists to avoid.
// fh* is the bounding box over every foothold segment endpoint; empty when a map has none.
// backTypes is the distinct set of back/<n>/type values (0 = single image, >0 = tiled or
// scrolling, i.e. the layer keeps covering the screen past the authored extent).
// backNoImg counts back layers whose bS (bitmap set) is empty - those draw nothing.
// bgFlatW/bgFlatH is the largest canvas among the map's type-0 (non-repeating) back
// layers, resolved through Map.wz/Back/<bS>.img/back/<no>; that is how wide the drawn
// backdrop actually is. bgTiled is 1 when at least one layer repeats (type != 0), i.e.
// the backdrop covers any viewport size on that axis regardless of canvas size.

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

    static WzPropertyCollection? Props(WzObject o) => o switch
    {
        WzImage im => im.WzProperties,
        WzImageProperty ip => ip.WzProperties,
        _ => null,
    };

    static WzImageProperty? Child(WzObject? o, string name)
    {
        var p = Props(o!);
        if (p == null) return null;
        foreach (var c in p) if (c.Name == name) return c;
        return null;
    }

    static int? Int(WzObject? o, string name)
    {
        var c = Child(o, name);
        if (c == null) return null;
        try { return c.GetInt(); } catch { return null; }
    }

    // Back/<set>.img/(back|ani)/<no> -> canvas pixel size. Animated layers keep their
    // frames one level deeper, so descend to frame 0. Returns (0,0) when unresolvable -
    // callers must treat that as "unknown", never as "zero-sized".
    static (int, int) CanvasSize(Dictionary<string, WzImage> sets, string set, bool ani, int no)
    {
        if (!sets.TryGetValue(set, out var img)) return (0, 0);
        bool parsed = img.Parsed;
        if (!parsed) img.ParseImage();
        try
        {
            WzObject? n = Child(img, ani ? "ani" : "back");
            n = Child(n, no.ToString(CultureInfo.InvariantCulture));
            if (n is not WzCanvasProperty && n != null) n = Child(n, "0");
            if (n is WzCanvasProperty c && c.PngProperty != null)
                return (c.PngProperty.Width, c.PngProperty.Height);
            return (0, 0);
        }
        catch { return (0, 0); }
        finally { if (!parsed) img.UnparseImage(); }
    }

    static void Walk(WzDirectory d, List<WzImage> outp)
    {
        foreach (var im in d.WzImages) outp.Add(im);
        foreach (var sub in d.WzDirectories) Walk(sub, outp);
    }

    static int Main(string[] args)
    {
        if (args.Length < 1)
        {
            Console.Error.WriteLine("usage: WzVR <Map.wz> [out.tsv]");
            return 2;
        }
        var (file, err) = TryOpen(args[0]);
        if (file == null) { Console.Error.WriteLine(err); return 1; }

        var images = new List<WzImage>();
        Walk(file.WzDirectory, images);

        // Back/<set>.img lookup, for resolving a back layer's actual canvas size.
        var backImgs = new Dictionary<string, WzImage>(StringComparer.OrdinalIgnoreCase);
        foreach (var im in images)
            if (im.FullPath.Contains("Back", StringComparison.OrdinalIgnoreCase)
                && im.Name.EndsWith(".img", StringComparison.Ordinal))
                backImgs[im.Name[..^4]] = im;

        TextWriter w = args.Length > 1 ? new StreamWriter(args[1]) : Console.Out;
        w.WriteLine(string.Join("\t", "mapid", "hasVR", "vrL", "vrT", "vrR", "vrB",
                                "fhL", "fhT", "fhR", "fhB", "nFh", "nBack", "backTypes", "backNoImg",
                                "bgFlatW", "bgFlatH", "bgTiled"));

        int n = 0;
        foreach (var im in images)
        {
            // map images are <9 digits>.img and live under Map/MapN/
            if (!im.Name.EndsWith(".img", StringComparison.Ordinal)) continue;
            string id = im.Name[..^4];
            if (id.Length != 9 || !id.All(char.IsDigit)) continue;

            bool parsed = im.Parsed;
            if (!parsed) im.ParseImage();
            try
            {
                var info = Child(im, "info");
                int? vl = Int(info, "VRLeft"), vt = Int(info, "VRTop"),
                     vr = Int(info, "VRRight"), vb = Int(info, "VRBottom");
                int has = (vl.HasValue ? 1 : 0) + (vt.HasValue ? 1 : 0)
                        + (vr.HasValue ? 1 : 0) + (vb.HasValue ? 1 : 0);

                int fhL = int.MaxValue, fhT = int.MaxValue, fhR = int.MinValue, fhB = int.MinValue, nFh = 0;
                var fhRoot = Child(im, "foothold");
                if (fhRoot != null)
                    foreach (var layer in Props(fhRoot)!)
                        foreach (var grp in Props(layer) ?? new WzPropertyCollection(null))
                            foreach (var seg in Props(grp) ?? new WzPropertyCollection(null))
                            {
                                int? x1 = Int(seg, "x1"), y1 = Int(seg, "y1"),
                                     x2 = Int(seg, "x2"), y2 = Int(seg, "y2");
                                if (!x1.HasValue || !y1.HasValue || !x2.HasValue || !y2.HasValue) continue;
                                nFh++;
                                fhL = Math.Min(fhL, Math.Min(x1.Value, x2.Value));
                                fhR = Math.Max(fhR, Math.Max(x1.Value, x2.Value));
                                fhT = Math.Min(fhT, Math.Min(y1.Value, y2.Value));
                                fhB = Math.Max(fhB, Math.Max(y1.Value, y2.Value));
                            }

                int nBack = 0, backNoImg = 0, bgW = 0, bgH = 0, bgTiled = 0;
                var types = new SortedSet<int>();
                var backRoot = Child(im, "back");
                if (backRoot != null)
                    foreach (var b in Props(backRoot)!)
                    {
                        nBack++;
                        int t = Int(b, "type") ?? 0;
                        types.Add(t);
                        var bs = Child(b, "bS");
                        string bsv = "";
                        try { bsv = bs?.GetString() ?? ""; } catch { }
                        if (bsv.Length == 0) { backNoImg++; continue; }
                        if (t != 0) { bgTiled = 1; continue; }
                        var (cw, ch) = CanvasSize(backImgs, bsv, Int(b, "ani") == 1, Int(b, "no") ?? 0);
                        if (cw > bgW) bgW = cw;
                        if (ch > bgH) bgH = ch;
                    }

                string S(int? v) => v.HasValue ? v.Value.ToString(CultureInfo.InvariantCulture) : "";
                string F(int v) => nFh == 0 ? "" : v.ToString(CultureInfo.InvariantCulture);
                w.WriteLine(string.Join("\t", id, has, S(vl), S(vt), S(vr), S(vb),
                                        F(fhL), F(fhT), F(fhR), F(fhB), nFh, nBack,
                                        string.Join(",", types), backNoImg, bgW, bgH, bgTiled));
                n++;
            }
            finally
            {
                if (!parsed) im.UnparseImage();
            }
        }
        w.Flush();
        if (w != Console.Out) w.Dispose();
        Console.Error.WriteLine($"maps: {n}");
        return 0;
    }
}
