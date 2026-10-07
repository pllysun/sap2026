#!/usr/bin/env python3
"""Run available local toolchains three times; do not claim production validation."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import time

import build_packs as authored

ROOT = Path(__file__).resolve().parent
JAVA_HOME = Path("/Users/pllysun/Library/Java/JavaVirtualMachines/temurin-21/Contents/Home")
ORACLES = [authored.clock_oracle, authored.bill_oracle, authored.divide_oracle, authored.staircase_oracle,
           authored.checksum_oracle, authored.multiples_oracle, authored.border_oracle, authored.date_oracle,
           authored.recurrence_oracle, authored.fraction_oracle]


def run(command, *, input_text=None):
    result = subprocess.run(command, input=input_text, text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8)
    if result.returncode:
        raise AssertionError(f"command failed: {command}; {result.stderr[:1500]}")
    return result.stdout


def main():
    start = time.monotonic()
    toolchains = {
        "c": shutil.which("clang"),
        "cpp": shutil.which("clang++"),
        "python": sys.executable,
        "java": str(JAVA_HOME / "bin" / "javac") if (JAVA_HOME / "bin" / "javac").exists() else None,
    }
    versions = {}
    for language, path in toolchains.items():
        if path:
            flag = "-version" if language == "java" else "--version"
            info = subprocess.run([path, flag], text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            versions[language] = (info.stdout or info.stderr).splitlines()[0]
    results = []
    total_runs = 0
    total_cases = 0
    with tempfile.TemporaryDirectory(prefix="sap-original-acm-a-") as work:
        temp = Path(work)
        for ordinal, item in enumerate(json.loads((ROOT / "manifest.json").read_text()), 1):
            pack_path = ROOT / item["path"]
            pack = json.loads(pack_path.read_text())
            assert pack["sourcePlatform"] == "原创" and pack["difficulty"] == "EASY"
            assert pack["modes"] == ["STDIO"] and pack["defaultMode"] == "STDIO" and pack["checker"] == "TOKENS"
            assert set(pack["profiles"]) == set(authored.LANGUAGES) == set(pack["references"])
            assert len(pack["cases"]) >= 20 and sum(case["sample"] for case in pack["cases"]) == 3
            assert len({case["name"] for case in pack["cases"]}) == len(pack["cases"])
            assert "链表" not in " ".join(pack["tags"]) + pack["description"]
            for language in authored.LANGUAGES:
                assert set(pack["references"][language]) == {"STDIO"}
                assert pack["profiles"][language]["starterFunction"] == "" and pack["profiles"][language]["functionDriver"] == ""
                assert pack["profiles"][language]["starterStdio"].count("\n") >= 3
                assert pack["references"][language]["STDIO"].count("\n") >= 2
            for case in pack["cases"]:
                tokens = case["input"].split()
                values = tokens if ordinal == 5 else tuple(map(int, tokens))
                expected = ORACLES[ordinal - 1](values)
                assert expected.split() == case["expectedOutput"].split(), (pack["slug"], case["name"])
            directory = temp / pack["slug"]
            directory.mkdir()
            pack_result = {"slug": pack["slug"], "title": pack["title"], "cases": len(pack["cases"]),
                           "publicSamples": 3, "packSha256": hashlib.sha256(pack_path.read_bytes()).hexdigest(), "languages": []}
            total_cases += len(pack["cases"])
            for language, executable in toolchains.items():
                if executable is None:
                    continue
                program = pack["references"][language]["STDIO"]
                if language == "c":
                    path = directory / "main.c"
                    path.write_text(program)
                    binary = directory / "c-program"
                    run([executable, "-std=c17", "-O2", "-Wall", "-Wextra", str(path), "-o", str(binary)])
                    command = [str(binary)]
                elif language == "cpp":
                    path = directory / "main.cpp"
                    path.write_text(program)
                    binary = directory / "cpp-program"
                    run([executable, "-std=c++23", "-O2", "-Wall", "-Wextra", str(path), "-o", str(binary)])
                    command = [str(binary)]
                elif language == "java":
                    path = directory / "Main.java"
                    path.write_text(program)
                    run([executable, "-d", str(directory), str(path)])
                    command = [str(JAVA_HOME / "bin" / "java"), "-Xmx128m", "-cp", str(directory), "Main"]
                else:
                    path = directory / "main.py"
                    path.write_text(program)
                    command = [executable, str(path)]
                for round_number in range(1, 4):
                    for case in pack["cases"]:
                        output = run(command, input_text=case["input"])
                        assert output.split() == case["expectedOutput"].split(), (pack["slug"], language, round_number, case["name"], output)
                        total_runs += 1
                pack_result["languages"].append({"language": language, "status": "PASSED", "rounds": 3, "caseExecutions": len(pack["cases"]) * 3})
            results.append(pack_result)
            print(f"{pack['slug']} {pack['title']}: {len(pack['cases'])} cases x {len(pack_result['languages'])} languages x 3 rounds PASS", flush=True)
    report = {"environment": "LOCAL_NATIVE_NOT_GO_JUDGE", "packCount": len(results), "caseCount": total_cases,
              "rounds": 3, "caseExecutions": total_runs, "status": "PASSED", "requestedLanguages": list(authored.LANGUAGES),
              "missingLanguages": [language for language in authored.LANGUAGES if language not in toolchains or not toolchains[language]],
              "toolVersions": versions, "elapsedSeconds": round(time.monotonic() - start, 2), "results": results,
              "productionValidation": "待 root 使用 GCC15.3/JDK27/Python3.14.7/Rust1.98.1 判题底座完成五语言三轮验证"}
    (ROOT / "local-validation.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: value for key, value in report.items() if key not in ("results", "toolVersions")}, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
