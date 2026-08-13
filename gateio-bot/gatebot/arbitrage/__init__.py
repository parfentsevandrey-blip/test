"""Треугольный арбитраж на споте Gate.io."""

from .executor import ExecutionResult, LegResult, TriangleExecutor
from .runner import ArbitrageRunner
from .scanner import DEFAULT_MIDDLE, ScanResult, TriangleScanner
from .triangle import (
    Leg,
    Opportunity,
    Triangle,
    best_size,
    check_minimums,
    evaluate,
    find_triangles,
)

__all__ = [
    "ArbitrageRunner",
    "ExecutionResult",
    "LegResult",
    "TriangleExecutor",
    "TriangleScanner",
    "ScanResult",
    "DEFAULT_MIDDLE",
    "Leg",
    "Opportunity",
    "Triangle",
    "best_size",
    "check_minimums",
    "evaluate",
    "find_triangles",
]
