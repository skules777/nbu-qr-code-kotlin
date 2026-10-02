#!/bin/sh
# The published library must not pull in anything but the Kotlin standard library — in particular
# nothing from the sample app (Compose Multiplatform, lifecycle). Fails CI if a POM says otherwise.
set -eu

cd "$(dirname "$0")/.."

# Publications are discovered rather than listed: iOS ones exist only on macOS hosts, and a new
# target must not slip past the check just because nobody added it here.
tasks=$(./gradlew -q :nbu-qr-code:tasks --all |
    sed -n 's/^\(generatePomFileFor[A-Za-z0-9]*Publication\).*/:nbu-qr-code:\1/p')
if [ -z "$tasks" ]; then
    echo "error: no POM generation tasks found" >&2
    exit 1
fi

# Stale POMs from an earlier build would otherwise be checked as if they were current.
rm -rf nbu-qr-code/build/publications
# shellcheck disable=SC2086
./gradlew -q $tasks

status=0
for pom in nbu-qr-code/build/publications/*/pom-default.xml; do
    unexpected=$(
        sed -n '/<dependencies>/,/<\/dependencies>/p' "$pom" |
            sed -n 's:.*<artifactId>\(.*\)</artifactId>.*:\1:p' |
            grep -v -x 'kotlin-stdlib' || true
    )
    if [ -n "$unexpected" ]; then
        echo "error: $pom depends on: $unexpected" >&2
        status=1
    fi
done
[ "$status" -eq 0 ] && echo "Published dependencies: kotlin-stdlib only ($(echo $tasks | wc -w | tr -d ' ') publications)."
exit "$status"
