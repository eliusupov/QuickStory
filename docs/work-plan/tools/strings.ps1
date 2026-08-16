# Minimal read-only `strings`: ASCII (default) or UTF-16LE runs of >= MinLen printable chars.
param([Parameter(Mandatory=$true)][string]$Path, [int]$MinLen=5, [switch]$Unicode)
$b = [IO.File]::ReadAllBytes($Path)
if ($Unicode) {
  # keep only even-offset bytes whose odd neighbour is 0 -> collapses UTF-16LE to bytes
  $sb = New-Object Text.StringBuilder
  for ($i=0; $i+1 -lt $b.Length; $i+=2) {
    if ($b[$i+1] -eq 0 -and $b[$i] -ge 0x20 -and $b[$i] -lt 0x7F) { [void]$sb.Append([char]$b[$i]) } else { [void]$sb.Append([char]1) }
  }
  $s = $sb.ToString()
} else {
  $s = [Text.Encoding]::GetEncoding(28591).GetString($b)
}
[regex]::Matches($s, "[\x20-\x7E]{$MinLen,}") | ForEach-Object { $_.Value }
