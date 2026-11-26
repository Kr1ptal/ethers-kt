#!/usr/bin/env bash
# Rewrites the version references that change during a release.
#
#   scripts/release-version.sh release <version> <next-version>-SNAPSHOT [<generated-notes>]
#       Prepares the single commit that releases <version>. It sets the README dependency examples to <version> and
#       gives CHANGELOG.md a section for it, dated today (UTC). VERSION_NAME moves straight to the next snapshot,
#       since the release itself is built with -PVERSION_NAME=<version>.
#
#       The section is the "## [Unreleased]" section if there is one. Otherwise it is filled from <generated-notes>,
#       a file written by `git cliff --unreleased --tag v<version> --strip all`, and left empty without one. Prints
#       where the notes came from: "unreleased", "generated" or "empty".
#   scripts/release-version.sh notes <version>|Unreleased
#       Prints the body of that CHANGELOG.md section. Exits with 3 if the section is missing, 4 if there are several,
#       and 5 if it is empty.
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
    echo "usage: $0 release <version> <next-version>-SNAPSHOT [<generated-notes>] | notes <version>|Unreleased" >&2
    exit 64
}

# replace <file> <perl substitution> <text expected afterwards>
replace() {
    local file=$1 expression=$2 expected=$3
    perl -pi -e "$expression" "$file"
    if ! grep -qF -- "$expected" "$file"; then
        echo "error: expected '$expected' in $file after update" >&2
        exit 1
    fi
}

# notes <section name>
notes() {
    local status=0
    SECTION=$1 perl -0777 -ne '
        my $name = quotemeta($ENV{SECTION});
        my @sections = /^## \[$name\][^\n]*\n(.*?)(?=^## \[|\z)/msg;
        exit 3 if @sections == 0;
        exit 4 if @sections > 1;
        my $body = $sections[0];
        $body =~ s/\A(?:[ \t]*\n)+//;
        $body =~ s/\s+\z//;
        exit 5 if $body eq "";
        print "$body\n";
    ' CHANGELOG.md || status=$?
    case "$status" in
        0) ;;
        3) echo "error: CHANGELOG.md has no '## [$1]' section" >&2 ;;
        4) echo "error: CHANGELOG.md has more than one '## [$1]' section" >&2 ;;
        5) echo "error: the '## [$1]' section of CHANGELOG.md is empty" >&2 ;;
        *) echo "error: could not read the '## [$1]' section of CHANGELOG.md" >&2 ;;
    esac
    return "$status"
}

mode=${1:-}
case "$mode" in
    release)
        [[ $# -eq 3 || $# -eq 4 ]] || usage
        version=$2 next=$3 generated=${4:-}
        [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "error: invalid release version '$version'" >&2; exit 1; }
        [[ $next =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || { echo "error: invalid snapshot version '$next'" >&2; exit 1; }
        if grep -qF "## [$version]" CHANGELOG.md; then
            echo "error: CHANGELOG.md already has a '## [$version]' section" >&2
            exit 1
        fi
        date=$(date -u +%Y-%m-%d)
        export VERSION=$version NEXT=$next DATE=$date

        case "$(grep -c '^## \[Unreleased\]' CHANGELOG.md || true)" in
            0)
                body=""
                if [[ -n $generated ]]; then
                    # git-cliff writes the section with its own heading, and only the heading if no commit qualifies.
                    body=$(perl -0777 -pe 's/\A\s*## \[[^\n]*\n?//; s/\A(?:[ \t]*\n)+//; s/\s+\z//' "$generated")
                fi
                if [[ -n $body ]]; then source=generated; else source=empty; fi
                # Above the latest release. The digit skips the heading template in the comment at the top.
                BODY=$body perl -0777 -pi -e '
                    my $body = $ENV{BODY} eq "" ? "" : "$ENV{BODY}\n\n";
                    s/^(?=## \[\d)/## [$ENV{VERSION}] - $ENV{DATE}\n\n$body/m;
                ' CHANGELOG.md
                ;;
            1)
                source=unreleased
                perl -pi -e 's/^## \[Unreleased\].*/## [$ENV{VERSION}] - $ENV{DATE}/' CHANGELOG.md
                ;;
            *)
                echo "error: CHANGELOG.md has more than one '## [Unreleased]' section" >&2
                exit 1
                ;;
        esac
        if ! grep -qxF "## [$version] - $date" CHANGELOG.md; then
            echo "error: could not add a '## [$version] - $date' section to CHANGELOG.md" >&2
            exit 1
        fi

        replace README.md 's/(io\.kriptal\.ethers:ethers-bom:)\d+\.\d+\.\d+(?=")/$1$ENV{VERSION}/g' \
            "io.kriptal.ethers:ethers-bom:$version\""
        for readme in README.md ethers-abigen-plugin/README.md; do
            replace "$readme" 's/(id\("io\.kriptal\.ethers\.abigen-plugin"\) version ")\d+\.\d+\.\d+(?=")/$1$ENV{VERSION}/g' \
                "id(\"io.kriptal.ethers.abigen-plugin\") version \"$version\""
        done
        replace gradle.properties 's/^VERSION_NAME=.*/VERSION_NAME=$ENV{NEXT}/' "VERSION_NAME=$next"
        replace ethers-abigen-plugin/README.md 's/(ethers-abigen-plugin-)\d+\.\d+\.\d+-SNAPSHOT(?=\.jar)/$1$ENV{NEXT}/g' \
            "ethers-abigen-plugin-$next.jar"
        echo "$source"
        ;;
    notes)
        [[ $# -eq 2 ]] || usage
        notes "$2"
        ;;
    *)
        usage
        ;;
esac
