#!/usr/bin/env bash
#
# Compteurs de telechargement des releases GitHub de Trailog : le seul signal d'usage dont on dispose
# sans serveur.
#
#   APK downloads   telechargements des APK (mise a jour de l'appli, ou installation a la main)
#   update checks   telechargements de latest-release.json : un par verification de mise a jour
#
# Ce ne sont pas des utilisateurs uniques : un appareil qui verifie a chaque lancement compte autant de
# fois, et les essais du developpeur y sont comptes. Une release garde son compteur du temps ou elle etait
# la derniere ; la derniere release est donc celle dont les chiffres sont encore en train de monter.
#
# Usage : release-stats.sh [--limit N] [proprietaire/depot]
# Necessite gh (https://cli.github.com), connecte a GitHub.

set -euo pipefail

repository="lc-4918/trailog"
limit=0

while [ $# -gt 0 ]; do
    case "$1" in
        --limit)
            limit="${2:?--limit demande un nombre}"
            shift 2
            ;;
        -h|--help)
            sed -n '3,15p' "$0" | sed 's/^# \{0,1\}//'
            exit 0
            ;;
        */*)
            repository="$1"
            shift
            ;;
        *)
            echo "Argument inconnu : $1" >&2
            exit 2
            ;;
    esac
done

# Les releases en lignes tabulees : etiquette, date, telechargements d'APK, verifications de mise a jour.
# RELEASE_STATS_FIXTURE remplace GitHub par un fichier, pour les tests.
fetch_releases() {
    if [ -n "${RELEASE_STATS_FIXTURE:-}" ]; then
        cat "$RELEASE_STATS_FIXTURE"
        return
    fi
    command -v gh >/dev/null 2>&1 || { echo "gh est introuvable : https://cli.github.com" >&2; exit 1; }
    # Les pre-releases (l'index de lieux, par exemple) ne sont pas des versions de l'appli.
    gh api "repos/$repository/releases" --paginate --jq '
        .[] | select(.prerelease == false) | [
            .tag_name,
            .published_at[0:10],
            ([.assets[] | select(.name | endswith(".apk")) | .download_count] | add // 0),
            ([.assets[] | select(.name == "latest-release.json") | .download_count] | add // 0)
        ] | @tsv
    '
}

fetch_releases | awk -F'\t' -v limit="$limit" '
    BEGIN { printf "%-10s %-11s %14s %14s\n", "Release", "Published", "APK downloads", "Update checks" }
    {
        totalApk += $3
        totalChecks += $4
        count++
        if (limit == 0 || count <= limit) {
            printf "%-10s %-11s %14d %14d\n", $1, $2, $3, $4
        }
    }
    END {
        printf "%-10s %-11s %14d %14d\n", "Total", "(" count ")", totalApk, totalChecks
    }
'
