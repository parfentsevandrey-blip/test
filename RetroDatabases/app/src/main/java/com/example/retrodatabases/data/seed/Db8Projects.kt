package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1970 to 1980: small programs in the spirit of the decade, with instructions and code to run. */
val db8Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.PROJECT,
        section = "Games",
        title = "Mastermind scoring",
        originalTitle = "Mastermind, the code-breaking game (1970)",
        author = "Written as a teaching example; the game was devised in 1970 by Mordecai Meirowitz",
        year = 1970,
        summary = "The scoring rule of Mastermind: a black peg means a right colour in the right place, a white peg a right colour in the wrong place, and the guesser reads only these pegs.",
        details = "Each colour in the code and in the guess is counted once, so repeated colours score correctly. The example plays three guesses against a fixed secret and prints the feedback for each.",
        instructions = """
            1. Save the example as mastermind.py.
            2. Run it: python3 mastermind.py
            3. Change the secret or the guesses to see other feedback.
        """,
        example = """
            COLOURS = "RGBY"   # red, green, blue, yellow

            def feedback(secret, guess):
                black = sum(s == g for s, g in zip(secret, guess))
                # White pegs: right colours in wrong places, counted once for each colour.
                common = sum(min(secret.count(c), guess.count(c)) for c in COLOURS)
                return black, common - black

            secret = "RGBY"
            for guess in ["RRGG", "RGBY", "BBYY"]:
                black, white = feedback(secret, guess)
                print(guess, "black", black, "white", white)
        """,
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.PROJECT,
        section = "Games",
        title = "A text adventure in the Zork style",
        originalTitle = "Zork, the text adventure (1977)",
        author = "Written as a teaching example, in the spirit of Zork",
        year = 1977,
        summary = "A small text adventure engine with rooms, exits and items, driven by a list of typed commands, in the spirit of Zork.",
        details = "Zork grew out of Dungeon, a game that MIT students wrote on a PDP-10 in the late 1970s. This engine keeps the same shape: a room describes itself and the things in it, and commands move the player or change the inventory.",
        instructions = """
            1. Save the example as adventure.py.
            2. Run it: python3 adventure.py
            3. Add rooms to the dictionary, or change the script, to play a different game.
        """,
        example = """
            rooms = {
                "hall": {"text": "A long hall.", "exits": {"north": "kitchen"}, "items": ["lamp"]},
                "kitchen": {"text": "A dusty kitchen.", "exits": {"south": "hall"}, "items": ["key"]},
            }
            here = "hall"
            inventory = []

            def look():
                room = rooms[here]
                items = ", ".join(room["items"]) or "nothing"
                print(f"{here}: {room['text']} You see: {items}.")

            def take(item):
                room = rooms[here]
                if item in room["items"]:
                    room["items"].remove(item)
                    inventory.append(item)
                    print(f"Taken: {item}.")
                else:
                    print(f"There is no {item} here.")

            def go(direction):
                global here
                exits = rooms[here]["exits"]
                if direction in exits:
                    here = exits[direction]
                    look()
                else:
                    print("You cannot go that way.")

            script = ["look", "take lamp", "take sword", "go north", "take key",
                      "go west", "go south", "inventory"]
            for command in script:
                print("> " + command)
                verb, _, noun = command.partition(" ")
                if verb == "look":
                    look()
                elif verb == "take":
                    take(noun)
                elif verb == "go":
                    go(noun)
                elif verb == "inventory":
                    print("You carry: " + (", ".join(inventory) or "nothing") + ".")
        """,
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.PROJECT,
        section = "Spreadsheets",
        title = "A VisiCalc-style spreadsheet",
        originalTitle = "VisiCalc (1979)",
        author = "Written as a teaching example, in the spirit of VisiCalc",
        year = 1979,
        summary = "A tiny spreadsheet: cells hold numbers or formulas, and a formula is evaluated from the cells it names, so a change in one cell flows through the rest.",
        details = "VisiCalc showed the idea to business users in 1979. This version supports sums and products of cell references and numbers, and it evaluates formulas on demand, so the table always shows the latest value of each cell.",
        instructions = """
            1. Save the example as sheet.py.
            2. Run it: python3 sheet.py
            3. Add cells or change formulas in the dictionary to build a different table.
        """,
        example = """
            import re

            sheet = {
                "A1": 10,
                "A2": 20,
                "A3": "=A1+A2",
                "B1": "=A3*2",
                "B2": "=B1+A1",
            }

            def evaluate(expression):
                # Sums of products of integers: only + and * are understood.
                total = 0
                for term in expression.split("+"):
                    product = 1
                    for factor in term.split("*"):
                        product *= int(factor)
                    total += product
                return total

            def value(cell, seen=()):
                raw = sheet.get(cell, 0)
                if not isinstance(raw, str):
                    return raw
                if cell in seen:
                    raise ValueError("circular reference at " + cell)
                expression = raw[1:]
                for ref in set(re.findall(r"[A-Z][0-9]+", expression)):
                    expression = expression.replace(ref, str(value(ref, seen + (cell,))))
                return evaluate(expression)

            def show(cells):
                for cell in cells:
                    print(f"{cell:>3} {value(cell)}")

            show(["A1", "A2", "A3", "B1", "B2"])
            print("set A1 to 15")
            sheet["A1"] = 15
            show(["A1", "A3", "B1", "B2"])
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.PROJECT,
        section = "Games",
        title = "A dungeon generator in the Rogue style",
        originalTitle = "Rogue (1980)",
        author = "Written as a teaching example, in the spirit of Rogue",
        year = 1980,
        summary = "A dungeon generator in the spirit of Rogue: rooms are placed at random and joined by corridors, and a seed makes the same map appear every time.",
        details = "Rogue was written by Michael Toy and Glenn Wichman in 1980, and it generated a new dungeon for each game. The example makes one map of 40 by 12 characters, with the player placed in the first room.",
        instructions = """
            1. Save the example as dungeon.py.
            2. Run it: python3 dungeon.py
            3. Change the seed to see another dungeon, or the number of rooms to make it bigger.
        """,
        example = """
            import random

            random.seed(1980)
            WIDTH, HEIGHT = 40, 12
            grid = [["#"] * WIDTH for _ in range(HEIGHT)]
            rooms = []
            for _ in range(4):
                w, h = random.randint(4, 8), random.randint(3, 4)
                x, y = random.randint(1, WIDTH - w - 1), random.randint(1, HEIGHT - h - 1)
                rooms.append((x, y, w, h))
                for row in range(y, y + h):
                    for col in range(x, x + w):
                        grid[row][col] = "."

            def centre(room):
                x, y, w, h = room
                return x + w // 2, y + h // 2

            for a, b in zip(rooms, rooms[1:]):
                (x1, y1), (x2, y2) = centre(a), centre(b)
                for col in range(min(x1, x2), max(x1, x2) + 1):
                    if grid[y1][col] == "#":
                        grid[y1][col] = "."
                for row in range(min(y1, y2), max(y1, y2) + 1):
                    if grid[row][x2] == "#":
                        grid[row][x2] = "."

            px, py = centre(rooms[0])
            grid[py][px] = "@"
            for line in grid:
                print("".join(line))
        """,
        rare = true,
    ),
)
