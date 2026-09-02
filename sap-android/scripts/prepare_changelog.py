#!/usr/bin/env python3
"""把一份更新说明安全写入 Changelog.kt 的列表首项。

更新说明支持逐行输入，也支持用 ``||`` 分隔，便于 GitHub Actions
workflow_dispatch 的单行输入框使用。该脚本只负责写 App 内日志；
发布页日志仍由 publish-release.sh 从同一条目提取，避免维护两份文案。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import tempfile
from pathlib import Path


ENTRY_MARKER = "    val entries: List<ChangelogEntry> = listOf(\n"


def kotlin_string(value: str) -> str:
    """Return a Kotlin-compatible quoted string, including literal '$'."""
    return json.dumps(value, ensure_ascii=False).replace("$", r"\$")


def parse_changes(raw: str) -> list[str]:
    normalized = raw.replace("\r\n", "\n").replace("\r", "\n")
    chunks = normalized.splitlines()
    if len(chunks) <= 1 and "||" in normalized:
        chunks = normalized.split("||")

    changes: list[str] = []
    for chunk in chunks:
        value = re.sub(r"^\s*(?:[-*•]\s*)?", "", chunk).strip()
        if value:
            changes.append(value)

    if not changes:
        raise ValueError("更新日志不能为空")
    if len(changes) > 20:
        raise ValueError("更新日志最多 20 条")
    if any(len(change) > 200 for change in changes):
        raise ValueError("单条更新日志不能超过 200 个字符")
    return changes


def insert_entry(
    changelog_file: Path,
    version_code: int,
    version_name: str,
    release_date: str,
    changes: list[str],
) -> None:
    source = changelog_file.read_text(encoding="utf-8")
    needle = f'versionCode = {version_code}, versionName = "{version_name}"'
    if needle in source:
        raise ValueError(f"Changelog.kt 已存在 {version_name} ({version_code})")
    if ENTRY_MARKER not in source:
        raise ValueError("找不到 Changelog.entries 列表插入点")

    change_lines = "\n".join(f"                {kotlin_string(change)}," for change in changes)
    entry = (
        "        ChangelogEntry(\n"
        f'            versionCode = {version_code}, versionName = {kotlin_string(version_name)}, '
        f'date = {kotlin_string(release_date)},\n'
        "            changes = listOf(\n"
        f"{change_lines}\n"
        "            ),\n"
        "        ),\n"
    )
    updated = source.replace(ENTRY_MARKER, ENTRY_MARKER + entry, 1)

    changelog_file.parent.mkdir(parents=True, exist_ok=True)
    fd, temp_name = tempfile.mkstemp(prefix=changelog_file.name, dir=changelog_file.parent)
    try:
        with os.fdopen(fd, "w", encoding="utf-8", newline="\n") as temp:
            temp.write(updated)
        os.replace(temp_name, changelog_file)
    finally:
        if os.path.exists(temp_name):
            os.unlink(temp_name)


def main() -> None:
    parser = argparse.ArgumentParser(description="为正式 App 版本写入内置更新日志")
    parser.add_argument("--changelog", type=Path, required=True)
    parser.add_argument("--version-code", type=int, required=True)
    parser.add_argument("--version-name", required=True)
    parser.add_argument("--date", required=True)
    parser.add_argument("--changes-file", type=Path, required=True)
    args = parser.parse_args()

    if args.version_code <= 0:
        raise SystemExit("versionCode 必须为正整数")
    if not re.fullmatch(r"\d+(?:\.\d+)*", args.version_name):
        raise SystemExit("versionName 必须是点分数字，例如 1.35")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", args.date):
        raise SystemExit("date 必须为 yyyy-MM-dd")

    try:
        changes = parse_changes(args.changes_file.read_text(encoding="utf-8"))
        insert_entry(
            args.changelog,
            args.version_code,
            args.version_name,
            args.date,
            changes,
        )
    except (OSError, ValueError) as error:
        raise SystemExit(f"写入更新日志失败：{error}") from error

    print(
        f"✓ 已写入 App 更新日志：{args.version_name} ({args.version_code})，"
        f"共 {len(changes)} 条"
    )


if __name__ == "__main__":
    main()
