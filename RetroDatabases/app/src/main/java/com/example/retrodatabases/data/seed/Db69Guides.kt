package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1350 to 1360: Oresme's rule for the distance of an evenly speeding body, the wool sack in stones and pounds, Oresme's harmonic grouping, Buridan's impetus rule and the legs of a route. */
val db69Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.GUIDE,
        section = "Motion",
        title = "How Oresme's graph finds the distance of an evenly speeding body",
        originalTitle = "How Oresme's graph finds the distance of an evenly speeding body",
        author = "Nicole Oresme",
        year = 1350,
        summary = "A body that gains speed evenly covers a distance equal to its mean speed times the time, and a short program checks this for a steady acceleration.",
        details = "A body that starts from rest and gains the same speed each second has a speed that rises in a straight line on Oresme's graph, so its mean speed is half its final speed. The example uses an acceleration of two units, so the speed after t seconds is 2 t, the mean speed is t and the distance is t times t.",
        instructions = """
            1. Save the example as mean_speed.py.
            2. Run it: python3 mean_speed.py
        """,
        example = """
            acceleration = 2
            for t in range(1, 5):
                speed = acceleration * t
                mean = speed // 2
                distance = mean * t
                print("time", t, "speed", speed, "mean speed", mean, "distance", distance)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.GUIDE,
        section = "Trade weights",
        title = "How a sack of wool is weighed in stones and pounds",
        originalTitle = "How a sack of wool is weighed in stones and pounds",
        author = "English wool merchants",
        year = 1353,
        summary = "English wool was reckoned in sacks, stones and pounds, and a short program splits a weight in pounds into whole sacks, stones and the pounds left over.",
        details = "A sack was reckoned at 26 stones and a stone at 14 pounds, so a sack held 364 pounds. The example converts a total of 1000 pounds, which gives 2 sacks, 19 stones and 6 pounds, and it checks that the three parts add back to the total.",
        instructions = """
            1. Save the example as wool_sacks.py.
            2. Run it: python3 wool_sacks.py
        """,
        example = """
            POUNDS_PER_STONE = 14
            STONES_PER_SACK = 26
            total = 1000
            sack_pounds = POUNDS_PER_STONE * STONES_PER_SACK
            sacks = total // sack_pounds
            rest = total - sacks * sack_pounds
            stones = rest // POUNDS_PER_STONE
            pounds = rest - stones * POUNDS_PER_STONE
            print("sacks", sacks)
            print("stones", stones)
            print("pounds", pounds)
            print("check", sacks * sack_pounds + stones * POUNDS_PER_STONE + pounds == total)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.GUIDE,
        section = "Series",
        title = "How Oresme's grouping shows that the harmonic sum grows without limit",
        originalTitle = "How Oresme's grouping shows that the harmonic sum grows without limit",
        author = "Nicole Oresme",
        year = 1350,
        summary = "Oresme grouped the terms of the harmonic series so that each group adds at least a half, and a short program shows the lower bound growing by a half at each doubling.",
        details = "The grouping is set out in Oresme's questions on Euclid's geometry, and its date of about 1350 is approximate. The example sums the first 2, 4, 8 and 16 terms and compares each sum with the bound one plus a half for each doubling, so the growth can be checked by hand.",
        instructions = """
            1. Save the example as oresme_harmonic.py.
            2. Run it: python3 oresme_harmonic.py
        """,
        example = """
            for k in range(1, 5):
                terms = 2 ** k
                total = sum(1 / i for i in range(1, terms + 1))
                bound = 1 + k / 2
                print("terms", terms, "sum", f"{total:.3f}", "bound", f"{bound:.1f}")
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How Buridan's impetus rule compares two thrown stones",
        originalTitle = "How Buridan's impetus rule compares two thrown stones",
        author = "John Buridan",
        year = 1350,
        summary = "Buridan held that the impetus of a thrown body grows with its weight and its speed, and a short program compares the impetus of two stones by multiplying weight by speed.",
        details = "The rule is set out in Buridan's questions on Aristotle's Physics, and the date of about 1350 is approximate. The example uses two illustrative stones, a heavy one of weight 2 moving at speed 3 and a light one of weight 1 moving at speed 2, and prints each impetus and the ratio between them.",
        instructions = """
            1. Save the example as impetus.py.
            2. Run it: python3 impetus.py
        """,
        example = """
            stones = [("heavy stone", 2, 3), ("light stone", 1, 2)]
            for name, weight, speed in stones:
                print(name, "impetus", weight * speed)
            ratio = (stones[0][1] * stones[0][2]) // (stones[1][1] * stones[1][2])
            print("ratio", ratio)
        """,
    ),

    entry(
        databaseId = 69,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How the legs of a route add up to a journey on a route map",
        originalTitle = "How the legs of a route add up to a journey on a route map",
        author = "Unknown map-makers",
        year = 1360,
        summary = "A route map of about 1360 marks the distance of each road leg, and a short program adds the legs in order to give the running total and the length of the whole journey.",
        details = "The leg lengths in the example are illustrative and are not taken from any map, so the example shows only the method of adding legs in order. The running total is printed after each leg, so the total can be checked by hand.",
        instructions = """
            1. Save the example as route_legs.py.
            2. Run it: python3 route_legs.py
        """,
        example = """
            legs = [12, 8, 15, 7]
            total = 0
            for number, leg in enumerate(legs, start=1):
                total += leg
                print("leg", number, "length", leg, "running total", total)
            print("journey", total)
        """,
    ),

)
