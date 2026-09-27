#!/bin/bash

# a helper script to regenerate the README screenshots in .images/
# It boots a throwaway tuwunel homeserver in Docker, seeds demo data, drives the real desktop UI and overwrites the PNGs.
set -euo pipefail
cd "$(dirname "$0")/.."

if ! docker info >/dev/null 2>&1; then
  echo "error: the Docker daemon is not reachable (is Docker Desktop / colima running?)" >&2
  exit 1
fi

./gradlew generateReadmeScreenshots "$@"
