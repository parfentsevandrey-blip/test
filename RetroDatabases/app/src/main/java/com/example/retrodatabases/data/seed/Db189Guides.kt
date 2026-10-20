package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 150 to 160: how an epicycle carries a planet round its deferent, with a program that steps the planet through four quarter turns. */
val db189Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.GUIDE,
        section = "Planets",
        title = "How an epicycle carries a planet round its deferent in Ptolemy's model",
        originalTitle = "Epicycle and deferent",
        author = "Ancient astronomers, set out by Claudius Ptolemy",
        year = 150,
        summary = "A planet on an epicycle moves on a small circle whose centre rides round a larger circle, and a short program steps the planet through four quarter turns to show its path.",
        details = "The epicycle was in use before Ptolemy, and the Almagest about 150 sets out the planetary models with deferents and epicycles. The radii and turns in the example are illustrative, chosen as whole numbers and quarter turns so that each position can be checked by hand, and they are not Ptolemy's values.",
        instructions = """
            1. Save the example as epicycle.py.
            2. Run it: python3 epicycle.py
        """,
        example = """
            DEFERENT = 12   # radius of the deferent, illustrative
            EPICYCLE = 3    # radius of the epicycle, illustrative

            def turn(x, y, quarters):
                # one quarter turn counterclockwise takes (x, y) to (-y, x)
                for _ in range(quarters % 4):
                    x, y = -y, x
                return x, y

            for step in range(4):
                cx, cy = turn(DEFERENT, 0, step)    # centre of the epicycle on the deferent
                ex, ey = turn(EPICYCLE, 0, -step)   # planet on the epicycle, turning the other way
                print("step", step, "planet at", cx + ex, cy + ey)
        """,
        rare = true,
    ),

)
