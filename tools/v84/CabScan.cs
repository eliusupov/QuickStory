using System;
using System.IO;

public static class CabScan
{
    // Scan a file for MSCF cabinet headers and validate them structurally.
    // A candidate is accepted only if signature=="MSCF", reserved1==0, reserved2==0,
    // reserved3==0, version==1.3, and cbCabinet fits inside the file from that offset.
    public static void Run(string path)
    {
        long len = new FileInfo(path).Length;
        using (var fs = File.OpenRead(path))
        {
            const int CHUNK = 32 * 1024 * 1024;
            byte[] buf = new byte[CHUNK + 64];
            long baseOff = 0;
            int carry = 0;
            while (true)
            {
                int n = fs.Read(buf, carry, CHUNK);
                if (n <= 0) break;
                int total = carry + n;
                int limit = total - 64;
                for (int i = 0; i <= limit; i++)
                {
                    if (buf[i] != 0x4D || buf[i + 1] != 0x53 || buf[i + 2] != 0x43 || buf[i + 3] != 0x46) continue;
                    if (BitConverter.ToUInt32(buf, i + 4) != 0) continue;   // reserved1
                    if (BitConverter.ToUInt32(buf, i + 12) != 0) continue;  // reserved2
                    if (BitConverter.ToUInt32(buf, i + 20) != 0) continue;  // reserved3
                    if (buf[i + 0x18] != 3 || buf[i + 0x19] != 1) continue; // versionMinor=3 versionMajor=1
                    uint cb = BitConverter.ToUInt32(buf, i + 8);
                    long off = baseOff + i;
                    if (cb == 0 || off + cb > len) continue;
                    ushort flags = BitConverter.ToUInt16(buf, i + 0x1E);
                    Console.WriteLine(string.Format(
                        "offset={0} cbCabinet={1} coffFiles={2} cFolders={3} cFiles={4} flags=0x{5:x4} setID={6} iCabinet={7}",
                        off, cb,
                        BitConverter.ToUInt32(buf, i + 16),
                        BitConverter.ToUInt16(buf, i + 0x1A),
                        BitConverter.ToUInt16(buf, i + 0x1C),
                        flags,
                        BitConverter.ToUInt16(buf, i + 0x20),
                        BitConverter.ToUInt16(buf, i + 0x22)));
                }
                // carry the last 64 bytes so a header straddling a chunk boundary is still seen
                carry = 64;
                Buffer.BlockCopy(buf, total - carry, buf, 0, carry);
                baseOff += total - carry;
                if (n < CHUNK) break;
            }
        }
        Console.WriteLine("filelen=" + len);
    }

    // Copy [offset, offset+length) out of src into dst.
    public static void Carve(string src, string dst, long offset, long length)
    {
        using (var i = File.OpenRead(src))
        using (var o = File.Create(dst))
        {
            i.Position = offset;
            byte[] b = new byte[4 * 1024 * 1024];
            long left = length;
            while (left > 0)
            {
                int want = (int)Math.Min(b.Length, left);
                int got = i.Read(b, 0, want);
                if (got <= 0) throw new IOException("short read, " + left + " bytes remaining");
                o.Write(b, 0, got);
                left -= got;
            }
        }
    }
}
