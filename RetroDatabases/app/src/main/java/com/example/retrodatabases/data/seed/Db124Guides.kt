package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 800 to 810: the wolf, goat and cabbage crossing from Alcuin's problem book, solved in seven crossings and checked by a program. */
val db124Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.GUIDE,
        section = "Puzzles",
        title = "How the wolf, goat and cabbage crossing is solved in seven crossings, checked by a program",
        originalTitle = "De lupo et capra et fasciculo cauli",
        author = "Alcuin of York (attributed)",
        year = 800,
        summary = "The wolf, goat and cabbage crossing, Problem 18 of the Propositiones, is solved in seven crossings, and a short program checks that no bank is left unsafe at any step.",
        details = "The puzzle asks a man to carry a wolf, a goat and a bundle of cabbage across a river in a boat that holds him and one item, so that the wolf is never left alone with the goat and the goat never with the cabbage. The plan in the example takes the goat over first and brings it back once, and after every crossing the program checks the bank the man has just left.",
        instructions = """
            1. Save the example as wolf_goat.py.
            2. Run it: python3 wolf_goat.py
        """,
        example = """
            def unsafe(bank):
                return ("wolf" in bank and "goat" in bank) or ("goat" in bank and "cabbage" in bank)

            PLAN = [
                ("goat", "right"),
                (None, "left"),
                ("wolf", "right"),
                ("goat", "left"),
                ("cabbage", "right"),
                (None, "left"),
                ("goat", "right"),
            ]

            banks = {"left": {"wolf", "goat", "cabbage"}, "right": set()}
            man = "left"
            every_step_safe = True
            for cargo, destination in PLAN:
                if cargo is not None:
                    banks[man].remove(cargo)
                    banks[destination].add(cargo)
                man = destination
                left_behind = "left" if man == "right" else "right"
                if unsafe(banks[left_behind]):
                    every_step_safe = False
            print("crossings:", len(PLAN))
            print("every bank safe:", every_step_safe)
            print("all across:", banks["right"] == {"wolf", "goat", "cabbage"})
        """,
    ),

)
