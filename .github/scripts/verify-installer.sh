#!/usr/bin/env bash
# Verify an installed Paintera with some runtime checks

# usage: verify-installer.sh <install root>
#   Linux:   /opt/Paintera
#   macOS:   Paintera.app/Contents
#   Windows: "/c/Program Files/Paintera" (run in Git Bash)
set -euo pipefail

root="$1"
here="$(cd "$(dirname "$0")" && pwd)"
work="$(mktemp -d)"

fail() { echo "ERROR: $*" >&2; exit 1; }

native_path() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else echo "$1"; fi; }

path_separator=":"
is_windows=false
case "$(uname -s)" in MINGW* | MSYS* | CYGWIN*) path_separator=";"; is_windows=true ;; esac

cfg="$(find "$root" -name Paintera.cfg | head -n 1)"
[ -n "$cfg" ] || fail "no Paintera.cfg below $root"
appdir="$(native_path "$(dirname "$cfg")")"
java="$(command -v java)"
[ -n "$java" ] || fail "no java on the PATH"
launcher="$(find "$root" -type f \( -path '*/bin/Paintera' -o -path '*/MacOS/Paintera' -o -name Paintera.exe \) | head -n 1)"
[ -n "$launcher" ] || fail "no Paintera launcher below $root"
echo "app dir:  $appdir"
echo "java:     $java"
echo "launcher: $launcher"

# the system properties the installed launcher passes to the JVM, with $APPDIR expanded
options=()
while IFS= read -r line; do
  for option in ${line#java-options=}; do
    case "$option" in
      -D* | -XX:*) options+=("${option//\$APPDIR/$appdir}") ;;
    esac
  done
done < <(tr -d '\r' < "$cfg" | grep '^java-options=')
echo "options:  ${options[*]}"

# Test the native libraries require
echo "::group::native libraries"
javac -cp "$appdir/*" -d "$work" "$here/InstallerRuntimeCheck.java"
"$java" "${options[@]}" -cp "$work${path_separator}$appdir/*" InstallerRuntimeCheck
echo "::endgroup::"

# smoke test the executable
echo "::group::launcher --help"
launch=("$launcher" --help)
if [ "$(uname -s)" = "Linux" ]; then
  command -v xvfb-run >/dev/null 2>&1 || sudo apt-get install -y xvfb
  launch=(xvfb-run -a "${launch[@]}")
fi
status=0
"${launch[@]}" > "$work/help.txt" 2>&1 || status=$?
cat "$work/help.txt"
echo "::endgroup::"
[ "$status" -eq 0 ] || fail "Paintera --help exited with $status"
if ! grep -qi "usage" "$work/help.txt"; then
  # the Windows launcher is a GUI program and may not write to the redirected output
  if [ "$is_windows" = true ]; then
    echo "::warning::no usage text in the output of Paintera --help"
  else
    fail "no usage text in the output of Paintera --help"
  fi
fi
echo "OK: $root"
