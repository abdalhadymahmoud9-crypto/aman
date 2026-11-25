#!/bin/sh
# Fetches the StevenBlack/hosts "porn" hosts file (MIT licensed, see ATTRIBUTION.md).
# Output: porn-hosts.txt in the current directory.
# Note: upstream changes over time. Re-fetching may produce a slightly
# different list than a previous build. That is expected.
set -e
curl -fsSL "https://raw.githubusercontent.com/StevenBlack/hosts/master/alternates/porn/hosts" -o porn-hosts.txt
echo "fetched: $(grep -c '^0\.0\.0\.0 ' porn-hosts.txt) host rows"
