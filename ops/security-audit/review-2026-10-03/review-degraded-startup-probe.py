#!/usr/bin/env python3
"""Exercise the unchanged Agent.launch with a synthetic process and config.

No process, real engine, production connection, or resource-control mutation.
The only writes are temporary fixture files and this review's evidence JSON.
"""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / 'docker/judger-agent.py'
spec = importlib.util.spec_from_file_location('review_agent', SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SyntheticProcess:
    def poll(self):
        return None


def main():
    with tempfile.TemporaryDirectory(prefix='sap-degraded-agent-review-') as tmp:
        directory = Path(tmp)
        token = directory / 'token'
        token.write_text('synthetic-review-token-' + 'x' * 40)
        args = argparse.Namespace(token_file=str(token), state_dir=str(directory),
            initial_stopped=False, memory_mb=640, cpu=1, max_concurrency=1,
            cgroup_prefix='reviewfixture', cpu_sets='', engine_port=5050,
            mount_conf=str(directory / 'missing-mount.yaml'))
        agent = module.Agent(args)
        config = {'runnerConfig': {'cgroupType': 1, 'cgroupControllers': ['memory'],
                                  'mount': [], 'uid': 0, 'gid': 0}}
        agent.engine = lambda *a, **kw: json.dumps(config).encode()
        with patch.object(module.subprocess, 'Popen', return_value=SyntheticProcess()) as popen:
            agent.launch()
            command = popen.call_args.args[0] if popen.called else []
        evidence = {
            'scope': 'unchanged Agent.launch with synthetic Popen and /config; no engine executed',
            'agent_source_sha256': hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
            'synthetic_config': config,
            'missing_mount_config': not Path(args.mount_conf).exists(),
            'reported_state': agent.state,
            'accepted_degraded_config': agent.state == 'RUNNING',
            'command_contains_no_fallback': '-no-fallback' in command,
            'command_contains_src_prefix': '-src-prefix' in command,
            'limitations': 'Proves absence of Agent startup config validation. Actual controller failure is established by Go source review, not a live degraded Linux sandbox.'
        }
    output = Path(__file__).with_name('degraded-startup-results.json')
    output.write_text(json.dumps(evidence, ensure_ascii=False, indent=2) + '\n')
    print(json.dumps(evidence, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
