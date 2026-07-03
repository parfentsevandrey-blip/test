"""
The system-tray presence: a crowned-crab seal that lives by the taskbar clock
and lets you govern the small waters without opening the window.

pystray runs its own event loop, so we start it on a background thread and
marshal every action back onto the Tkinter thread through the Aquarium's
thread-safe helpers.
"""

from __future__ import annotations

import pystray
from pystray import Menu, MenuItem as Item

import config as C
import autostart
from seal import build_seal


class Tray:
    def __init__(self, app):
        self.app = app
        self._topmost = False
        self.icon = pystray.Icon(
            "ministry",
            icon=build_seal(64),
            title=C.APP_NAME,
            menu=self._build_menu(),
        )

    # ------------------------------------------------------------------ #
    # menu
    # ------------------------------------------------------------------ #
    def _build_menu(self):
        return Menu(
            Item("Open the Tank", self._show, default=True),
            Item("Hide the Tank", self._hide),
            Menu.SEPARATOR,
            Item("Feed the Nation  🍤", self._feed),
            Item("Poke the Head of State  👑", self._poke),
            Menu.SEPARATOR,
            Item("Always on Top", self._toggle_top,
                 checked=lambda item: self._topmost),
            Item("Launch at Windows Startup", self._toggle_autostart,
                 checked=lambda item: autostart.is_enabled()),
            Menu.SEPARATOR,
            Item("About the Ministry…", self._about),
            Item("Resign (Quit)", self._quit),
        )

    # ------------------------------------------------------------------ #
    # handlers  (pystray calls these with (icon, item))
    # ------------------------------------------------------------------ #
    def _show(self, icon, item):
        self.app.show()

    def _hide(self, icon, item):
        self.app.hide()

    def _feed(self, icon, item):
        self.app.feed()

    def _poke(self, icon, item):
        self.app.poke_crab()

    def _toggle_top(self, icon, item):
        self._topmost = not self._topmost
        self.app.toggle_topmost(self._topmost)

    def _toggle_autostart(self, icon, item):
        autostart.toggle()

    def _about(self, icon, item):
        self.app.show_about(C.about_text())

    def _quit(self, icon, item):
        try:
            self.icon.stop()
        finally:
            self.app.quit()

    # ------------------------------------------------------------------ #
    # tooltip ticker (called from the Tk thread)
    # ------------------------------------------------------------------ #
    def set_title(self, text):
        try:
            self.icon.title = f"{C.APP_SHORT} — {text}"
        except Exception:
            pass

    # ------------------------------------------------------------------ #
    def run(self):
        self.icon.run()

    def stop(self):
        try:
            self.icon.stop()
        except Exception:
            pass
