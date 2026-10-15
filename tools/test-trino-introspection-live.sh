#!/usr/bin/env bash
set -euo pipefail

[[ ${TRINO_LIVE:-0} == 1 ]] || { echo "Opt in with TRINO_LIVE=1 tools/test-trino-introspection-live.sh" >&2; exit 2; }
root=$(cd "$(dirname "$0")/.." && pwd)
cd "$root"
scratch="$root/build/introspection-parity/live"
mkdir -p "$scratch"
exec 9>"$scratch/lane.lock"
flock -n 9 || { echo "This live lane is already running" >&2; exit 75; }
running=$(docker ps --format '{{.Image}} {{.Names}}')
if grep -Eiq 'trino|clickhouse|doris|test-server|sql-test' <<<"$running"; then
    echo "Another task engine is running; live lane deferred:" >&2
    echo "$running" >&2
    exit 75
fi
name="trino-introspection-$$"
network="$name-net"
container=""
cleanup() {
    status=$?
    trap - EXIT
    if [[ -n $container ]]; then
        docker logs "$container" > "$scratch/server.log" 2>&1 || true
        docker rm -f "$container" >/dev/null 2>&1 || true
    fi
    docker network rm "$network" >/dev/null 2>&1 || true
    exit "$status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
mkdir -p "$scratch/etc/catalog"
cat > "$scratch/etc/node.properties" <<'EOF'
node.environment=introspection_test
node.id=introspection-test
node.data-dir=/data/trino
EOF
cat > "$scratch/etc/config.properties" <<'EOF'
coordinator=true
node-scheduler.include-coordinator=true
http-server.http.port=8080
discovery.uri=http://localhost:8080
query.max-memory=256MB
query.max-memory-per-node=256MB
memory.heap-headroom-per-node=128MB
http-server.threads.max=32
query.max-split-manager-callback-threads=8
query.remote-task.max-callback-threads=8
task.http-response-threads=8
exchange.client-threads=4
exchange.page-buffer-client.max-callback-threads=4
EOF
for client in memory-manager announcer node-manager exchange scheduler worker-info; do
    printf '%s.http-client.max-threads=16\n%s.http-client.min-threads=2\n' "$client" "$client" >> "$scratch/etc/config.properties"
done
cat > "$scratch/etc/jvm.config" <<'EOF'
-server
-Xmx768M
-XX:+UseG1GC
-XX:ActiveProcessorCount=2
-Djdk.attach.allowAttachSelf=true
EOF
cat > "$scratch/etc/catalog/memory.properties" <<'EOF'
connector.name=memory
memory.max-data-per-node=128MB
EOF
cat > "$scratch/etc/catalog/tpch.properties" <<'EOF'
connector.name=tpch
tpch.splits-per-node=1
EOF
docker network create --internal "$network" >/dev/null
container=$(docker run -d --name "$name" --network "$network" --memory=2g --memory-swap=2g \
    --cpus=2 --pids-limit=512 --label dev.sort.task.engine=trino-introspection \
    -v "$scratch/etc:/etc/trino:ro" trinodb/trino:483)
# The host reaches the private bridge directly; internal networks do not publish host ports.
address=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' "$container")
[[ -n $address ]] || { echo "Task container has no private address" >&2; exit 1; }
ready=false
for attempt in {1..90}; do
    if curl --fail --silent "http://$address:8080/v1/info" | grep -q '"starting":false'; then ready=true; break; fi
    if [[ $(docker inspect -f '{{.State.Running}}' "$container" 2>/dev/null || true) != true ]]; then
        docker logs "$container" >&2
        exit 1
    fi
    sleep 2
done
[[ $ready == true ]] || { echo "Trino did not become ready" >&2; exit 1; }
export TRINO_LIVE_URL="jdbc:trino://$address:8080?sessionUser=introspection"
printf '%s\n' "$TRINO_LIVE_URL" > "$scratch/url.txt"
echo "Running live IDE lane against $TRINO_LIVE_URL"
if (( $# )); then tasks=("$@"); else tasks=(test test262 test263); fi
if [[ ${TRINO_LIVE_CLEARED_ONLY:-0} == 1 ]]; then
    filters=(--tests '*TrinoAutoIntrospectLiveTest.testClearedModelCompletionRestoresCatalogAndSchema')
    lane_timeout=5m
else
    filters=(--tests '*TrinoAutoIntrospectLiveTest' --tests '*TrinoLiveCatalogLiveTest'
        --tests '*TrinoJdbcMetadataTruthTest' --tests '*TrinoPgCatalogLandmineTest'
        --tests '*TrinoConsoleTruthTest' --tests '*TrinoAuthMatrixTest')
    lane_timeout=15m
fi
# No reusable daemon; timeout and EXIT traps remove the task engine even on test failures.
timeout --signal=TERM --kill-after=30s "$lane_timeout" ./gradlew --no-daemon --max-workers=2 \
    -Dorg.gradle.daemon.registry.base="$scratch/gradle-daemons" --refresh-dependencies \
    "${tasks[@]}" -Ptest.kotlin=absent \
    "${filters[@]}" \
    --continue --console=plain > "$scratch/gradle.log" 2>&1
echo "Live lane passed; reports are under $scratch and build/test-results."
