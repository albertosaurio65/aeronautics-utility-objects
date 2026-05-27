param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArgs
)

chcp 65001 | Out-Null
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()
$OutputEncoding = [System.Text.UTF8Encoding]::new()
Remove-Item Env:DEBUG -ErrorAction SilentlyContinue

if (-not $GradleArgs -or $GradleArgs.Count -eq 0) {
    $GradleArgs = @("build")
}

& .\gradlew.bat @GradleArgs
