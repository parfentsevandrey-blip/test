"""Command-line entry point.

    python -m mav --input listings.json
    python -m mav --input listings.csv --config config.yaml --format csv --out deals.csv
"""

from __future__ import annotations

import argparse
import sys
from datetime import date, datetime
from pathlib import Path

from .config import load_config
from .pipeline import rank_offers
from .providers.file_import import FileImportProvider
from .report import to_csv, to_markdown


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(
        prog="mav",
        description="Find underpriced apartment listings in modern Moscow residential complexes.",
    )
    p.add_argument("--input", required=True, help="path to a .json or .csv file of exported listings")
    p.add_argument("--config", default=None, help="path to a YAML config file (see config.example.yaml)")
    p.add_argument("--format", choices=["markdown", "csv"], default="markdown")
    p.add_argument("--out", default=None, help="write the report to this file instead of stdout")
    p.add_argument("--top-n", type=int, default=None, help="override output.top_n from the config")
    p.add_argument(
        "--as-of",
        default=None,
        help="YYYY-MM-DD date to treat as 'today' for freshness scoring (defaults to the real today)",
    )
    return p


def main(argv=None) -> int:
    args = build_parser().parse_args(argv)

    cfg = load_config(args.config)
    if args.top_n is not None:
        cfg.output.top_n = args.top_n

    as_of = datetime.strptime(args.as_of, "%Y-%m-%d").date() if args.as_of else date.today()

    offers = FileImportProvider(args.input).fetch()
    ranked = rank_offers(offers, cfg, as_of=as_of)

    report = to_markdown(ranked, cfg, as_of=as_of) if args.format == "markdown" else to_csv(ranked, as_of=as_of)

    if args.out:
        Path(args.out).write_text(report, encoding="utf-8")
        print(f"Wrote {len(ranked)} listing(s) to {args.out}", file=sys.stderr)
    else:
        print(report)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
