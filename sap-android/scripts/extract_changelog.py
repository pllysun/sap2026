#!/usr/bin/env python3
"""从 Changelog.kt 提取指定 App 版本的用户可见更新说明。"""

from __future__ import annotations

import argparse
import re
from pathlib import Path


def decode_kotlin_string(literal: str) -> str:
    """Decode the quoted Kotlin string format emitted by prepare_changelog.py."""
    if len(literal) < 2 or literal[0] != '"' or literal[-1] != '"':
        raise ValueError("更新日志不是合法的 Kotlin 字符串")

    escapes = {
        '"': '"',
        "\\": "\\",
        "$": "$",
        "b": "\b",
        "f": "\f",
        "n": "\n",
        "r": "\r",
        "t": "\t",
    }
    body = literal[1:-1]
    result: list[str] = []
    index = 0
    while index < len(body):
        char = body[index]
        if char != "\\":
            result.append(char)
            index += 1
            continue

        index += 1
        if index >= len(body):
            raise ValueError("更新日志包含不完整的转义字符")
        escaped = body[index]
        if escaped == "u":
            codepoint = body[index + 1 : index + 5]
            if len(codepoint) != 4 or not re.fullmatch(r"[0-9a-fA-F]{4}", codepoint):
                raise ValueError("更新日志包含无效的 Unicode 转义")
            result.append(chr(int(codepoint, 16)))
            index += 5
            continue
        if escaped not in escapes:
            raise ValueError(f"更新日志包含不支持的转义：\\{escaped}")
        result.append(escapes[escaped])
        index += 1
    return "".join(result)


def extract_changes(source: str, version_code: int, version_name: str) -> list[str]:
    entry_start = re.compile(
        rf'versionCode\s*=\s*{version_code},\s*'
        rf'versionName\s*=\s*"{re.escape(version_name)}"'
    )
    match = entry_start.search(source)
    if match is None:
        raise ValueError(f"找不到 {version_name} ({version_code}) 的 App 更新日志")

    changes_start = re.search(r"changes\s*=\s*listOf\(", source[match.end() :])
    if changes_start is None:
        raise ValueError("找不到 changes 列表")
    cursor = match.end() + changes_start.end()

    changes: list[str] = []
    line_pattern = re.compile(r'^\s*("(?:\\.|[^"\\])*"),\s*$')
    for line in source[cursor:].splitlines():
        if re.fullmatch(r"\s*\),\s*", line):
            break
        line_match = line_pattern.fullmatch(line)
        if line_match is None:
            if line.strip():
                raise ValueError(f"changes 包含不符合约定的行：{line.strip()}")
            continue
        changes.append(decode_kotlin_string(line_match.group(1)))

    if not changes:
        raise ValueError("changes 为空")
    return changes


def main() -> None:
    parser = argparse.ArgumentParser(description="提取 App 内置更新日志")
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    args = parser.parse_args()

    try:
        source = args.changelog.read_text(encoding="utf-8")
        changes = extract_changes(source, args.version_code, args.version_name)
    except (OSError, ValueError) as error:
        raise SystemExit(f"提取更新日志失败：{error}") from error

    print("\n".join(changes))


if __name__ == "__main__":
    main()
