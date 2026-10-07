#!/usr/bin/env bash
set -euo pipefail
base_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
node_token_file=''
node_cpu=1
node_memory=640
node_concurrency=1
node_port=5051
node_prefix=sapjudgernode
node_name=sap-judger-node
node_image=pllysun/sap:1.5.9
node_push_url=''
node_tls_cert=''
node_tls_key=''
while (($#)); do
    case "$1" in
        --token-file) node_token_file="$2";;
        --cpu) node_cpu="$2";;
        --memory-mb) node_memory="$2";;
        --concurrency) node_concurrency="$2";;
        --port) node_port="$2";;
        --prefix) node_prefix="$2";;
        --name) node_name="$2";;
        --image) node_image="$2";;
        --push-url) node_push_url="$2";;
        --tls-cert) node_tls_cert="$2";;
        --tls-key) node_tls_key="$2";;
        *) echo "Unknown option: $1" >&2; exit 1;;
    esac
    shift 2
done
[[ $EUID == 0 && -f "$node_token_file" && "$node_port" =~ ^[0-9]+$ ]] || { echo 'Run as root with --token-file and a valid --port.' >&2; exit 1; }
[[ $(uname -m) == x86_64 ]] || { echo 'This runtime requires a native Linux x86_64 server; emulation cannot provide the required sandbox syscalls.' >&2; exit 1; }
docker inspect "$node_name" >/dev/null 2>&1 && { echo 'A container with this name already exists. Choose a different --name or manage the existing container.' >&2; exit 1; }
node_token_file="$(realpath "$node_token_file")"
chmod 600 "$node_token_file"
mapfile -t node_mounts < <(python3 "$base_dir/prepare.py" --prefix "$node_prefix" --cpu "$node_cpu" --memory-mb "$node_memory" --concurrency "$node_concurrency")
(( ${#node_mounts[@]} > 0 )) || { echo 'Node cgroups were not prepared.' >&2; exit 1; }
mount_args=()
for node_mount in "${node_mounts[@]}"; do
    node_target="$(dirname "$node_mount")"
    [[ -f /sys/fs/cgroup/cgroup.controllers ]] && node_target="$node_mount"
    mount_args+=(--mount "type=bind,src=$node_mount,dst=$node_target")
done
tls_args=()
if [[ -n "$node_tls_cert" ]]; then
    [[ -f "$node_tls_cert" && -f "$node_tls_key" ]] || exit 1
    tls_args+=(--mount "type=bind,src=$(realpath "$node_tls_cert"),dst=/run/tls/cert.pem,readonly" --mount "type=bind,src=$(realpath "$node_tls_key"),dst=/run/tls/key.pem,readonly" -e NODE_TLS_CERT=/run/tls/cert.pem -e NODE_TLS_KEY=/run/tls/key.pem)
fi
# Persist the dedicated host cgroups across host restarts when systemd is present.
if command -v systemctl >/dev/null; then
    install -d -m 755 /usr/local/lib/sap-judger-node
    install -m 755 "$base_dir/prepare.py" "/usr/local/lib/sap-judger-node/$node_prefix.py"
    cat > "/etc/systemd/system/sap-judger-$node_prefix.service" <<UNIT
[Unit]
Description=SAP judge resource budget ($node_prefix)
Before=docker.service
[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/usr/bin/python3 /usr/local/lib/sap-judger-node/$node_prefix.py --prefix $node_prefix --cpu $node_cpu --memory-mb $node_memory --concurrency $node_concurrency
[Install]
WantedBy=multi-user.target
UNIT
    systemctl daemon-reload
    systemctl enable "sap-judger-$node_prefix.service"
fi
docker run -d --name "$node_name" --restart unless-stopped --cgroupns=host \
    --cap-add SYS_ADMIN --cap-add SYS_PTRACE --cap-add SYS_RESOURCE \
    --security-opt "seccomp=$base_dir/seccomp.json" --security-opt systempaths=unconfined \
    "${mount_args[@]}" "${tls_args[@]}" \
    --mount "type=bind,src=$node_token_file,dst=/run/secrets/judger-token,readonly" \
    -v "$node_name-data:/app/data" -p "$node_port:5051" \
    -e JUDGER_NODE_ONLY=true -e "NODE_CGROUP_PREFIX=$node_prefix" \
    -e "NODE_CPU=$node_cpu" -e "NODE_MEMORY_MB=$node_memory" -e "NODE_CONCURRENCY=$node_concurrency" \
    -e "NODE_PUSH_URL=$node_push_url" "$node_image"
echo "Node started on port $node_port. Add its address and token in Algorithm Library > Unified Management."
