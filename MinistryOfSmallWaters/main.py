"""
Ministry of Small Waters — entry point.

Boots the simulation, opens the little pixel tank, and installs the crowned-crab
tray icon. The Tk window owns the main thread; the tray runs alongside it.

Run it with:  pythonw main.py      (or:  python main.py  for a debug console)
"""

from __future__ import annotations

import sys
import threading


def main():
    from entities import World
    from aquarium import Aquarium

    world = World()
    app = Aquarium(world)

    tray = None
    try:
        from tray import Tray
        tray = Tray(app)
        app.on_status = tray.set_title
        threading.Thread(target=tray.run, name="tray", daemon=True).start()
    except Exception as exc:                     # pystray missing / no tray host
        print(f"[Ministry] tray unavailable ({exc}); running windowed only.",
              file=sys.stderr)
        # without a tray, closing the window must actually quit
        app.root.protocol("WM_DELETE_WINDOW", app.quit)

    try:
        app.run()
    finally:
        if tray is not None:
            tray.stop()


if __name__ == "__main__":
    main()
