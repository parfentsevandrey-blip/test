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

        def _run_tray():
            # pystray.Icon construction is inert; the OS tray registration only
            # happens here in run(). If there is no tray host (headless/minimal
            # Linux, a locked-down Windows session), this is where it fails --
            # on the daemon thread, out of reach of the except below. So catch
            # it here and restore a real quit path on the Tk thread.
            try:
                tray.run()
            except Exception as exc:
                print(f"[Ministry] tray backend failed ({exc}); "
                      "closing the window now quits.", file=sys.stderr)
                app._do(lambda: app.root.protocol("WM_DELETE_WINDOW", app.quit))

        threading.Thread(target=_run_tray, name="tray", daemon=True).start()
    except Exception as exc:                     # pystray missing / import error
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
