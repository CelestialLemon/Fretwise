#!/usr/bin/env bash
set -euo pipefail

# Pushes play-along .mp4 videos onto a connected Android device into Fretwise's external videos folder.
# Usage: ./scripts/push_videos.sh [path/to/videos_dir]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_DIR="$SCRIPT_DIR/../../guitar-playalong/output"
SRC_DIR="${1:-$DEFAULT_DIR}"
PKG="com.celestiallemon.tuner"
DEST_DIR="/sdcard/Android/data/$PKG/files/videos"

if [ ! -d "$SRC_DIR" ]; then
  echo "Video directory not found: $SRC_DIR" >&2
  echo "Pass the folder containing your play-along .mp4 files as the first argument." >&2
  exit 1
fi

adb shell mkdir -p "$DEST_DIR"

count=0
shopt -s nullglob
for file in "$SRC_DIR"/*.mp4; do
  echo "Pushing $(basename "$file")..."
  adb push "$file" "$DEST_DIR/"
  count=$((count + 1))
done

echo "Done — pushed $count video(s) to $DEST_DIR"
