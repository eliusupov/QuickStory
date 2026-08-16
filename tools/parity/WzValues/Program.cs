using System.Text;
using System.Text.RegularExpressions;
using MapleLib.WzLib;

// ponytail: one generic value-dumper, not four per-file tools. Every question this ticket
// asks ("what ids does v84 have", "did mob stats move", "which npcs are placed") is the same
// query against a packed .wz: walk to depth N, print path + scalar value, filter by regex.
// The comparison itself is done in Python against the server's already-extracted XML tree,
// because that side needs no WZ library at all.
//
//   WzValues <wzFile> <outTsv> <maxDepth> [pathRegex]
//
// Output is TAB-separated: <path>\t<type>\t<value>.  <path> starts below the archive root,
// e.g. "Consume/0200.img/02000000/info/price".  Containers print with an empty value, so a
// presence-only question ("does this id exist") and a value question ("what is its maxHP")
// read off the same file.  The regex, when given, filters which paths are PRINTED; the walk
// always descends to maxDepth so a filtered child is never lost to a non-matching parent.

static class Program
{
    static long _images, _rows;
    static readonly List<string> Failures = new();

    static (WzFile? file, string err) TryOpen(string path)
    {
        var errs = new List<string>();
        // A wrong IV throws InvalidDataException from ParseWzFile - a failed candidate, not
        // a failed file. Same three-IV fallback as docs/wz-baseline/tool.
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try
            {
                f = new WzFile(path, -1, ver);
                if (f.ParseWzFile() == WzFileParseStatus.Success) return (f, "");
                errs.Add($"{ver}: parse status not Success");
            }
            catch (Exception ex) { errs.Add($"{ver}: {ex.GetType().Name} {ex.Message}"); }
            f?.Dispose();
        }
        return (null, string.Join(" | ", errs));
    }

    static int Main(string[] args)
    {
        if (args.Length < 3)
        {
            Console.Error.WriteLine("usage: WzValues <wzFile> <outTsv> <maxDepth> [pathRegex]");
            return 2;
        }
        string wzPath = args[0], outPath = args[1];
        int maxDepth = int.Parse(args[2]);
        Regex? filter = args.Length > 3 && args[3].Length > 0 ? new Regex(args[3], RegexOptions.Compiled) : null;

        var (file, err) = TryOpen(wzPath);
        if (file == null) { Console.Error.WriteLine($"open failed: {wzPath}: {err}"); return 3; }

        Directory.CreateDirectory(Path.GetDirectoryName(Path.GetFullPath(outPath))!);
        using var w = new StreamWriter(outPath, false, new UTF8Encoding(false));

        void Emit(string path, string type, string value)
        {
            if (filter != null && !filter.IsMatch(path)) return;
            w.Write(path); w.Write('\t'); w.Write(type); w.Write('\t');
            // values are single-line by construction here, but a stray newline in a string
            // property would corrupt the TSV for every downstream reader.
            w.Write(value.Replace('\t', ' ').Replace('\n', ' ').Replace('\r', ' '));
            w.Write('\n');
            _rows++;
        }

        void Props(WzPropertyCollection? props, string prefix, int depth)
        {
            if (props == null) return;
            foreach (var p in props)
            {
                string path = prefix + "/" + p.Name;
                object? v = null;
                // Canvas/sound WzValue materialises a bitmap or an audio buffer; never ask for it.
                if (p.PropertyType is not (WzPropertyType.Canvas or WzPropertyType.Sound
                        or WzPropertyType.SubProperty or WzPropertyType.Convex or WzPropertyType.Null))
                {
                    try { v = p.WzValue; } catch { v = null; }
                }
                Emit(path, p.PropertyType.ToString(), v?.ToString() ?? "");
                if (depth < maxDepth) Props(p.WzProperties, path, depth + 1);
            }
        }

        void Dir(WzDirectory d, string prefix)
        {
            foreach (var sub in d.WzDirectories)
            {
                string p = prefix.Length == 0 ? sub.Name : prefix + "/" + sub.Name;
                Emit(p, "Directory", "");
                Dir(sub, p);
            }
            foreach (var img in d.WzImages)
            {
                string p = prefix.Length == 0 ? img.Name : prefix + "/" + img.Name;
                Emit(p, "Image", img.BlockSize.ToString());
                // ParseImage() reports failure by RETURNING FALSE without throwing, and the
                // WzProperties getter throws that bool away - a failed image then looks exactly
                // like an empty one. Read the bool. (H1 in docs/wz-baseline/TOOL-NOTES.md)
                bool ok; string e = "ParseImage returned false";
                try { ok = img.ParseImage(); }
                catch (Exception ex) { ok = false; e = $"{ex.GetType().Name}: {ex.Message}"; }
                _images++;
                if (!ok) { Failures.Add($"{p}\t{e}"); img.UnparseImage(); continue; }
                if (maxDepth > 0) Props(img.WzProperties, p, 1);
                img.UnparseImage();   // keep memory flat across ~60k images
                if (_images % 2000 == 0) Console.Error.WriteLine($"  {_images} images, {_rows} rows");
            }
        }

        Dir(file.WzDirectory, "");
        file.Dispose();
        w.Flush();

        Console.Error.WriteLine($"{wzPath}: {_images} images parsed, {Failures.Count} parse failures, {_rows} rows -> {outPath}");
        foreach (var f in Failures) Console.Error.WriteLine("  [PARSE-FAIL] " + f);
        // Parse failures are reported, not fatal: Sound.wz/BgmGL.img fails in every tree
        // (MapleLib limitation) and a caller measuring Mob.wz should not be blocked by it.
        return 0;
    }
}
