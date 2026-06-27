$ErrorActionPreference = "Stop"

Write-Host "Building project and test runner..."
.\gradlew.bat build testClasses

Write-Host "Running automated Minecraft smoke tests..."
java -Djava.awt.headless=false -cp "build/classes/java/test" com.submarine.automated.SubmarineTestRunner

if ((Test-Path "test-result.txt") -and ((Get-Content -Raw "test-result.txt").Trim() -eq "PASS")) {
    Write-Host "All submarine tests passed."
    exit 0
}

Write-Error "Submarine tests failed."
exit 1
