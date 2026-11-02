#!/usr/bin/env bash
# Quick launcher for RepoFile Explorer
PORT=8080

# Find next available port if 8080 is taken
while lsof -Pi :$PORT -sTCP:LISTEN -t >/dev/null 2>&1 ; do
    PORT=$((PORT+1))
done

echo "=========================================================="
echo " Starting RepoFile Explorer & Downloader at http://localhost:$PORT"
echo " Pre-configured for: git@github.com:DHRUV7044/labfiles.git"
echo "=========================================================="

python3 -m http.server $PORT --directory "$(dirname "$0")"
