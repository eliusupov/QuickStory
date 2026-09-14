using MapleLib.WzLib;
using MapleLib.WzLib.WzProperties;
using System.Security.Cryptography;

// Native String.wz edit only. Server StatEffect: +10*level, 340000 ms, cooldown 0.
// Never saves over its source; inspect the staged archive before installing it.
if (args.Length != 2) throw new ArgumentException("Usage: NimbleTooltip <source String.wz> <new staging directory>");
string source = Path.GetFullPath(args[0]), stage = Path.GetFullPath(args[1]);
if (Directory.Exists(stage)) throw new IOException("Use a new staging directory (preserves existing artifacts).");
string[] protectedRoots = [@"D:\games\MapleStory", @"D:\games\MSv84\client", @"D:\games\dreamms",
    @"D:\games\MapleStory\Server\porting-resources\wz-data\v84"];
bool Inside(string path, string root) => path.Equals(root, StringComparison.OrdinalIgnoreCase)
    || path.StartsWith(root + Path.DirectorySeparatorChar, StringComparison.OrdinalIgnoreCase);
if ((Inside(stage, protectedRoots[0]) && !Inside(stage, @"D:\games\MapleStory\Server"))
    || protectedRoots.Skip(1).Any(root => Inside(stage, root)))
    throw new IOException("Staging destination is protected client data.");

WzFile Open(string path)
{
    foreach (var version in new[] { WzMapleVersion.GMS, WzMapleVersion.BMS, WzMapleVersion.EMS })
    {
        var file = new WzFile(path, -1, version);
        try { if (file.ParseWzFile() == WzFileParseStatus.Success) return file; }
        catch { file.Dispose(); throw; }
        file.Dispose();
    }
    throw new IOException("Cannot parse " + path);
}
Dictionary<string, string> Snapshot(WzFile file)
{
    var result = new Dictionary<string, string>(StringComparer.Ordinal);
    void Properties(IEnumerable<WzImageProperty> props, string path)
    {
        foreach (var prop in props)
        {
            string key = path + "/" + prop.Name;
            object? value = prop.WzValue;
            if (value is Array) throw new IOException("Unsupported array at " + key);
            result.Add(key, prop.GetType().Name + "\t" + (value is WzPropertyCollection ? "" : value?.ToString()));
            if (prop.WzProperties != null) Properties(prop.WzProperties, key);
        }
    }
    void Directory(WzDirectory dir, string path)
    {
        result.Add(path, "directory");
        foreach (var child in dir.WzDirectories) Directory(child, path + "/" + child.Name);
        foreach (var image in dir.WzImages)
        {
            if (!image.ParseImage()) throw new IOException("Cannot parse " + image.Name);
            string key = path + "/" + image.Name;
            result.Add(key, "image");
            Properties(image.WzProperties, key);
        }
    }
    Directory(file.WzDirectory, file.WzDirectory.Name);
    return result;
}
string Hash(string path) { using var stream = File.OpenRead(path); return Convert.ToHexString(SHA256.HashData(stream)); }
string beforeHash = Hash(source);
using var input = Open(source);
var before = Snapshot(input);
var image = input.WzDirectory.WzImages.Single(img => img.Name == "Skill.img");
string[] ids = ["0001002", "10001002", "20001002", "20011002"];
var expected = new Dictionary<string, string>(StringComparer.Ordinal);
var changes = new List<string> { "path\told\tnew" };
foreach (string id in ids)
{
    var replacements = new Dictionary<string, string> {
        ["desc"] = @"[Master Level : 3]\nIncreases movement speed for 340 sec.\n#cCooldown: None.#",
        ["h1"] = "MP -4; speed +10 for 340 sec.",
        ["h2"] = "MP -7; speed +20 for 340 sec.",
        ["h3"] = "MP -10; speed +30 for 340 sec."
    };
    foreach (var (name, text) in replacements)
    {
        if (image[id]?[name] is not WzStringProperty prop) throw new IOException("Missing string " + id + "/" + name);
        string key = input.WzDirectory.Name + "/Skill.img/" + id + "/" + name;
        changes.Add(key + "\t" + prop.Value + "\t" + text);
        prop.Value = text;
        expected.Add(key, nameof(WzStringProperty) + "\t" + text);
    }
}
image.Changed = true;
System.IO.Directory.CreateDirectory(stage);
string output = Path.Combine(stage, "String.wz"), previousCwd = Environment.CurrentDirectory;
try { Environment.CurrentDirectory = stage; input.SaveToDisk(output); }
finally { Environment.CurrentDirectory = previousCwd; }
using var saved = Open(output);
var after = Snapshot(saved);
if (before.Count != after.Count || before.Any(entry => !after.TryGetValue(entry.Key, out var value)
    || value != expected.GetValueOrDefault(entry.Key, entry.Value)))
    throw new IOException("Verification failed: unexpected node or value change. Do not install.");
if (Hash(source) != beforeHash) throw new IOException("Source changed during staging. Do not install.");
File.Copy(source, Path.Combine(stage, "String.original.wz"));
File.WriteAllLines(Path.Combine(stage, "changes.tsv"), changes);
string report = $"Verified {before.Count} nodes; exactly {expected.Count} intended string replacements; source unchanged.\n"
    + $"Original SHA256: {beforeHash}\nStaged SHA256: {Hash(output)}\n";
File.WriteAllText(Path.Combine(stage, "verification.txt"), report);
Console.WriteLine(report + output);
