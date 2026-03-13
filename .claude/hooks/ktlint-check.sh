#!/bin/bash
INPUT=$(cat)
FILE_PATH=$(echo "$INPUT" | jq -r '.tool_input.file_path // empty')

if [[ -z "$FILE_PATH" || "$FILE_PATH" != *.kt ]]; then
  exit 0
fi

# Resolve project root from Claude Code's cwd, fallback to script's relative location
PROJECT_DIR=$(echo "$INPUT" | jq -r '.cwd // empty')
if [[ -z "$PROJECT_DIR" ]]; then
  PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
fi

# Make path relative to project root for Gradle
RELATIVE_PATH="${FILE_PATH#"$PROJECT_DIR"/}"

OUTPUT=$(cd "$PROJECT_DIR" && ./gradlew ktlintFile -PfilePath="$RELATIVE_PATH" --quiet --console=plain 2>&1)
if [[ $? -ne 0 ]]; then
  # Extract ktlint violation lines that couldn't be auto-fixed
  VIOLATIONS=$(echo "$OUTPUT" | grep '\.kt:')
  if [[ -n "$VIOLATIONS" ]]; then
    echo "ktlint violations that could not be auto-fixed:"
    echo "$VIOLATIONS"
  else
    echo "ktlintFile task failed:"
    echo "$OUTPUT" | tail -20
  fi
  exit 2
fi

exit 0
