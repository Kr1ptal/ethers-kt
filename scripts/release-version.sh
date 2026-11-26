#!/usr/bin/env bash
# Rewrites the version references that change during a release.
#
#   scripts/release-version.sh release <version>
#       Sets VERSION_NAME and the README dependency examples, and turns the "## [Unreleased]" section of
#       CHANGELOG.md into the section of that version, dated today (UTC).
#   scripts/release-version.sh snapshot <version>-SNAPSHOT
#       Sets VERSION_NAME and the snapshot jar name in the abigen plugin README.
#   scripts/release-version.sh notes <version>|Unreleased
#       Prints the body of that CHANGELOG.md section. Fails if the section is missing or empty.
set -euo pipefail

cd "$(dirname "$0")/.."

usage() {
    echo "usage: $0 release <version> | snapshot <version>-SNAPSHOT | notes <version>|Unreleased" >&2
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
        [[ $# -eq 2 ]] || usage
        version=$2
        [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "error: invalid release version '$version'" >&2; exit 1; }
        if grep -qF "## [$version]" CHANGELOG.md; then
            echo "error: CHANGELOG.md already has a '## [$version]' section" >&2
            exit 1
        fi
        notes Unreleased >/dev/null
        date=$(date -u +%Y-%m-%d)
        export VERSION=$version DATE=$date

        replace gradle.properties 's/^VERSION_NAME=.*/VERSION_NAME=$ENV{VERSION}/' "VERSION_NAME=$version"
        replace README.md 's/(io\.kriptal\.ethers:ethers-bom:)\d+\.\d+\.\d+(?=")/$1$ENV{VERSION}/g' \
            "io.kriptal.ethers:ethers-bom:$version\""
        for readme in README.md ethers-abigen-plugin/README.md; do
            replace "$readme" 's/(id\("io\.kriptal\.ethers\.abigen-plugin"\) version ")\d+\.\d+\.\d+(?=")/$1$ENV{VERSION}/g' \
                "id(\"io.kriptal.ethers.abigen-plugin\") version \"$version\""
        done
        replace CHANGELOG.md 's/^## \[Unreleased\].*/## [$ENV{VERSION}] - $ENV{DATE}/' "## [$version] - $date"
        ;;
    snapshot)
        [[ $# -eq 2 ]] || usage
        version=$2
        [[ $version =~ ^[0-9]+\.[0-9]+\.[0-9]+-SNAPSHOT$ ]] || { echo "error: invalid snapshot version '$version'" >&2; exit 1; }
        export VERSION=$version

        replace gradle.properties 's/^VERSION_NAME=.*/VERSION_NAME=$ENV{VERSION}/' "VERSION_NAME=$version"
        replace ethers-abigen-plugin/README.md 's/(ethers-abigen-plugin-)\d+\.\d+\.\d+-SNAPSHOT(?=\.jar)/$1$ENV{VERSION}/g' \
            "ethers-abigen-plugin-$version.jar"
        ;;
    notes)
        [[ $# -eq 2 ]] || usage
        notes "$2"
        ;;
    *)
        usage
        ;;
esac
