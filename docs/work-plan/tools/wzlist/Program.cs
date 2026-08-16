using MapleLib.WzLib;

// ponytail: read-only structure lister for ticket 35. One question - "what top-level
// nodes does this archive contain, and under which IV does it parse" - so one tool, no
// flags beyond the two positionals.
//
//   WzList <path-to-wz> [depth=1]
//
// Prints: IV/version used, WzFile.Version, and every directory/image name down to
// <depth> levels, one per line, prefixed by kind (D=directory, I=image).
// It never parses image contents, so a 2 GB archive costs the directory table only.

static class Program
{
    static int Main(string[] args)
    {
        if (args.Length < 1) { Console.Error.WriteLine("WzList <wz> [depth]"); return 2; }
        string path = args[0];
        int depth = args.Length > 1 ? int.Parse(args[1]) : 1;

        WzFile? file = null;
        WzMapleVersion used = default;
        var errs = new List<string>();
        foreach (var ver in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
        {
            WzFile? f = null;
            try
            {
                f = new WzFile(path, -1, ver);
                var st = f.ParseWzFile();
                if (st == WzFileParseStatus.Success) { file = f; used = ver; break; }
                errs.Add($"{ver}: {st}");
            }
            catch (Exception ex) { errs.Add($"{ver}: {ex.GetType().Name} {ex.Message}"); }
            f?.Dispose();
        }
        if (file == null)
        {
            Console.WriteLine("PARSE-FAILED " + path);
            foreach (var e in errs) Console.WriteLine("  " + e);
            return 1;
        }

        Console.WriteLine($"FILE {path}");
        Console.WriteLine($"IV {used}");
        Console.WriteLine($"VERSION {file.Version}");
        Console.WriteLine($"HEADER ident={file.Header.Ident} fsize={file.Header.FSize} fstart={file.Header.FStart} copyright={file.Header.Copyright.Trim()}");

        int dirs = 0, imgs = 0;
        void Walk(WzDirectory d, string prefix, int lvl)
        {
            foreach (var sub in d.WzDirectories.OrderBy(x => x.Name, StringComparer.OrdinalIgnoreCase))
            {
                dirs++;
                Console.WriteLine($"D {prefix}/{sub.Name}");
                if (lvl < depth) Walk(sub, prefix + "/" + sub.Name, lvl + 1);
            }
            foreach (var img in d.WzImages.OrderBy(x => x.Name, StringComparer.OrdinalIgnoreCase))
            {
                imgs++;
                Console.WriteLine($"I {prefix}/{img.Name} {img.BlockSize}");
            }
        }
        Walk(file.WzDirectory, "", 1);
        Console.WriteLine($"TOTAL dirs={dirs} imgs={imgs}");
        file.Dispose();
        return 0;
    }
}
