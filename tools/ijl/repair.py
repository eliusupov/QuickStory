"""Repair the installed IJL proxy's verified export/forwarder ABI, never in-place."""
import argparse
import hashlib
from pathlib import Path
import struct

import pefile

SOURCE_SHA256 = "82796ecc6b3b13ae16ae0bac2bdb303f5afa50abd39b7715bfcd93e6a417e79d"
# Original embedded Intel DLL (ijl15.dll.bak), not guessed from the proxy's exports.
ORDINALS = {"ijlGetLibVersion": 1, "ijlInit": 2, "ijlFree": 3,
            "ijlRead": 4, "ijlWrite": 5, "ijlErrorStr": 6,
            "LoadDLLsFromDirectory": 7}


def repair(source: bytes) -> bytes:
    if hashlib.sha256(source).hexdigest() != SOURCE_SHA256:
        raise ValueError("Unrecognized IJL proxy; no repair attempted")
    pe = pefile.PE(data=source)
    exports = pe.DIRECTORY_ENTRY_EXPORT
    assert exports.struct.Base == 1 and exports.struct.NumberOfFunctions == 7
    assert exports.struct.NumberOfNames == 7
    symbols = {s.name.decode("ascii"): s for s in exports.symbols}
    assert symbols.keys() == ORDINALS.keys()
    output = bytearray(source)
    touched = set()

    def put(offset, data):
        output[offset:offset + len(data)] = data
        touched.update(range(offset, offset + len(data)))

    functions = pe.get_offset_from_rva(exports.struct.AddressOfFunctions)
    name_ordinals = pe.get_offset_from_rva(exports.struct.AddressOfNameOrdinals)
    for index, name in enumerate(sorted(symbols)):
        symbol = symbols[name]
        ordinal = ORDINALS[name]
        put(functions + 4 * (ordinal - 1), struct.pack("<I", symbol.address))
        put(name_ordinals + 2 * index, struct.pack("<H", ordinal - 1))
        if name == "LoadDLLsFromDirectory":
            continue
        offset = pe.get_offset_from_rva(symbol.address)
        # Compiler prologue before a tail jump shifts both return address and args.
        assert source[offset:offset + 5] == bytes.fromhex("558becff25")
        assert source[offset + 9:offset + 11] == bytes.fromhex("5dc3")
        put(offset, b"\x90" * 3)

    assert len(output) == len(source)
    assert all(a == b or i in touched for i, (a, b) in enumerate(zip(source, output)))
    fixed = pefile.PE(data=bytes(output))
    for symbol in fixed.DIRECTORY_ENTRY_EXPORT.symbols:
        name = symbol.name.decode("ascii")
        assert symbol.ordinal == ORDINALS[name]
        assert symbol.address == symbols[name].address  # named resolution unchanged
    return bytes(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("output", type=Path)
    args = parser.parse_args()
    if args.source.resolve() == args.output.resolve():
        parser.error("Output must differ from source")
    source = args.source.read_bytes()
    fixed = repair(source)
    args.output.write_bytes(fixed)
    changes = [i for i, (a, b) in enumerate(zip(source, fixed)) if a != b]
    print(f"Repaired {len(changes)} bytes; same {len(fixed)}-byte DLL")
    print("Changed file offsets: " + ", ".join(f"{i:X}" for i in changes))
    print("SHA256 " + hashlib.sha256(fixed).hexdigest())


if __name__ == "__main__":
    main()
