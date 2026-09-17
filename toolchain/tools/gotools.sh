#!/usr/bin/env bash
# Go-based developer tools, cross-compiled with the host Go toolchain
# (GOOS=android works out of the box for pure-Go programs):
#   yq    YAML/JSON/XML query + edit
#   shfmt shell script formatter
#   gofmt the Go formatter (from the host GOROOT sources)
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/../env.sh"
toolchain_env "${ABI:?ABI is required}"

command -v go >/dev/null || die "host go toolchain is required"

export GOOS=android GOARCH=arm64 CGO_ENABLED=0
ldflags=(-ldflags "-s -w")

# `go install pkg@version` refuses to cross-compile with GOBIN set, so pin the
# versions in a throwaway module and build the commands explicitly.
moddir="$TOOL_BUILD/gotools-mod"
mkdir -p "$moddir"
cd "$moddir"
if [[ ! -f go.mod ]]; then
    go mod init codex-android/toolchain-gotools >/dev/null 2>&1
fi

log "go get yq v$YQ_VER"
go get "github.com/mikefarah/yq/v4@v$YQ_VER"
log "go get shfmt v$SHFMT_VER"
go get "mvdan.cc/sh/v3/cmd/shfmt@v$SHFMT_VER"

log "go build yq / shfmt"
go build "${ldflags[@]}" -o "$TOOL_OUT/bin/yq" github.com/mikefarah/yq/v4
go build "${ldflags[@]}" -o "$TOOL_OUT/bin/shfmt" mvdan.cc/sh/v3/cmd/shfmt

# gofmt ships inside the host Go distribution; build it straight from GOROOT.
if [[ ! -x "$TOOL_OUT/bin/gofmt" ]]; then
    log "go build gofmt"
    goroot="$(go env GOROOT)"
    (cd "$goroot/src" && GO111MODULE=off go build "${ldflags[@]}" -o "$TOOL_OUT/bin/gofmt" cmd/gofmt)
fi

for tool in yq shfmt gofmt; do
    log "$tool: $(file -b "$TOOL_OUT/bin/$tool")"
done
