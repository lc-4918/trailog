#!/usr/bin/env bash
# Test de release-stats.sh : le tableau, les totaux et --limit, sur un jeu de releases fixe (sans GitHub).
set -euo pipefail

directory="$(cd "$(dirname "$0")" && pwd)"
fixture="$(mktemp)"
trap 'rm -f "$fixture"' EXIT
printf 'v0.2.0\t2026-10-02\t5\t7\nv0.1.1\t2026-10-01\t3\t2\nv0.1.0\t2026-09-30\t0\t1\n' > "$fixture"

failures=0
check() {
    if [ "$2" != "$3" ]; then
        echo "ECHEC : $1"; echo "  attendu : $2"; echo "  obtenu  : $3"
        failures=$((failures + 1))
    fi
}

output="$(RELEASE_STATS_FIXTURE="$fixture" "$directory/release-stats.sh")"
check "en-tete" "Release    Published    APK downloads  Update checks" "$(echo "$output" | sed -n 1p)"
check "premiere ligne" "v0.2.0     2026-10-02               5              7" "$(echo "$output" | sed -n 2p)"
check "totaux" "Total      (3)                      8             10" "$(echo "$output" | tail -n 1)"
check "nombre de lignes" "5" "$(echo "$output" | wc -l | tr -d ' ')"

limited="$(RELEASE_STATS_FIXTURE="$fixture" "$directory/release-stats.sh" --limit 1)"
check "limit : une release montree" "3" "$(echo "$limited" | wc -l | tr -d ' ')"
check "limit : les totaux portent sur tout" "Total      (3)                      8             10" "$(echo "$limited" | tail -n 1)"

if "$directory/release-stats.sh" --inconnu >/dev/null 2>&1; then
    echo "ECHEC : un argument inconnu doit echouer"; failures=$((failures + 1))
fi

[ "$failures" -eq 0 ] && echo "release-stats : tous les tests passent" || { echo "$failures echec(s)"; exit 1; }
