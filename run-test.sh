#!/usr/bin/env bash
set -euo pipefail

echo "Building project and test runner..."
./gradlew build testClasses

echo "Running automated Minecraft smoke tests..."
java -Djava.awt.headless=false -cp "build/classes/java/test" com.submarine.automated.SubmarineTestRunner

if [[ -f test-result.txt && "$(tr -d '\r\n' < test-result.txt)" == "PASS" ]]; then
  echo "All submarine tests passed."
  exit 0
fi

echo "Submarine tests failed." >&2
exit 1
