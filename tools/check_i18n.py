#!/usr/bin/env python3
"""Fails if a UI string used by the Android or web app has no Turkish translation.

Translatable strings are the arguments of `L.tx("...")` (Kotlin UI) and `t("...")` (web/app.js);
enum constructor literals of selected UI enums are included too. Dictionary keys are read from
core/src/commonMain/kotlin/com/shopref/core/i18n/*.kt.
"""
import glob
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
LIT = r'"((?:[^"\\]|\\.)*)"'


def unescape(s: str) -> str:
    return s.replace('\\"', '"').replace("\\'", "'").replace("\\n", "\n").replace("\\\\", "\\")


def dictionary_keys() -> set:
    keys = set()
    for f in glob.glob(str(ROOT / "core/src/commonMain/kotlin/com/shopref/core/i18n/*.kt")):
        src = Path(f).read_text(encoding="utf-8")
        for m in re.finditer(r'^\s*' + LIT + r'\s+to\b', src, re.M):
            keys.add(unescape(m.group(1)))
    return keys


def kotlin_ui_keys() -> set:
    keys = set()
    src = ""
    for f in glob.glob(str(ROOT / "app/src/main/java/**/*.kt"), recursive=True):
        src += Path(f).read_text(encoding="utf-8") + "\n"
    for m in re.finditer(r'L\.tx\(\s*((?:' + LIT + r'\s*\+?\s*)+)[,)]', src):
        keys.add(unescape("".join(re.findall(LIT, m.group(1)))))
    for m in re.finditer(r'L\.tx\(\s*if \([^\n]*?\) ' + LIT + r' else ' + LIT, src):
        keys.update({unescape(m.group(1)), unescape(m.group(2))})
    for m in re.finditer(r'L\.tx\([^\n]*?\?: ' + LIT + r'\)', src):
        keys.add(unescape(m.group(1)))
    for enum in ("Tab", "ReferencePage", "ScrewSystem"):
        m = re.search(r'enum class ' + enum + r'\b.*?\{(.*?)\n\}', src, re.S)
        if m:
            for e in re.finditer(r'\w+\(((?:' + LIT + r',?\s*)+)', m.group(1)):
                keys.update(unescape(k) for k in re.findall(LIT, e.group(1)))
    return keys


def web_ui_keys() -> set:
    keys = set()
    for f in glob.glob(str(ROOT / "web/src/*.js")) + glob.glob(str(ROOT / "web/src/*.html")):
        src = Path(f).read_text(encoding="utf-8")
        for m in re.finditer(r'\bt\(\s*(["\'`])((?:(?!\1)[^\\]|\\.)*)\1', src):
            if "${" not in m.group(2):
                keys.add(unescape(m.group(2)))
        for m in re.finditer(r'data-t="([^"]*)"', src):
            keys.add(m.group(1))
    return keys


def main() -> int:
    have = dictionary_keys()
    missing = {}
    for name, used in (("android", kotlin_ui_keys()), ("web", web_ui_keys())):
        for k in sorted(used):
            if k.strip() and k not in have:
                missing.setdefault(name, []).append(k)
    if missing:
        for name, ks in missing.items():
            print(f"Missing Turkish translations ({name}):")
            for k in ks:
                print(f"  {k!r}")
        return 1
    print(f"i18n OK: {len(have)} dictionary entries cover all UI strings.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
