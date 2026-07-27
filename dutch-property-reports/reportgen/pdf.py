"""Конвертация .docx → .pdf через LibreOffice в headless-режиме."""

from __future__ import annotations

import logging
import shutil
import subprocess
from pathlib import Path

log = logging.getLogger(__name__)


def soffice_binary() -> str | None:
    for name in ("soffice", "libreoffice"):
        found = shutil.which(name)
        if found:
            return found
    return None


def convert(docx_path: Path, out_dir: Path | None = None, timeout: int = 600) -> Path:
    """Конвертирует DOCX в PDF рядом с исходником. Требует установленный LibreOffice."""
    binary = soffice_binary()
    if binary is None:
        raise RuntimeError(
            "LibreOffice не найден — установите libreoffice, либо конвертируйте "
            "DOCX в PDF вручную (Word: «Сохранить как PDF»)."
        )
    out_dir = out_dir or docx_path.parent
    out_dir.mkdir(parents=True, exist_ok=True)
    cmd = [
        binary,
        "--headless",
        "--norestore",
        "--convert-to",
        "pdf",
        "--outdir",
        str(out_dir),
        str(docx_path),
    ]
    log.info("конвертация в PDF: %s", docx_path.name)
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    pdf_path = out_dir / (docx_path.stem + ".pdf")
    if result.returncode != 0 or not pdf_path.exists():
        raise RuntimeError(
            f"LibreOffice не смог конвертировать файл:\n{result.stdout}\n{result.stderr}"
        )
    log.info("сохранён PDF: %s", pdf_path)
    return pdf_path
