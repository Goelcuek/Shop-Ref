#!/usr/bin/env python3
"""Bundle the web app into one self-contained HTML file.

Inlines web/src/styles.css, the Kotlin/JS engine and web/src/app.js into web/src/index.html.
Build the engine first:  ./gradlew -p core compileProductionExecutableKotlinJs
Usage:  python3 web/build.py [--engine PATH] [--out PATH]
"""
import argparse
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "web" / "src"
ENGINE = ROOT / "core/build/compileSync/js/main/productionExecutable/kotlin/shopref.js"
OUT = ROOT / "web" / "dist" / "index.html"


def script_safe(js: str) -> str:
    """Make JavaScript safe to embed in an inline <script> element."""
    js = re.sub(r"^//# sourceMappingURL=.*$", "", js, flags=re.M)
    return js.replace("</script", "<\\/script").replace("<!--", "<\\!--")


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--engine", type=pathlib.Path, default=ENGINE)
    ap.add_argument("--out", type=pathlib.Path, default=OUT)
    args = ap.parse_args()

    if not args.engine.exists():
        raise SystemExit(f"Engine not found: {args.engine}\nRun: ./gradlew -p core compileProductionExecutableKotlinJs")

    page = (SRC / "index.html").read_text(encoding="utf-8")
    parts = {
        "/*STYLE*/": (SRC / "styles.css").read_text(encoding="utf-8").replace("</style", "<\\/style"),
        "/*ENGINE*/": script_safe(args.engine.read_text(encoding="utf-8")),
        "/*APP*/": script_safe((SRC / "app.js").read_text(encoding="utf-8")),
    }
    for marker, content in parts.items():
        if page.count(marker) != 1:
            raise SystemExit(f"Template must contain {marker} exactly once")
        page = page.replace(marker, content)

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(page, encoding="utf-8")
    print(f"Wrote {args.out.relative_to(ROOT) if args.out.is_relative_to(ROOT) else args.out} ({len(page.encode()) / 1024:.0f} KB)")


if __name__ == "__main__":
    main()
