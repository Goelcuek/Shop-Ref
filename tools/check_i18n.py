#!/usr/bin/env python3
"""Fails if a UI string used by the Android or web app has no Turkish translation.

Translatable strings are the arguments of `L.tx("...")` (Kotlin UI) and, in web/src/app.js, of `t(...)`
plus the label arguments of the form helpers (num, sel, toggle, ...); enum constructor literals of selected
UI enums and the web TABS / REF_PAGES tables are included too. Dictionary keys are read from
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


# Web helpers whose arguments at these positions are translated with t() inside the helper.
WEB_LABEL_ARGS = {"t": (0,), "num": (2,), "sel": (2,), "toggle": (2, 3), "slider": (2,), "kpi": (0,), "rnum": (1,),
                  "machineEditor": (1,), "f": (1,)}
JS_STR = r"""(['"])((?:(?!\1)[^\\]|\\.)*)\1"""


def js_call_args(src: str, start: int) -> list:
    """Top-level argument texts of the call whose '(' is at src[start]."""
    args, depth, i, cur = [], 0, start, ""
    while i < len(src):
        c = src[i]
        if c in "'\"`":
            j = i + 1
            while j < len(src) and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            cur += src[i:j + 1]
            i = j + 1
            continue
        if c in "([{":
            depth += 1
            if depth == 1:
                i += 1
                continue
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                args.append(cur)
                return args
        elif c == "," and depth == 1:
            args.append(cur)
            cur = ""
            i += 1
            continue
        cur += c
        i += 1
    return args


def js_label_literals(expr: str) -> list:
    """String literals an argument can evaluate to: plain or ternary branches, not comparison operands."""
    out = []
    for m in re.finditer(JS_STR, expr):
        before = expr[:m.start()].rstrip()
        after = expr[m.end():].lstrip()
        if before.endswith(("==", "!=")) or after.startswith(("==", "!=")):
            continue
        out.append(unescape(m.group(2)))
    return out


def web_ui_keys() -> set:
    keys = set()
    for f in glob.glob(str(ROOT / "web/src/*.js")) + glob.glob(str(ROOT / "web/src/*.html")):
        src = Path(f).read_text(encoding="utf-8")
        for name, positions in WEB_LABEL_ARGS.items():
            for m in re.finditer(r'(?<![\w.$])' + name + r'\(', src):
                args = js_call_args(src, m.end() - 1)
                for pos in positions:
                    if pos < len(args) and "`" not in args[pos]:
                        keys.update(js_label_literals(args[pos]))
        for table in ("TABS", "REF_PAGES"):
            m = re.search(r'const ' + table + r' = \[(.*?)\];', src, re.S)
            if m:
                for row in re.finditer(r'\[([^\[\]]*)\]', m.group(1)):
                    keys.update(unescape(x[1]) for x in re.findall(JS_STR, row.group(1))[1:])
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
