# IJL screenshot forwarding repair

The owner's proxy assigns different ordinals from its embedded Intel DLL and puts
`push ebp; mov ebp,esp` before every tail jump. Screenshot calls resolve by ordinal:
the captured client calls `0xAA0CF8 -> [0xB4138C] -> ordinal 2`, incorrectly landing
on `ijlErrorStr`. The extra saved EBP then becomes the JPEG callee's return address.
The crash at `001AE661` is stack execution; its caller is `00766D06` in the Scroll
Lock (`VK_SCROLL`, `0x91`) screenshot path. No projectile hook occurs in that stack.

`repair.py` accepts only SHA256 `82796ecc6b3b13ae16ae0bac2bdb303f5afa50abd39b7715bfcd93e6a417e79d`.
It changes six 3-byte prologues and export function/name-ordinal tables only. Named
exports keep their addresses; JPEG ordinals match the original `ijl15.dll.bak`.
The proxy's loader, resources, unpacking hooks and embedded JPEG DLL stay intact.

```powershell
python tools/ijl/repair.py D:\games\MapleStory\ijl15.dll tools/ijl/ijl15.fixed.dll
tools/ijl/build.cmd
```

The dedicated x86 host checks all seven ordinal/name bindings, actual version/error
calls, and 20 actual JPEG init/free cycles using the client's `0x4E68` buffer size.
The proxy writes its embedded `ijl15.dll.bak` into the host's working directory;
these checks run inside `tools/ijl`, never the protected client directory.

Installation requires a separate owner exception for root `ijl15.dll`. Preserve
the original as a repo-side backup, close the client, verify both hashes, then
replace only that file. No agent launches the game.
