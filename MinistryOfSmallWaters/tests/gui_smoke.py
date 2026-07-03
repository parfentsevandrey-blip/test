"""Headless GUI smoke test: drive the real Tk window under Xvfb, exercise
interactions, and grab a screenshot so the render pipeline can be verified."""
import os, sys, time, traceback

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

def main():
    from entities import World
    from aquarium import Aquarium

    import config
    night = "night" in sys.argv
    world = World()
    if night:
        world.day_t = config.DAY_LENGTH * 0.5    # jump to midnight
    app = Aquarium(world)
    app.root.geometry(f"+30+30")            # predictable position for screenshot
    app.root.update()

    # run ~5 seconds of animation, poking & feeding along the way
    frames = 150
    for i in range(frames):
        if i == 20:
            world.feed_nation()             # pellets rain down
        if i == 25:
            f = world.creatures[1]
            world.click(f.x, f.y)           # poke a fish
        if i == 30:
            world.crab.poke(world); world.crab.poke(world)   # flag easter egg
        if i == 60:
            world.feed_nation()
        app.root.update_idletasks()
        app.root.update()
        time.sleep(1/30)

    print("frames rendered:", frames)
    print("live food:", len(world.food), "| bubbles:", len(world.bubbles),
          "| effects:", [e.kind for e in world.effects])
    print("canvas items:", len(app.canvas.find_all()))

    if "quittest" in sys.argv:
        # verify the Ctrl-Q hard-quit binding tears the app down
        app.root.focus_force()
        app.root.event_generate("<Control-q>")
        app.root.update()          # dispatch the key -> quit() enqueues _q
        app._pump()                # drain the command queue -> destroys root
        assert app._alive is False, "Ctrl-Q did not quit the app"
        print("QUIT TEST OK (Ctrl-Q tore down the app)")
        return

    # screenshot via X11 grab
    shot = os.path.join(os.path.dirname(__file__),
                        "tank_shot_night.png" if night else "tank_shot.png")
    try:
        from PIL import ImageGrab
        img = ImageGrab.grab(xdisplay=os.environ.get("DISPLAY"))
        from config import CANVAS_W, CANVAS_H
        # window content sits a little inside the frame; grab a generous region
        crop = img.crop((30, 30, 30 + CANVAS_W + 12, 30 + CANVAS_H + 40))
        crop.save(shot)
        print("screenshot saved:", shot, crop.size)
    except Exception as e:
        print("screenshot failed:", e)

    app.root.destroy()

if __name__ == "__main__":
    try:
        main()
        print("GUI SMOKE OK")
    except Exception:
        traceback.print_exc()
        sys.exit(1)
