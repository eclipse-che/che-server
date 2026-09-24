#!/usr/bin/env bash
#
# Copyright (c) 2012-2026 Red Hat, Inc.
# This program and the accompanying materials are made
# available under the terms of the Eclipse Public License 2.0
# which is available at https://www.eclipse.org/legal/epl-2.0/
#
# SPDX-License-Identifier: EPL-2.0
#
# Contributors:
#   Red Hat, Inc. - initial API and implementation
#

#
# Outer editor + inner devcontainer.
# che-code stays in the UDI container; the repo's devcontainer.json is built and run as a
# nested rootless-podman container. Terminals, lifecycle commands and file ownership are
# wired so the inner container behaves like the project's real environment.
#
# Env overrides:
#   CONTAINER_NAME     nested container name           (default: devcontainer)
#   IMAGE_NAME         built image tag                 (default: localhost/devcontainer:latest)
#   REBUILD=1          force rebuild (remove existing container and image; default: auto-rebuild when config changes)
#   NO_CACHE=1         rebuild without cache (passes --no-cache to devcontainer build)
#   STRICT_LIFECYCLE=1 exit non-zero if any lifecycle command fails
#   NO_KEEP_ID=1       skip --userns=keep-id (debugging)
#   PREFLIGHT_IMAGE    runnable probe image (default: quay.io/podman/hello:latest; override for a registry mirror)
#
set -uo pipefail

# Serialize setup and rebuild without interrupting an active build. Opening in append mode
# preserves the current owner's PID while another invocation waits.
LOCK_FILE="${LOCK_FILE:-/tmp/.devcontainer-setup.lock}"
LOCK_HELD=0
if command -v flock >/dev/null 2>&1; then
  exec 9>>"$LOCK_FILE" || { echo "cannot open setup lock: $LOCK_FILE" >&2; exit 1; }
  if ! flock -n 9; then
    echo "another devcontainer setup is in progress; waiting..."
    flock -w 900 9 || { echo "timed out waiting for the in-progress setup" >&2; exit 1; }
  fi
  LOCK_HELD=1
  printf '%s\n' "$$" > "$LOCK_FILE"
fi

