"""Validate this directory's original packs against independent Python oracles.

The default is a strict five-language check. For local machines without Rust,
run --languages c cpp java python and preserve the explicit language list in
the report. Online go-judge validation is a separate release requirement.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time

from generate import ORACLES, LANGUAGES

ROOT = Path(__file__).resolve().parent
JAVA_HOME = Path("/Users/pllysun/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home")


def tool(name, environment):
    override = os.environ.get(environment)
    if override:
        return override
    if name in ("java", "javac") and (JAVA_HOME / "bin" / name).exists():
        return str(JAVA_HOME / "bin" / name)
    return shutil.which(name)


TOOLS = {
    "c": tool("cc", "CC"),
    "cpp": tool("c++", "CXX"),
    "java": tool("javac", "JAVAC"),
    "python": tool("python3", "PYTHON"),
    "rust": tool("rustc", "RUSTC"),
}


def run(command, **kwargs):
    return subprocess.run(command, capture_output=True, text=True, timeout=30, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--languages", nargs="+", choices=LANGUAGES, default=list(LANGUAGES))
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--report", type=Path, default=ROOT / "validation-local.json")
    args = parser.parse_args()
    if args.rounds < 1:
        raise ValueError("rounds must be positive")
    for language in args.languages:
        if not TOOLS[language]:
            raise RuntimeError(f"Missing requested {language} compiler/runtime; configure toolchain override")
    manifest = json.loads((ROOT / "manifest.json").read_text())
    report = dict(
        generatedAt=datetime.now(timezone.utc).isoformat(),
        scope="original-acm-011..020",
        rounds=args.rounds,
        languages=args.languages,
        missingLocalToolchains=[language for language in LANGUAGES if not TOOLS[language]],
        environment={},
        schemaChecks=0,
        oracleChecks=0,
        results=[],
        onlineProductionValidation="Required separately: Rust 1.98.1, JDK 27 and actual go-judge runtime",
    )
    for language in args.languages:
        version = run([TOOLS[language], "-version" if language == "java" else "--version"])
        report["environment"][language] = (version.stdout + version.stderr).splitlines()[0]
    for entry in manifest:
        file = ROOT / entry["path"]
        raw = file.read_bytes()
        pack = json.loads(raw)
        index = int(pack["slug"].rsplit("-", 1)[1])
        assert pack["sourcePlatform"] == "原创" and pack["sourceId"] == f"ORIGINAL-ACM-{index:03}"
        assert pack["modes"] == ["STDIO"] and pack["defaultMode"] == "STDIO" and pack["checker"] == "TOKENS"
        assert "链表" not in " ".join([pack["description"], *pack["tags"]])
        assert len(pack["cases"]) >= 20 and sum(case["sample"] for case in pack["cases"]) == 3
        assert all(case["sample"] for case in pack["cases"][:3])
        assert len(set(case["input"] for case in pack["cases"])) == len(pack["cases"])
        for language in LANGUAGES:
            assert pack["profiles"][language]["starterStdio"].count("\n") >= 3
            assert pack["references"][language]["STDIO"].count("\n") >= 3
            assert pack["profiles"][language]["starterFunction"] == ""
            assert pack["profiles"][language]["functionDriver"] == ""
        report["schemaChecks"] += 1
        for case in pack["cases"]:
            assert case["expectedOutput"] == ORACLES[index](case["input"]), f"Oracle mismatch {pack['slug']}/{case['name']}"
            report["oracleChecks"] += 1
        for language in args.languages:
            with tempfile.TemporaryDirectory(prefix=f"sap-original-{index}-{language}-") as temporary:
                work = Path(temporary)
                source = work / {"c": "main.c", "cpp": "main.cpp", "java": "Main.java", "python": "main.py", "rust": "main.rs"}[language]
                source.write_text(pack["references"][language]["STDIO"])
                binary = work / "main"
                if language == "c":
                    compile_command = [TOOLS[language], "-std=c17", "-O2", "-Wall", "-Wextra", str(source), "-o", str(binary)]
                elif language == "cpp":
                    compile_command = [TOOLS[language], "-std=c++17", "-O2", "-Wall", "-Wextra", str(source), "-o", str(binary)]
                elif language == "java":
                    compile_command = [TOOLS[language], str(source)]
                elif language == "rust":
                    compile_command = [TOOLS[language], "--edition=2024", "-O", str(source), "-o", str(binary)]
                else:
                    compile_command = [TOOLS[language], "-m", "py_compile", str(source)]
                compiled = run(compile_command, cwd=work)
                if compiled.returncode:
                    raise RuntimeError(f"{pack['slug']}/{language} compile failed: {compiled.stderr[:1000]}")
                execute = [str(binary)] if language in ("c", "cpp", "rust") else [tool("java", "JAVA"), "-cp", str(work), "Main"] if language == "java" else [TOOLS[language], str(source)]
                started = time.monotonic()
                worst_seconds = 0.0
                executions = 0
                for round_index in range(args.rounds):
                    for case in pack["cases"]:
                        case_start = time.monotonic()
                        result = run(execute, input=case["input"], cwd=work)
                        worst_seconds = max(worst_seconds, time.monotonic() - case_start)
                        if result.returncode or result.stdout.split() != case["expectedOutput"].split():
                            raise RuntimeError(f"{pack['slug']}/{language}/{case['name']}/round-{round_index + 1}: exit={result.returncode}; actual={result.stdout[:200]!r}; expected={case['expectedOutput'][:200]!r}; stderr={result.stderr[:500]!r}")
                        executions += 1
                item = dict(slug=pack["slug"], language=language, mode="STDIO", rounds=args.rounds,
                            cases=len(pack["cases"]), executions=executions, status="PASS",
                            seconds=round(time.monotonic() - started, 3),
                            worstCaseWallSeconds=round(worst_seconds, 3),
                            compilerWarnings=compiled.stderr.strip(),
                            packSha256=hashlib.sha256(raw).hexdigest())
                report["results"].append(item)
                args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
                print(json.dumps({key: item[key] for key in ("slug", "language", "rounds", "executions", "status")}), flush=True)
    report["status"] = "PASS"
    report["totalExecutions"] = sum(item["executions"] for item in report["results"])
    args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps(dict(status="PASS", packs=len(manifest), oracleChecks=report["oracleChecks"], executions=report["totalExecutions"], languages=args.languages)), flush=True)


if __name__ == "__main__":
    main()
