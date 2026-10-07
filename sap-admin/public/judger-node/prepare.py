#!/usr/bin/env python3
"""Prepare a dedicated cgroup on the node host (v1 or v2). Run as root."""
import argparse
import os
from pathlib import Path
import re

def prepare(prefix, cpu, memory, concurrency):
    if os.geteuid() != 0 or not re.fullmatch(r'[a-zA-Z0-9_-]{1,50}', prefix):
        raise ValueError('Run as root with a valid dedicated cgroup prefix')
    if cpu < 1 or cpu > (os.cpu_count() or 1) or not 1 <= concurrency <= 32 or memory < concurrency * 640:
        raise ValueError('Reserve at least one CPU and 640 MiB per concurrent task; concurrency must be 1..32')
    mem_total = int(next(line for line in Path('/proc/meminfo').read_text().splitlines() if line.startswith('MemTotal:')).split()[1]) // 1024
    if memory > mem_total - 512:
        raise ValueError('Keep at least 512 MiB of host memory outside the judge budget')
    root = Path('/sys/fs/cgroup')
    mounts = []
    if (root / 'cgroup.controllers').exists():
        available = (root / 'cgroup.controllers').read_text().split()
        required = ['cpu', 'memory', 'pids']
        if 'cpuset' in available:
            required.append('cpuset')
        if not set(required).issubset(available):
            raise RuntimeError('CPU, memory and pids controllers must be delegated to this host')
        (root / 'cgroup.subtree_control').write_text(' '.join('+' + c for c in required))
        target = root / prefix
        target.mkdir(exist_ok=True)
        for key, value in {'memory.max': str(memory * 1048576), 'memory.swap.max': '0', 'cpu.max': '%s 100000' % int(cpu * 100000), 'pids.max': str(128 * concurrency + 128)}.items():
            (target / key).write_text(value)
        (target / 'cgroup.subtree_control').write_text(' '.join('+' + c for c in required))
        mounts.append(str(target))
    else:
        seen = set()
        for controller in ['memory', 'pids', 'cpu', 'cpuacct', 'cpuset']:
            parent = (root / controller).resolve()
            if not parent.is_dir():
                raise RuntimeError('Missing cgroup controller: ' + controller)
            target = parent / prefix
            if target in seen:
                continue
            seen.add(target)
            target.mkdir(exist_ok=True)
            values = {'memory.limit_in_bytes': str(memory * 1048576), 'pids.max': str(128 * concurrency + 128), 'cpu.cfs_period_us': '100000', 'cpu.cfs_quota_us': str(int(cpu * 100000))}
            for key, value in values.items():
                if (target / key).exists():
                    (target / key).write_text(value)
            for key in ['cpuset.cpus', 'cpuset.mems']:
                if (target / key).exists():
                    (target / key).write_text((parent / key).read_text())
            mounts.append(str(target))
    ns = Path('/proc/sys/user/max_user_namespaces')
    if ns.exists() and int(ns.read_text()) < 1024:
        ns.write_text('1024')
    return mounts

if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--prefix', default='sapjudger')
    p.add_argument('--cpu', type=float, default=1)
    p.add_argument('--memory-mb', type=int, default=640)
    p.add_argument('--concurrency', type=int, default=1)
    a = p.parse_args()
    for path in prepare(a.prefix, a.cpu, a.memory_mb, a.concurrency):
        print(path)
