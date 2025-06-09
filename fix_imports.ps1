$files = Get-ChildItem -Path "src/movie" -Filter "*.java" -Recurse
foreach ($file in $files) {
    $content = Get-Content $file.FullName -Raw
    $content = $content -replace "jakarta\.servlet", "javax.servlet"
    $content = $content -replace "import utils\.", "import common."
    Set-Content $file.FullName $content
} 