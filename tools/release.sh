#!/usr/bin/env bash
#
# Builds and signs the release APK. Replaces Android Studio's "Generate Signed
# APK" wizard.
#
# Gradle builds unsigned and apksigner signs afterwards, so app/build.gradle.kts
# — the file F-Droid builds from — carries no signing config at all.
#
# macOS ONLY. The secrets come from the macOS Keychain via `security`, and the
# Android Studio / SDK fallback paths are macOS ones. Porting means swapping
# keychain() for another secret store (secret-tool on Linux) and the two path
# defaults below; everything else — Gradle, zipalign, apksigner — is portable.
#
# Everything machine-specific lives in the macOS Keychain, so this script needs
# no editing to run on another Mac.
#
# ONE-TIME SETUP PER MACHINE. Three items in the login Keychain, under the
# service `y20k-android-keystore` — shared by every y20k app signed with
# y20k.jks, so this is done once per machine, not once per app:
#
#   keystore-path   where y20k.jks lives on this machine
#   store           the keystore password
#   key             the password for the "y20k key" alias
#
# The path is not secret and goes on the command line. For the two passwords the
# trailing -w is left bare, which makes security prompt for the value instead of
# taking it as an argument: they stay out of the shell history and out of `ps`.
# -U updates an item that already exists, so the same lines rotate a password
# later. -T /usr/bin/security lets this script read them back without a dialog.
#
#   security add-generic-password -U -s y20k-android-keystore -a keystore-path -T /usr/bin/security -w /path/to/y20k.jks
#   security add-generic-password -U -s y20k-android-keystore -a store         -T /usr/bin/security -w
#   security add-generic-password -U -s y20k-android-keystore -a key           -T /usr/bin/security -w
#
# To check what is stored (prints the value, so mind who is looking):
#
#   security find-generic-password -w -s y20k-android-keystore -a keystore-path
#
# The signed APK lands in app/build/outputs/apk/release/, named ready for upload
# to the Codeberg release page, together with R8's mapping file named to
# match; the unsigned intermediate is deleted. Builds
# clean by default — pass --no-clean to reuse existing output. Refuses to build
# unless the working tree matches HEAD, so the APK always corresponds to a commit
# you can tag — pass --allow-dirty for a test build.
#
# The non-free branch publishes betas under a different name. It carries a
# tools/release.conf that this script sources if present, so release.sh itself
# stays identical on both branches and merges cleanly:
#
#   NAME_KIND="beta"
#   NAME_SUFFIX="-non-free"
#
# Usage: tools/release.sh [--no-clean] [--allow-dirty]

set -euo pipefail

[ "$(uname -s)" = "Darwin" ] || { echo "macOS only — see the note at the top of this script" >&2; exit 1; }

KEYCHAIN_SERVICE="y20k-android-keystore"
KEY_ALIAS="y20k key"
OUT_DIR="app/build/outputs/apk/release"
APK_PREFIX="transistor"

# master publishes <prefix>-app-release-<version>.apk. The non-free branch
# overrides both halves in tools/release.conf, which exists only on that branch.
NAME_KIND="release"
NAME_SUFFIX=""

# The certificate that has signed every release so far. A mismatch means the
# wrong key — users could not upgrade — so the script stops without writing.
EXPECTED_SHA256="6105145f84e5cbd697f0f6e12c9777042e9b2db667a7ff0da412f67435316dd8"

CLEAN=true
ALLOW_DIRTY=false
for arg in "$@"; do
    case "$arg" in
        --no-clean) CLEAN=false ;;
        --allow-dirty) ALLOW_DIRTY=true ;;
        *) echo "unknown option: $arg" >&2; exit 2 ;;
    esac
done

cd "$(dirname "$0")/.."

# Branch-local naming override; tracked on non-free, absent on master.
# shellcheck source=/dev/null
[ -f tools/release.conf ] && . tools/release.conf

# A release has to be reproducible from a commit, so refuse to build a tree that
# does not match HEAD. Untracked files count: an untracked source file compiles
# into the APK without existing in the tag. --allow-dirty skips this for test builds.
git rev-parse --is-inside-work-tree >/dev/null 2>&1 \
    || { echo "not a git repository — cannot verify the tree" >&2; exit 1; }
TREE_STATE="$(git describe --always)"
if [ -n "$(git status --porcelain)" ]; then
    if [ "$ALLOW_DIRTY" = false ]; then
        echo "working tree is not clean — commit, stash, or pass --allow-dirty:" >&2
        git status --short >&2
        exit 1
    fi
    # `git describe --dirty` only sees tracked edits, so mark it here instead —
    # otherwise an --allow-dirty build with untracked files reports a clean sha.
    TREE_STATE="$TREE_STATE-dirty"
fi

