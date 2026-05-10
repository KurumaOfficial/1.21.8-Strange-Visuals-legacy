$files = Get-ChildItem -Path 'c:\Users\gameg\Desktop\Holo_Project\Strange\New2\src\client\java\ru\strange\client\module\impl' -Recurse -Filter '*.java'
$allSettings = @()
foreach ($f in $files) {
    $lines = Get-Content $f.FullName -Encoding UTF8
    foreach ($line in $lines) {
        $ms = [regex]::Matches($line, 'Setting\s*\(\s*"([^"]+)"')
        foreach ($m in $ms) {
            $allSettings += "$($f.Name)|$($m.Groups[1].Value)"
        }
    }
}
$allSettings | Sort-Object -Unique
