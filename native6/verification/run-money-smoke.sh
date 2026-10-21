#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
kotlinc "$ROOT/app/src/main/java/com/alsafa/native6/Money.kt" "$ROOT/verification/MoneySmoke.kt" -include-runtime -d "$OUT/money-smoke.jar"
java -jar "$OUT/money-smoke.jar"
