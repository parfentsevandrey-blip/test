"""
Optional 'launch the Ministry when Windows starts' support.

Writes a value under HKCU\\...\\Run. Everything is wrapped so that on non-Windows
platforms (or if the registry is unavailable) it degrades to harmless no-ops --
the app still runs, you just don't get autostart.
"""

from __future__ import annotations

import os
import sys

APP_KEY = "MinistryOfSmallWaters"
_RUN_PATH = r"Software\Microsoft\Windows\CurrentVersion\Run"


def _winreg():
    try:
        import winreg
        return winreg
    except Exception:
        return None


def launch_command():
    """The command Windows should run to start us at login."""
    if getattr(sys, "frozen", False):          # packaged .exe
        return f'"{sys.executable}"'
    # running from source: prefer pythonw.exe so no console window flashes
    exe = sys.executable
    base = os.path.dirname(exe)
    pyw = os.path.join(base, "pythonw.exe")
    runner = pyw if os.path.exists(pyw) else exe
    script = os.path.abspath(os.path.join(os.path.dirname(__file__), "main.py"))
    return f'"{runner}" "{script}"'


def is_enabled():
    winreg = _winreg()
    if not winreg:
        return False
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_PATH) as k:
            winreg.QueryValueEx(k, APP_KEY)
        return True
    except Exception:
        return False


def enable():
    winreg = _winreg()
    if not winreg:
        return False
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_PATH, 0,
                            winreg.KEY_SET_VALUE) as k:
            winreg.SetValueEx(k, APP_KEY, 0, winreg.REG_SZ, launch_command())
        return True
    except Exception:
        return False


def disable():
    winreg = _winreg()
    if not winreg:
        return False
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, _RUN_PATH, 0,
                            winreg.KEY_SET_VALUE) as k:
            winreg.DeleteValue(k, APP_KEY)
        return True
    except Exception:
        return False


def toggle():
    if is_enabled():
        disable()
        return False
    enable()
    return True