# Reads one Keychain item, or points at the setup notes above.
keychain() {
    security find-generic-password -w -s "$KEYCHAIN_SERVICE" -a "$1" 2>/dev/null && return
    echo "missing Keychain item '$1' in service $KEYCHAIN_SERVICE" >&2
    echo "see ONE-TIME SETUP at the top of this script" >&2
    exit 1
}

# AGP 9 needs JDK 17+, and the system default is often older, so prefer the JDK
# bundled with Android Studio wherever it happens to be installed.
for studio in "$HOME/Applications/Android Studio.app" "/Applications/Android Studio.app"; do
    if [ -x "$studio/Contents/jbr/Contents/Home/bin/java" ]; then
        export JAVA_HOME="$studio/Contents/jbr/Contents/Home"
        break
    fi
done
[ -n "${JAVA_HOME:-}" ] || { echo "no JDK found — install Android Studio or set JAVA_HOME to a JDK 17+" >&2; exit 1; }

SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
[ -d "$SDK_DIR/build-tools" ] || { echo "no Android SDK build-tools under $SDK_DIR" >&2; exit 1; }
BUILD_TOOLS="$SDK_DIR/build-tools/$(ls -1 "$SDK_DIR/build-tools" | sort -V | tail -1)"

KEYSTORE="$(keychain keystore-path)"
[ -f "$KEYSTORE" ] || { echo "keystore not found at $KEYSTORE" >&2; exit 1; }

VERSION_NAME="$(sed -n 's/^ *versionName = "\(.*\)"/\1/p' app/build.gradle.kts)"
[ -n "$VERSION_NAME" ] || { echo "could not read versionName from app/build.gradle.kts" >&2; exit 1; }
echo "==> Transistor $VERSION_NAME at $TREE_STATE, build-tools $(basename "$BUILD_TOOLS")"

if [ "$CLEAN" = true ]; then ./gradlew clean; fi
./gradlew assembleRelease

# R8's mapping file is what makes a production stack trace from a published APK
# readable, so it is archived beside the APK under the matching name. R8 runs
# once per variant, so one mapping covers every APK this build produced — the
# name carries no ABI segment even where the build splits by ABI. Missing means
# R8 did not run, which is worth stopping for: the APK would ship unmapped.
MAPPING="app/build/outputs/mapping/release/mapping.txt"
[ -f "$MAPPING" ] || { echo "no mapping file at $MAPPING — is R8 still enabled?" >&2; exit 1; }
cp "$MAPPING" "$OUT_DIR/$APK_PREFIX-app-$NAME_KIND-mapping-$VERSION_NAME$NAME_SUFFIX.txt"

STORE_PASS="$(keychain store)"
KEY_PASS="$(keychain key)"

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# No ABI splits here, so assembleRelease produces exactly one unsigned APK and
# the published name carries no ABI segment.
UNSIGNED="$OUT_DIR/app-release-unsigned.apk"
[ -f "$UNSIGNED" ] || { echo "no unsigned APK at $UNSIGNED" >&2; exit 1; }

# -P 16 keeps any .so entries on 16 KB boundaries, as AGP already lays them out;
# alignment has to happen before signing, apksigner only preserves it.
"$BUILD_TOOLS/zipalign" -P 16 -f 4 "$UNSIGNED" "$WORK/aligned.apk"

# Both passwords are read from stdin, store first, one per line. v1 is off
# deliberately: apksigner would enable it, but minSdk 25 never needs JAR signing
# and it adds ~200 KB of META-INF the wizard-built releases lack.
printf '%s\n%s\n' "$STORE_PASS" "$KEY_PASS" | "$BUILD_TOOLS/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias "$KEY_ALIAS" \
    --ks-pass stdin \
    --key-pass stdin \
    --v1-signing-enabled false \
    --v3-signing-enabled false \
    --v4-signing-enabled false \
    --out "$WORK/signed.apk" \
    "$WORK/aligned.apk"

got="$("$BUILD_TOOLS/apksigner" verify --print-certs "$WORK/signed.apk" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')"
if [ "$got" != "$EXPECTED_SHA256" ]; then
    echo "certificate mismatch" >&2
    echo "  expected $EXPECTED_SHA256" >&2
    echo "  got      ${got:-<none>}" >&2
    exit 1
fi

SIGNED="$OUT_DIR/$APK_PREFIX-app-$NAME_KIND-$VERSION_NAME$NAME_SUFFIX.apk"
mv "$WORK/signed.apk" "$SIGNED"
rm -f "$UNSIGNED"
echo "    $SIGNED"
echo "    $OUT_DIR/$APK_PREFIX-app-$NAME_KIND-mapping-$VERSION_NAME$NAME_SUFFIX.txt"
echo "==> done, certificate matches $EXPECTED_SHA256"
