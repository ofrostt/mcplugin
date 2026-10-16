#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mvn -B -ntp -f frostduels/pom.xml "$@" verify
mvn -B -ntp "$@" verify
