#!/usr/bin/env bash
# Prints the release notes for a tag: the matching CHANGELOG.tacit.md section followed by the install instructions.
# Usage: release-notes.sh <tag> [changelog] [install-template]
set -euo pipefail

tag="${1:?usage: release-notes.sh <tag> [changelog] [install-template]}"
changelog="${2:-CHANGELOG.tacit.md}"
install_template="${3:-.github/release-install.md}"
version="${tag#v}"

section="$(awk -v v="$version" '
  /^## / { if (found) exit; if ($2 == v) { found = 1; next } }
  found { print }
' "$changelog")"

# Trim leading and trailing blank lines
section="$(printf '%s\n' "$section" | sed -e '/./,$!d' | sed -e ':a' -e '/^\n*$/{$d;N;ba' -e '}')"

if [[ -z "${section//[[:space:]]/}" ]]; then
  echo "error: no (or empty) '## $version' section in $changelog" >&2
  exit 1
fi

printf '%s\n\n---\n\n' "$section"
sed -e "s/{{version}}/$version/g" -e "s/{{tag}}/$tag/g" "$install_template"
