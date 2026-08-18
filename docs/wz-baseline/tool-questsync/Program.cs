using System.Xml.Linq;
using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;

// WzQuestSync - project the SERVER's quest counts onto a client Quest.wz.
//
// WHY THIS EXISTS
// The "quest requirements are halved" rule is not code anywhere. It is a data edit:
// every `count` under Quest.wz/Check.img (mob + item requirements) and every NEGATIVE
// `count` under Act.img (the items a quest takes on hand-in) is stored at ceil(n/2),
// never below 1. It was applied to BOTH the server's XML (wz/Quest.wz/*.img.xml) and
// the client's binary Quest.wz, because the client is what actually gates completion:
// the quest window's "x/y" denominator, the completion cue and `autoComplete` all come
// from the client's own Check.img. A server that asks for less than the client does
// soft-locks the quest - the server stops counting at its number and the client never
// reaches its own.
//
// The v84 cutover installed a pristine 2010 Quest.wz, which dropped the client half of
// that edit. This tool puts it back, and is re-runnable after any future client swap.
//
// It does NOT re-derive the halving. The server XML is the single source of truth for
// what every requirement is; this copies those numbers across so the two cannot drift.
// Paths the server does not have are left alone.
//
//   WzQuestSync <clientQuest.wz> <serverWzQuestDir> <outDir>
//
// Writes <outDir>/Quest.wz. It never writes to a game install - see the outDir guard.
// Verify the result before installing it:
//   WzPeek dump <outDir>/Quest.wz Check.img   and diff against the same dump of the
//   original; the only differing lines must be `.../count`.

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

    /// <summary>Every "&lt;imgName&gt;/a/b/count" leaf in one of Cosmic's Quest XMLs.</summary>
    static Dictionary<string, int> ReadServerCounts(string xmlPath, string imgName)
    {
        var res = new Dictionary<string, int>();
        void Walk(XElement node, string prefix)
        {
            foreach (var c in node.Elements())
            {
                string name = c.Attribute("name")?.Value ?? "";
                string p = prefix + "/" + name;
                if (c.Name.LocalName == "imgdir") Walk(c, p);
                else if (name == "count") res[p] = int.Parse(c.Attribute("value")!.Value);
            }
        }
        Walk(XDocument.Load(xmlPath).Root!, imgName);
        return res;
    }

    static int Sync(WzImage img, string imgName, Dictionary<string, int> want, List<string> log)
    {
        int changed = 0;
        void Walk(WzImageProperty node, string prefix)
        {
            // leaf properties (int, string) carry no collection at all
            if (node.WzProperties == null) return;
            foreach (var c in node.WzProperties)
            {
                string p = prefix + "/" + c.Name;
                if (c.Name == "count" && c is WzIntProperty ip)
                {
                    if (want.TryGetValue(p, out int v) && ip.Value != v)
                    {
                        log.Add($"{p}\t{ip.Value}\t->\t{v}");
                        ip.Value = v;
                        changed++;
                    }
                }
                else
                {
                    Walk(c, p);
                }
            }
        }
        img.ParseImage();
        foreach (var c in img.WzProperties) Walk(c, imgName + "/" + c.Name);
        if (changed > 0) img.Changed = true;
        return changed;
    }

    static int Refuse(string why)
    {
        Console.Error.WriteLine("REFUSED: " + why);
        return 2;
    }

    static int Main(string[] args)
    {
        if (args.Length != 3)
        {
            Console.Error.WriteLine("usage: WzQuestSync <clientQuest.wz> <serverWzQuestDir> <outDir>");
            return 1;
        }
        string clientWz = Path.GetFullPath(args[0]), serverDir = Path.GetFullPath(args[1]), outDir = Path.GetFullPath(args[2]);

        // Same rule as WzMerge: never write into a game install, and never onto the source.
        if (Directory.Exists(outDir) && Directory.EnumerateFiles(outDir, "*.exe").Any())
            return Refuse($"<outDir> contains .exe - that is a game install, not a staging directory ({outDir})");
        if (string.Equals(Path.GetDirectoryName(clientWz), outDir, StringComparison.OrdinalIgnoreCase))
            return Refuse("<outDir> is the client's own directory; SaveToDisk truncates before it reads");
        Directory.CreateDirectory(outDir);

        var check = ReadServerCounts(Path.Combine(serverDir, "Check.img.xml"), "Check.img");
        var act = ReadServerCounts(Path.Combine(serverDir, "Act.img.xml"), "Act.img");
        Console.WriteLine($"server counts: Check.img {check.Count}, Act.img {act.Count}");

        var (file, err) = TryOpen(clientWz);
        if (file == null) return Refuse(err);

        var log = new List<string>();
        int n = 0;
        foreach (var img in file.WzDirectory.WzImages)
        {
            if (img.Name == "Check.img") n += Sync(img, "Check.img", check, log);
            else if (img.Name == "Act.img") n += Sync(img, "Act.img", act, log);
        }
        Console.WriteLine($"counts rewritten: {n}");
        File.WriteAllLines(Path.Combine(outDir, "questsync-changes.tsv"), log);

        if (n == 0)
        {
            Console.WriteLine("nothing to do - client already matches the server");
            file.Dispose();
            return 0;
        }

        // MapleLib's scratch .TEMP is relative to the working directory, and SaveToDisk
        // truncates its destination up front - both reasons to be inside outDir here.
        string outWz = Path.Combine(outDir, "Quest.wz");
        string cwd = Directory.GetCurrentDirectory();
        try
        {
            Directory.SetCurrentDirectory(outDir);
            file.SaveToDisk(outWz);
        }
        finally { Directory.SetCurrentDirectory(cwd); }
        file.Dispose();
        Console.WriteLine($"wrote {outWz} ({new FileInfo(outWz).Length} bytes)");
        return 0;
    }
}
