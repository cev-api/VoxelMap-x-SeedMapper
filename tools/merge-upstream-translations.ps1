$ErrorActionPreference = 'Stop'
# Merge upstream translation additions/changes by key without discarding fork keys or order.
$root = Split-Path $PSScriptRoot -Parent
$utf8 = [Text.UTF8Encoding]::new($false)
$paths = git -c core.autocrlf=false -C $root diff --name-only --diff-filter=U | Where-Object { $_ -match '/lang/' }
foreach ($path in $paths) {
    $oursText = (git -C $root show "99f1423a:$path") -join "`n"
    $ours = $oursText | ConvertFrom-Json
    $base = ((git -C $root show "959c81e6:$path") -join "`n") | ConvertFrom-Json
    $theirs = ((git -C $root show "27395f4a:$path") -join "`n") | ConvertFrom-Json
    $additions = [Collections.Generic.List[string]]::new()
    foreach ($property in $theirs.PSObject.Properties) {
        $old = $base.PSObject.Properties[$property.Name]
        $local = $ours.PSObject.Properties[$property.Name]
        if ($null -eq $local) {
            $key = ConvertTo-Json $property.Name -Compress
            $value = ConvertTo-Json $property.Value -Compress
            $additions.Add("  ${key}: $value")
        } elseif ($null -ne $old -and $local.Value -eq $old.Value -and $property.Value -ne $old.Value) {
            $key = [regex]::Escape((ConvertTo-Json $property.Name -Compress))
            $value = ConvertTo-Json $property.Value -Compress
            $oursText = [regex]::Replace($oursText, "(?m)^(\s*${key}:\s*).*?(,?)$", [Text.RegularExpressions.MatchEvaluator]{param($m) $m.Groups[1].Value + $value + $m.Groups[2].Value})
        }
    }
    if ($additions.Count -gt 0) {
        $oursText = $oursText.TrimEnd().TrimEnd('}').TrimEnd() + ",`n" + ($additions -join ",`n") + "`n}"
    }
    $null = $oursText | ConvertFrom-Json
    [IO.File]::WriteAllText((Join-Path $root $path), ($oursText -replace "`r?`n", "`r`n") + "`r`n", $utf8)
}
Write-Output "Merged $(@($paths).Count) translation files by key."
