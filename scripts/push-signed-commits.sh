#!/usr/bin/env bash
# Recreates local commits on a new GitHub branch through the API, so that GitHub signs them. `master` only accepts
# signed commits, and a commit made with `git commit` on a CI runner is not signed.
#
#   scripts/push-signed-commits.sh <branch> <base> <tip>
#
# Creates <branch> at <base>, which must already be on GitHub, then adds one commit for every commit in
# <base>..<tip>, with the same message and file contents. The new commits are fetched and checked against the local
# ones, and their SHAs are printed, oldest first.
#
# Needs GH_TOKEN with write access to the repository contents, and GITHUB_REPOSITORY (owner/name).
set -euo pipefail

[[ $# -eq 3 ]] || { echo "usage: $0 <branch> <base> <tip>" >&2; exit 64; }
branch=$1
base=$(git rev-parse --verify --quiet "$2^{commit}") || { echo "error: unknown commit '$2'" >&2; exit 1; }
tip=$(git rev-parse --verify --quiet "$3^{commit}") || { echo "error: unknown commit '$3'" >&2; exit 1; }
repo=${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is not set}

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

locals=$(git rev-list --reverse "$base..$tip")
[[ -n $locals ]] || { echo "error: no commits in $base..$tip" >&2; exit 1; }

gh api "repos/$repo/git/refs" -f "ref=refs/heads/$branch" -f "sha=$base" --silent
echo "Created $branch at $base" >&2

mutation='mutation($input: CreateCommitOnBranchInput!) { createCommitOnBranch(input: $input) { commit { oid } } }'
head=$base
previous=$base
created=()
for commit in $locals; do
    if [[ $(git rev-parse "$commit^@") != "$previous" ]]; then
        echo "error: $commit is not a single-parent commit on top of $previous" >&2
        exit 1
    fi

    echo '[]' > "$tmp/additions"
    echo '[]' > "$tmp/deletions"
    while IFS= read -r -d '' status && IFS= read -r -d '' path; do
        case "$status" in
            A|M)
                git show "$commit:$path" | base64 | tr -d '\n' > "$tmp/contents"
                jq --arg path "$path" --rawfile contents "$tmp/contents" \
                    '. + [{path: $path, contents: $contents}]' "$tmp/additions" > "$tmp/next"
                mv "$tmp/next" "$tmp/additions"
                ;;
            D)
                jq --arg path "$path" '. + [{path: $path}]' "$tmp/deletions" > "$tmp/next"
                mv "$tmp/next" "$tmp/deletions"
                ;;
            *)
                echo "error: unsupported change '$status' to $path in $commit" >&2
                exit 1
                ;;
        esac
    done < <(git diff-tree -r --no-commit-id --name-status -z "$commit")

    oid=$(
        jq -n \
            --arg query "$mutation" \
            --arg repo "$repo" \
            --arg branch "$branch" \
            --arg head "$head" \
            --arg headline "$(git log -1 --format=%s "$commit")" \
            --arg body "$(git log -1 --format=%b "$commit")" \
            --slurpfile additions "$tmp/additions" \
            --slurpfile deletions "$tmp/deletions" \
            '{
                query: $query,
                variables: {
                    input: {
                        branch: {repositoryNameWithOwner: $repo, branchName: $branch},
                        expectedHeadOid: $head,
                        message: {headline: $headline, body: $body},
                        fileChanges: {additions: $additions[0], deletions: $deletions[0]}
                    }
                }
            }' |
            gh api graphql --input - --jq '.data.createCommitOnBranch.commit.oid'
    )
    [[ $oid =~ ^[0-9a-f]{40}$ ]] || { echo "error: GitHub returned no commit for $commit: '$oid'" >&2; exit 1; }
    echo "Created $oid from $commit ($(git log -1 --format=%s "$commit"))" >&2

    created+=("$oid")
    head=$oid
    previous=$commit
done

# `gh` supplies the credentials, as the checkout does not keep any.
git -c credential.helper= -c 'credential.helper=!gh auth git-credential' fetch --quiet origin "refs/heads/$branch"

index=0
for commit in $locals; do
    oid=${created[$index]}
    index=$((index + 1))
    if [[ $(git rev-parse "$oid^{tree}") != "$(git rev-parse "$commit^{tree}")" ]]; then
        echo "error: $oid does not have the same contents as $commit" >&2
        exit 1
    fi
    if [[ $(gh api "repos/$repo/commits/$oid" --jq '.commit.verification.verified') != true ]]; then
        echo "error: GitHub did not sign $oid" >&2
        exit 1
    fi
done

printf '%s\n' "${created[@]}"
