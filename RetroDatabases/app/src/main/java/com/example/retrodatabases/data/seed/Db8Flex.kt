package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex projects of 1970 to 1980: showpieces that squeeze the most out of a few lines of code, from bit tricks to dithering and a probability puzzle. */
val db8Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 8,
        kind = Kind.FLEX,
        section = "Bits",
        title = "Gosper's hack",
        originalTitle = "The HAKMEM memo of 1972: the next number with the same bit count",
        author = "Bill Gosper",
        year = 1972,
        summary = "The next larger integer with the same number of one bits, found with a few bit operations, so every combination of bits can be listed in order.",
        details = "Gosper's hack appeared in the MIT memo HAKMEM in 1972. The example prints the first five four-bit numbers with two ones, in increasing order.",
        instructions = """
            1. Save the example as gosper.py.
            2. Run it: python3 gosper.py
        """,
        example = """
            def next_same_popcount(x):
                c = x & -x
                r = x + c
                return (((r ^ x) >> 2) // c) | r

            x = 0b0011
            for _ in range(5):
                print(format(x, "04b"))
                x = next_same_popcount(x)
        """,
        rare = true,
    ),

    entry(
        databaseId = 8,
        kind = Kind.FLEX,
        section = "Graphics",
        title = "Floyd–Steinberg dithering",
        originalTitle = "An adaptive algorithm for spatial grey scale (Floyd and Steinberg, 1976)",
        author = "Robert Floyd and Louis Steinberg",
        year = 1976,
        summary = "Dithering that turns a grey image into black and white dots, and pushes each pixel's rounding error onto its unprocessed neighbours.",
        details = "The method was published in 1976. Its error spreading keeps the average brightness of each area close to the original, so a grey band looks grey from a distance. The example dithers four bands of grey and prints them as text.",
        instructions = """
            1. Save the example as dither.py.
            2. Run it: python3 dither.py
        """,
        example = """
            # Four bands of four rows each, from dark grey to white.
            WIDTH, HEIGHT = 32, 16
            image = [[[0.25, 0.5, 0.75, 1.0][y // 4] for _ in range(WIDTH)] for y in range(HEIGHT)]
            for y in range(HEIGHT):
                for x in range(WIDTH):
                    old = image[y][x]
                    new = 1.0 if old >= 0.5 else 0.0
                    error = old - new
                    image[y][x] = new
                    if x + 1 < WIDTH:
                        image[y][x + 1] += error * 7 / 16
                    if y + 1 < HEIGHT:
                        if x > 0:
                            image[y + 1][x - 1] += error * 3 / 16
                        image[y + 1][x] += error * 5 / 16
                        if x + 1 < WIDTH:
                            image[y + 1][x + 1] += error * 1 / 16
            for row in image:
                print("".join("." if v == 1.0 else "#" for v in row))
        """,
    ),

    entry(
        databaseId = 8,
        kind = Kind.FLEX,
        section = "Probability",
        title = "Monty Hall by simulation",
        originalTitle = "The Monty Hall problem (Selvin, 1975)",
        author = "Steve Selvin, with a simulation written for this database",
        year = 1975,
        summary = "A host who opens a losing door makes switching win two times in three, and a simulation of 100,000 games shows it.",
        details = "Steve Selvin posed the puzzle in a letter to The American Statistician in 1975, and it became famous in 1990, when Marilyn vos Savant answered it in her Parade column. The example runs the game with a fixed seed and prints the win rate of staying and of switching.",
        instructions = """
            1. Save the example as monty.py.
            2. Run it: python3 monty.py
        """,
        example = """
            import random

            random.seed(1975)
            trials = 100000
            stay_wins = switch_wins = 0
            for _ in range(trials):
                car = random.randrange(3)
                pick = random.randrange(3)
                # The host opens a door that is neither the pick nor the car.
                opened = next(d for d in range(3) if d != pick and d != car)
                switched = next(d for d in range(3) if d != pick and d != opened)
                stay_wins += pick == car
                switch_wins += switched == car
            print(f"stay: {stay_wins / trials:.3f}")
            print(f"switch: {switch_wins / trials:.3f}")
        """,
    ),
)
