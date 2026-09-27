#!/bin/bash

# A helper script to create the macOS .icns file from a square 1024x1024 PNG.
#
# Usage: scripts/create_icons.sh src/jvmMain/resources/logo.png
# The resulting logo.icns is written next to the source image.
#
# A complete .icns has TEN members. The largest one (icon_512x512@2x.png, i.e. 1024x1024) is what macOS uses for
# Finder previews, Get Info and the App Store, so a nine-member .icns is incomplete even though the Dock looks fine.
# The script verifies the member count at the end -- the .icns checked in before 2026-09 was missing that member.

set -euo pipefail

if [ $# -ne 1 ]; then
    echo "usage: $0 <square-1024x1024-source.png>" >&2
    exit 2
fi

SOURCE="$1"
# Strip only the final extension; ${1%.*} keeps directories and any dots in the path intact, unlike splitting on '.'.
ICONSET="${SOURCE%.*}.iconset"
ICNS="${SOURCE%.*}.icns"

rm -rf "$ICONSET"
mkdir -p "$ICONSET"

# NOTE: `sips -z H W` forces exact dimensions and does NOT preserve the aspect ratio. That is fine here because the
# source is square (1024x1024); feeding a non-square image to this script would silently distort every member.
sips -z 16 16     "$SOURCE" --out "$ICONSET/icon_16x16.png"      > /dev/null
sips -z 32 32     "$SOURCE" --out "$ICONSET/icon_16x16@2x.png"   > /dev/null
sips -z 32 32     "$SOURCE" --out "$ICONSET/icon_32x32.png"      > /dev/null
sips -z 64 64     "$SOURCE" --out "$ICONSET/icon_32x32@2x.png"   > /dev/null
sips -z 128 128   "$SOURCE" --out "$ICONSET/icon_128x128.png"    > /dev/null
sips -z 256 256   "$SOURCE" --out "$ICONSET/icon_128x128@2x.png" > /dev/null
sips -z 256 256   "$SOURCE" --out "$ICONSET/icon_256x256.png"    > /dev/null
sips -z 512 512   "$SOURCE" --out "$ICONSET/icon_256x256@2x.png" > /dev/null
sips -z 512 512   "$SOURCE" --out "$ICONSET/icon_512x512.png"    > /dev/null
sips -z 1024 1024 "$SOURCE" --out "$ICONSET/icon_512x512@2x.png" > /dev/null

iconutil -c icns "$ICONSET" -o "$ICNS"
rm -rf "$ICONSET"

# Verify: round-trip the .icns back to an iconset and count the members.
VERIFY="$(mktemp -d)"
trap 'rm -rf "$VERIFY"' EXIT
iconutil -c iconset "$ICNS" -o "$VERIFY/check.iconset"
MEMBERS="$(find "$VERIFY/check.iconset" -name '*.png' | wc -l | tr -d ' ')"
if [ "$MEMBERS" -ne 10 ]; then
    echo "ERROR: $ICNS has $MEMBERS members, expected 10" >&2
    exit 1
fi
echo "$ICNS written with $MEMBERS members"