# The parent owns the lock and stays alive for the whole run. Close the descriptor in the setup
# child so conmon, fuse-overlayfs, and lifecycle processes cannot retain it after setup finishes.
(
exec 9>&-
PROJECTS_ROOT="${PROJECTS_ROOT:-/projects}"
# Project: explicit arg > DWO's PROJECT_SOURCE > the only directory under PROJECTS_ROOT.
if [ "$#" -ge 1 ] && [ -n "${1:-}" ]; then
  PROJECT_DIR="${PROJECTS_ROOT}/${1}"
elif [ -n "${PROJECT_SOURCE:-}" ] && [ -d "${PROJECT_SOURCE}" ]; then
  PROJECT_DIR="$PROJECT_SOURCE"
else
  mapfile -t _dirs < <(find "$PROJECTS_ROOT" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort)
  if [ "${#_dirs[@]}" -eq 1 ]; then
    PROJECT_DIR="${_dirs[0]}"
  else
    echo "specify a project: $(basename "$0") <name>" >&2
    [ "${#_dirs[@]}" -gt 1 ] && printf '  %s\n' "${_dirs[@]##*/}" >&2
    exit 1
  fi
fi
[ -d "$PROJECT_DIR" ] || { echo "no such project: $PROJECT_DIR" >&2; exit 1; }
PROJECT_NAME="$(basename "$PROJECT_DIR")"
CONTAINER_NAME="${CONTAINER_NAME:-devcontainer}"
IMAGE_NAME="${IMAGE_NAME:-localhost/devcontainer:latest}"
REBUILD="${REBUILD:-0}"
NO_CACHE="${NO_CACHE:-0}"
STRICT_LIFECYCLE="${STRICT_LIFECYCLE:-0}"
LIFECYCLE_FAILURES=0

# --- phase timing ---------------------------------------------------------
# Cold start is the main operational cost of this approach (CLI install + base image pull +
# build). Record where the time actually goes so it can be reported rather than guessed at.
T_START=$(date +%s); PHASE_T=$T_START; CURRENT_PHASE=""; PHASE_LOG=()
_close_phase(){ local now; now=$(date +%s)
  [ -n "$CURRENT_PHASE" ] && PHASE_LOG+=("$((now-PHASE_T))|$CURRENT_PHASE"); PHASE_T=$now; }
step(){ _close_phase; CURRENT_PHASE="$2"; echo "[$1] $2"; return 0; }

echo "=== devcontainer (outer editor): ${PROJECT_NAME} ==="

# ---------------------------------------------------------------------------
# 1. Container engine
# ---------------------------------------------------------------------------
# UDI moves the real podman to podman.orig and puts a symlink on PATH that becomes the
# KUBEDOCK WRAPPER when KUBEDOCK_ENABLED=true. The wrapper sends run/exec to kubedock — a
# separate pod — while build stays local, so the image would be built somewhere the runtime
# cannot see it and --network=host would no longer be the pod's netns.
PODMAN="${ORIGINAL_PODMAN_PATH:-/usr/bin/podman.orig}"
[ -x "$PODMAN" ] || PODMAN="$(command -v podman 2>/dev/null)"
[ -n "$PODMAN" ] || { echo "podman not found" >&2; exit 1; }
step "1/8" "engine: $PODMAN"

# Exercise the same engine and host networking used by start_container. Podman info's
# capability and UID-map fields do not prove that a container can actually start.
# Cached image pull makes this cheap after the first workspace start; 120s is a timeout
# ceiling, not the expected duration. Called only when we are about to build/run, not
# on a no-op reuse of an already-running container.
run_preflight() {
  PREFLIGHT_IMAGE="${PREFLIGHT_IMAGE:-quay.io/podman/hello:latest}"
  PREFLIGHT_CONTAINER="che-devcontainer-preflight-$$"
  step "1b" "checking nested container execution (timeout: 120s)..."
  PREFLIGHT_RC=0
  timeout --kill-after=5s 120s "$PODMAN" run --rm --network=host \
    --name "$PREFLIGHT_CONTAINER" "$PREFLIGHT_IMAGE" 9>&- || PREFLIGHT_RC=$?
  if [ "$PREFLIGHT_RC" -ne 0 ]; then
    timeout --kill-after=5s 10s "$PODMAN" rm -f "$PREFLIGHT_CONTAINER" \
      >/dev/null 2>&1 9>&- || true
    if [ "$PREFLIGHT_RC" -eq 124 ] || [ "$PREFLIGHT_RC" -eq 137 ]; then
      echo "Nested-container preflight timed out; devcontainer setup stopped." >&2
    else
      echo "Nested-container preflight failed (exit ${PREFLIGHT_RC}); devcontainer setup stopped." >&2
    fi
    echo "See the Podman error above for the cause (for example, image pull or container runtime failure)." >&2
    exit "$PREFLIGHT_RC"
  fi
  step "1b" "nested container execution succeeded"
}

# Derive the range instead of hardcoding a uid — UDI's entrypoint computes this from the
# actual uid, and a hardcoded "user:1001:..." maps the wrong host range on a uid-1000 pod.
# NOTE: a 65536-ID userns cannot map container ID 65534 (nobody) however the ranges are
# split, because our own UID consumes one. apt's sandbox may still need disabling.
SU_USER="$(id -un 2>/dev/null || echo user)"; SU_UID="$(id -u)"
if [ "$SU_UID" -gt 0 ] && [ "$SU_UID" -lt 65536 ]; then
  RANGES="$(printf '%s:1:%s\n%s:%s:%s\n' "$SU_USER" "$((SU_UID-1))" \
                                          "$SU_USER" "$((SU_UID+1))" "$((65535-SU_UID))")"
  printf '%s\n' "$RANGES" > /etc/subuid 2>/dev/null &&
  printf '%s\n' "$RANGES" > /etc/subgid 2>/dev/null &&
  "$PODMAN" system migrate >/dev/null 2>&1 &&
  step "2/8" "store=$("$PODMAN" info --format json 2>/dev/null | jq -r '.store.graphDriverName + " @ " + .store.graphRoot') subuid=${SU_USER}(${SU_UID})" ||
  step "2/8" "store=$("$PODMAN" info --format json 2>/dev/null | jq -r '.store.graphDriverName + " @ " + .store.graphRoot') subuid=unchanged"
fi

# ---------------------------------------------------------------------------
# 3. devcontainer CLI + pre-build config
# ---------------------------------------------------------------------------
DEVCONTAINER_BIN="${DEVCONTAINER_BIN:-$(command -v devcontainer || echo /home/user/.devcontainers/bin/devcontainer)}"
if ! "$DEVCONTAINER_BIN" --version >/dev/null 2>&1; then
  step "3/8" "installing devcontainer CLI..."
  # TODO: remove once the devcontainer CLI is bundled in the Universal Developer Image.
  # Tracking: https://github.com/devfile/developer-images/pull/267
  # Until then the CLI is installed at workspace start, which requires network access to
  # npm/GitHub and is a blocker for airgapped clusters.
  # Pin a release. Do not install from GitHub main — that is an unpinned supply chain.
  # Override with DEVCONTAINER_CLI_VERSION if a workspace needs a newer CLI.
  DEVCONTAINER_CLI_VERSION="${DEVCONTAINER_CLI_VERSION:-0.89.0}"
  npm install -g "@devcontainers/cli@${DEVCONTAINER_CLI_VERSION}" >/dev/null 2>&1 || {
    echo "  npm install failed; installing CLI ${DEVCONTAINER_CLI_VERSION} from tagged release..." >&2
    curl -fsSL "https://raw.githubusercontent.com/devcontainers/cli/v${DEVCONTAINER_CLI_VERSION}/scripts/install.sh" \
      | sh -s -- --version "${DEVCONTAINER_CLI_VERSION}"
  }
  DEVCONTAINER_BIN="$(command -v devcontainer || echo /home/user/.devcontainers/bin/devcontainer)"
else
  step "3/8" "devcontainer CLI present."
fi

# read-configuration shells out to `docker ps` before doing anything, so WITHOUT
# --docker-path it exits 1 with no output and we would silently fall back to a
# comment-stripping regex. Point it at the real engine.
CONFIG_JSON=""
raw="$("$DEVCONTAINER_BIN" read-configuration --docker-path "$PODMAN" \
        --workspace-folder "$PROJECT_DIR" 2>/dev/null || true)"
[ -n "$raw" ] && CONFIG_JSON="$(printf '%s\n' "$raw" | grep -E '^\{' | tail -1 \
  | jq -c '.configuration // empty' 2>/dev/null || true)"
if [ -z "$CONFIG_JSON" ]; then
  DC=""
  for c in "$PROJECT_DIR/.devcontainer/devcontainer.json" "$PROJECT_DIR/.devcontainer.json"; do
    [ -f "$c" ] && { DC="$c"; break; }
  done
  [ -n "$DC" ] || DC="$(find "$PROJECT_DIR/.devcontainer" -mindepth 2 -maxdepth 2 \
    -name devcontainer.json 2>/dev/null | sort | head -1)"
  [ -n "$DC" ] || { echo "  no devcontainer.json found" >&2; exit 1; }
  echo "  read-configuration failed; using fallback parser on $DC" >&2
  # whole-line comments only, so a URL inside a string survives
  CONFIG_JSON="$(sed -e 's@^[[:space:]]*//.*$@@' "$DC" | jq -c '.' 2>/dev/null)"
  CONFIG_PATH="$DC"
  [ -n "$CONFIG_JSON" ] || { echo "  could not parse $DC" >&2; exit 1; }
else
  # Use the file the CLI actually resolved, rather than repeating config discovery.
  CONFIG_PATH="$(printf '%s' "$CONFIG_JSON" | jq -r '.configFilePath.fsPath // empty' 2>/dev/null || true)"
fi

CONFIG_FINGERPRINT="$(printf '%s' "$CONFIG_JSON" | sha256sum | cut -d' ' -f1)"

# ---------------------------------------------------------------------------
# 4. initializeCommand (runs OUTSIDE the container, per spec)
# ---------------------------------------------------------------------------
JQ_NORMALIZE='
def norm($name):
  if . == null then empty
  elif type == "string" then {name:$name, argv:["/bin/sh","-c",.]}
  elif type == "array"  then {name:$name, argv:.}
  elif type == "object" then to_entries[] | .key as $k | (.value | norm($k))
  else empty end;
norm("")'

run_outer() {
  local spec="$1" line label; [ -z "$spec" ] || [ "$spec" = "null" ] && return 0
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    label="$(printf '%s' "$line" | jq -r '.name')"
    local -a argv=(); mapfile -t argv < <(printf '%s' "$line" | jq -r '.argv[]')
    [ "${#argv[@]}" -eq 0 ] && continue
    echo "  -> initializeCommand${label:+ [$label]}: ${argv[*]}"
    ( cd "$PROJECT_DIR" && "${argv[@]}" ) || {
      echo "  !! initializeCommand failed" >&2; LIFECYCLE_FAILURES=$((LIFECYCLE_FAILURES+1)); }
  done < <(printf '%s' "$spec" | jq -c "$JQ_NORMALIZE")
}
step "4/8" "initializeCommand..."
run_outer "$(printf '%s' "$CONFIG_JSON" | jq -c '.initializeCommand // null')"

# ---------------------------------------------------------------------------
# 5. Build
# ---------------------------------------------------------------------------
REUSING=0
if [ "$REBUILD" = "1" ]; then
  "$PODMAN" rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  [ "$NO_CACHE" = "1" ] && { "$PODMAN" rmi -f "$IMAGE_NAME" >/dev/null 2>&1 || true; }
elif "$PODMAN" container exists "$CONTAINER_NAME" 2>/dev/null; then
  STORED_FP="$("$PODMAN" inspect --format json "$CONTAINER_NAME" 2>/dev/null \
    | jq -r '.[0].Config.Labels["che.devcontainer.config"] // ""' 2>/dev/null || echo "")"
  if [ "$STORED_FP" = "$CONFIG_FINGERPRINT" ]; then
    REUSING=1; step "5/8" "reusing existing container (config unchanged)."
    "$PODMAN" start "$CONTAINER_NAME" >/dev/null 2>&1 || true
  else
    step "5/8" "devcontainer.json changed; rebuilding."
    "$PODMAN" rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  fi
fi
if [ "$REUSING" = "0" ]; then
  run_preflight
  step "5/8" "building image (slow part)..."
  BUILD_ARGS=(--docker-path="$PODMAN" --workspace-folder "$PROJECT_DIR" --image-name "$IMAGE_NAME")
  [ "$NO_CACHE" = "1" ] && BUILD_ARGS+=(--no-cache)
  "$DEVCONTAINER_BIN" build "${BUILD_ARGS[@]}" || {
      echo "  build failed" >&2; exit 1; }
fi

# ---------------------------------------------------------------------------
# 6. Merged metadata from the built image
# ---------------------------------------------------------------------------
# `devcontainer build` writes a devcontainer.metadata LABEL containing the FULLY MERGED
# config — the base image's metadata, every Feature's contributions, and devcontainer.json.
# This is where remoteUser actually lives for most real repos (vscode-remote-try-node has
# its remoteUser line commented out, but the image metadata says "node"). Reading only
# devcontainer.json means lifecycle commands run as root and write root-owned node_modules
# into the bind mount. Using --include-merged-configuration instead would need a registry
# round-trip; the label is local and already merged.
JQ_MERGE='
def last_of($k): [ .[] | .[$k] // empty ] | last // null;
def all_of($k):  [ .[] | .[$k] // empty ];
{ remoteUser: last_of("remoteUser"), containerUser: last_of("containerUser"),
  workspaceFolder: last_of("workspaceFolder"),
  remoteEnv:    ( [ .[] | .remoteEnv    // {} ] | add // {} ),
  containerEnv: ( [ .[] | .containerEnv // {} ] | add // {} ),
  onCreateCommand: all_of("onCreateCommand"), updateContentCommand: all_of("updateContentCommand"),
  postCreateCommand: all_of("postCreateCommand"), postStartCommand: all_of("postStartCommand"),
  postAttachCommand: all_of("postAttachCommand") }'

META='{}'
label="$("$PODMAN" inspect --format json "$IMAGE_NAME" 2>/dev/null \
  | jq -r '.[0].Config.Labels["devcontainer.metadata"] // ""')"
if [ -n "$label" ]; then
  META="$(printf '%s' "$label" | jq -c "$JQ_MERGE" 2>/dev/null || echo '{}')"
fi
# Fall back to devcontainer.json for anything the label did not provide.
META="$(jq -n --argjson m "$META" --argjson c "$CONFIG_JSON" '
  { remoteUser: ($m.remoteUser // $c.remoteUser // $c.containerUser // null),
    workspaceFolder: ($m.workspaceFolder // $c.workspaceFolder // "/workspace"),
    remoteEnv: (($c.remoteEnv // {}) + ($m.remoteEnv // {})),
    containerEnv: (($c.containerEnv // {}) + ($m.containerEnv // {})),
    hooks: { onCreateCommand: ($m.onCreateCommand // []), updateContentCommand: ($m.updateContentCommand // []),
             postCreateCommand: ($m.postCreateCommand // []), postStartCommand: ($m.postStartCommand // []),
             postAttachCommand: ($m.postAttachCommand // []) } }')"

REMOTE_USER="$(printf '%s' "$META" | jq -r '.remoteUser // empty')"
WORKSPACE_FOLDER="$(printf '%s' "$META" | jq -r '.workspaceFolder')"
echo "  remoteUser=${REMOTE_USER:-<image default>}  workspaceFolder=${WORKSPACE_FOLDER}"

env_args() {  # $1 = remoteEnv|containerEnv
  printf '%s' "$META" | jq -r --arg k "$1" '(.[$k] // {}) | to_entries[]
    | select(.value | tostring | test("\\$\\{") | not) | "-e", "\(.key)=\(.value)"'
}
CONTAINER_ENV=(); mapfile -t CONTAINER_ENV < <(env_args containerEnv)
REMOTE_ENV=();    mapfile -t REMOTE_ENV    < <(env_args remoteEnv)
skipped="$(printf '%s' "$META" | jq -r '[(.remoteEnv//{}),(.containerEnv//{})] | add | to_entries[]
  | select(.value|tostring|test("\\$\\{")) | .key' | paste -sd, -)"
[ -n "$skipped" ] && echo "  NOTE: env with \${...} substitution skipped: $skipped" >&2

# ---------------------------------------------------------------------------
# 7. Run the container, with UID parity
# ---------------------------------------------------------------------------
# The whole point of outer-editor is "edit outside, run inside" — so the two sides must agree
# on file ownership. Without keep-id, anything created inside (node_modules, build output,
# .venv) is owned by a subuid and the editor cannot modify or delete it, which is what forced
# the chmod 777 workaround. keep-id:uid=,gid= maps our uid to the remoteUser inside, so files
# created either way are owned by us. Requires overlay (fuse) — VFS cannot chown.
KEEP_ID_ARGS=()
if [ "$REUSING" = "0" ] && [ "${NO_KEEP_ID:-0}" != "1" ] && [ -c /dev/fuse ]; then
  IN_UID=""; IN_GID=""
  if [ -n "$REMOTE_USER" ]; then
    IN_UID="$("$PODMAN" run --rm "$IMAGE_NAME" id -u "$REMOTE_USER" 2>/dev/null | tr -dc '0-9')"
    IN_GID="$("$PODMAN" run --rm "$IMAGE_NAME" id -g "$REMOTE_USER" 2>/dev/null | tr -dc '0-9')"
  fi
  if [ -n "$IN_UID" ] && [ -n "$IN_GID" ]; then
    KEEP_ID_ARGS=(--userns="keep-id:uid=${IN_UID},gid=${IN_GID}")
  else
    KEEP_ID_ARGS=(--userns=keep-id)
  fi
fi

# The dev container does NOT receive cluster credentials. It runs arbitrary repository
# code and with --network=host already reaches che-code (:3100) and machine-exec (:3333).
# VS Code dev containers do not expose Kubernetes service-account tokens; cluster tooling
# lives in UDI — use a regular editor terminal for kubectl/oc.
start_container() {   # $1 = extra args array name
  local -n extra="$1"
  "$PODMAN" run -d --name "$CONTAINER_NAME" --network=host \
    --label "che.devcontainer.config=${CONFIG_FINGERPRINT}" \
    "${extra[@]}" \
    -v "$PROJECT_DIR:${WORKSPACE_FOLDER}" \
    "${CONTAINER_ENV[@]}" \
    "$IMAGE_NAME" sleep infinity
}

KEEP_ID_OK=0
if [ "$REUSING" = "0" ]; then
  step "6/8" "starting container..."
  "$PODMAN" rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
  if [ "${#KEEP_ID_ARGS[@]}" -gt 0 ] && start_container KEEP_ID_ARGS >/dev/null 2>&1; then
    KEEP_ID_OK=1; echo "  uid parity: ${KEEP_ID_ARGS[*]}"
  else
    [ "${#KEEP_ID_ARGS[@]}" -gt 0 ] && echo "  keep-id unavailable; falling back (files created inside will be subuid-owned)" >&2
    "$PODMAN" rm -f "$CONTAINER_NAME" >/dev/null 2>&1 || true
    NONE=(); start_container NONE >/dev/null || { echo "  could not start container" >&2; exit 1; }
  fi
else
  step "6/8" "container already running."
  # A reused container keeps the user-namespace mapping it was created with, so ask the
  # container rather than trusting a variable the create path never set. Without this,
  # KEEP_ID_OK stays 0 on every reuse and the fallback chmod below walks the whole project
  # and widens permissions on a container that already has uid parity.
  #
  # JSON + jq, not --format: `.HostConfig.IDMappings.UidMap` is the JSON key but not the Go
  # field name, and the template form fails with "can't evaluate field UidMap in type
  # *define.InspectIDMappings" on podman 5.8.
  if "$PODMAN" inspect "$CONTAINER_NAME" --format json 2>/dev/null \
       | jq -e '(.[0].HostConfig.IDMappings.UidMap // []) | length > 0' >/dev/null 2>&1; then
    KEEP_ID_OK=1
    echo "  uid parity: already active on the reused container"
  fi
fi

# Only widen permissions when uid parity failed. a+rwX (not 777) so the execute bit is not
# set on every tracked file, which would make git report the whole repo as modified.
if [ "$KEEP_ID_OK" = "0" ]; then
  "$PODMAN" exec --user 0 "$CONTAINER_NAME" chmod -R a+rwX "$WORKSPACE_FOLDER" 2>/dev/null || true
fi
"$PODMAN" exec "$CONTAINER_NAME" git config --global --replace-all safe.directory "$WORKSPACE_FOLDER" 2>/dev/null || true

# ---------------------------------------------------------------------------
# 8. Lifecycle commands
# ---------------------------------------------------------------------------
EXEC_USER=(); [ -n "$REMOTE_USER" ] && EXEC_USER=(-u "$REMOTE_USER")
CREATE_MARKER=/tmp/.devcontainer-create-hooks-done

run_hook() {   # $1 = hook name
  local hook="$1" entries line label rc
  entries="$(printf '%s' "$META" | jq -c --arg k "$hook" '.hooks[$k][]?')"
  [ -n "$entries" ] || return 0
  while IFS= read -r spec; do
    [ -z "$spec" ] && continue
    while IFS= read -r line; do
      [ -z "$line" ] && continue
      label="$(printf '%s' "$line" | jq -r '.name')"
      local -a argv=(); mapfile -t argv < <(printf '%s' "$line" | jq -r '.argv[]')
      [ "${#argv[@]}" -eq 0 ] && continue
      echo "  -> ${hook}${label:+ [$label]}: ${argv[*]}"
      rc=0
      "$PODMAN" exec "${EXEC_USER[@]}" "${REMOTE_ENV[@]}" -w "$WORKSPACE_FOLDER" \
        "$CONTAINER_NAME" "${argv[@]}" || rc=$?
      [ "$rc" -ne 0 ] && { echo "  !! ${hook} exited ${rc}" >&2
                           LIFECYCLE_FAILURES=$((LIFECYCLE_FAILURES+1)); }
    done < <(printf '%s' "$spec" | jq -c "$JQ_NORMALIZE")
  done <<< "$entries"
}

step "7/8" "lifecycle commands..."
if "$PODMAN" exec "$CONTAINER_NAME" test -f "$CREATE_MARKER" 2>/dev/null; then
  echo "  creation hooks already ran for this container."
else
  run_hook onCreateCommand; run_hook updateContentCommand; run_hook postCreateCommand
  "$PODMAN" exec "$CONTAINER_NAME" touch "$CREATE_MARKER" 2>/dev/null || true
fi
run_hook postStartCommand
run_hook postAttachCommand

INNER_SHELL=/bin/sh
"$PODMAN" exec "$CONTAINER_NAME" sh -c 'command -v bash' >/dev/null 2>&1 && INNER_SHELL=bash

if [ "$LIFECYCLE_FAILURES" -gt 0 ]; then
  echo; echo "  WARNING: ${LIFECYCLE_FAILURES} lifecycle command(s) failed."
  [ "$STRICT_LIFECYCLE" = "1" ] && exit 1
fi

_close_phase
echo
echo "=== ready ==="
printf '  timing: total %ss' "$(( $(date +%s) - T_START ))"
for e in "${PHASE_LOG[@]}"; do
  d="${e%%|*}"; l="${e#*|}"; [ "$d" -ge 2 ] && printf ' | %s %ss' "${l%% *}" "$d"
done
echo
echo "  Open a shell inside it with the command shown below."
echo "  Files:  ${PROJECT_DIR} <-> ${WORKSPACE_FOLDER}"
[ "$KEEP_ID_OK" = "1" ] && echo "  UID parity ON — files created inside are owned by you." \
                        || echo "  UID parity OFF — files created inside are subuid-owned."
echo "  Shell:  ${PODMAN} exec -it ${CONTAINER_NAME} ${INNER_SHELL}"
exit 0
)
SETUP_RC=$?
# Clear the published PID while we still own the lock, before another run can acquire it.
if [ "$LOCK_HELD" = 1 ]; then
  : > "$LOCK_FILE"
fi
exit "$SETUP_RC"
