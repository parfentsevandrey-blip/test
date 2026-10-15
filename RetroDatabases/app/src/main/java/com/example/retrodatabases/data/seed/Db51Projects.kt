package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1530 to 1540: a French-Latin word index, a calendar wheel, a carat-mixing calculator and the planetary hours of a Sunday. */
val db51Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "A French-Latin word index in alphabetical order",
        originalTitle = "Dictionaire francoislatin, autrement dict les mots francois, avec les latins qui y correspondent",
        author = "Robert Estienne",
        year = 1539,
        summary = "A word index sorts the French headwords of a dictionary alphabetically without regard to case, and a short program prints such an index from seven French-Latin pairs.",
        details = "Estienne's dictionary of 1539 paired French words with their Latin equivalents, and an index of this kind lets a reader find a headword quickly. The pairs in the example are everyday words chosen for the program, not entries taken from the book.",
        instructions = """
            1. Save the example as french_index.py.
            2. Run it: python3 french_index.py
        """,
        example = """
            PAIRS = [("pain", "panis"), ("eau", "aqua"), ("roi", "rex"), ("cheval", "equus"),
                     ("livre", "liber"), ("arbre", "arbor"), ("Dieu", "Deus")]

            # An index sorts the French headwords alphabetically, ignoring the case of the letters.
            for french, latin in sorted(PAIRS, key=lambda pair: pair[0].lower()):
                print(f"{french}: {latin}")
            print(f"entries: {len(PAIRS)}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "A paper wheel for the days of the year",
        originalTitle = "Instrument Buch",
        author = "Peter Apian",
        year = 1533,
        summary = "A calendar wheel turns a day of the year into a month and a day, and a short program does the same job for four days by subtracting each month's length in turn.",
        details = "Apian's Instrument Buch of 1533 is known for paper wheels that readers turned to answer questions of time and the sky, and wheels of this kind were used to read dates. The program does the wheel's job with a table of month lengths for a year of 365 days and does not reproduce Apian's drawing.",
        instructions = """
            1. Save the example as calendar_wheel.py.
            2. Run it: python3 calendar_wheel.py
        """,
        example = """
            MONTHS = [("January", 31), ("February", 28), ("March", 31), ("April", 30), ("May", 31), ("June", 30),
                      ("July", 31), ("August", 31), ("September", 30), ("October", 31), ("November", 30), ("December", 31)]

            def month_and_day(day_of_year):
                # Walk the months in order, subtracting each month's length, as a paper wheel turns past its window.
                for name, length in MONTHS:
                    if day_of_year <= length:
                        return name, day_of_year
                    day_of_year -= length
                raise ValueError("day outside the year")

            for day in (1, 59, 60, 365):
                name, day_of_month = month_and_day(day)
                print(f"day {day} of the year is {name} {day_of_month}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.PROJECT,
        section = "Metals",
        title = "Mixing two gold alloys to a target carat count",
        originalTitle = "Pirotechnia",
        author = "Vannoccio Biringuccio",
        year = 1540,
        summary = "Mixing gold of two carat counts to reach a third count needs weights in the ratio of the carat differences, and a short program gives that ratio for three mixtures.",
        details = "Goldsmiths and mint officials of the period mixed alloys of different fineness to reach a standard, and Biringuccio's book of 1540 covers the metals and their assay. The mixtures in the example are invented for the calculation, and the program prints the weight ratio of the two alloys.",
        instructions = """
            1. Save the example as alloy_mix.py.
            2. Run it: python3 alloy_mix.py
        """,
        example = """
            from fractions import Fraction as F

            def weight_ratio(a, b, target):
                # Weights x of a carats and y of b carats reach the target when x times (a - target) equals y times (target - b).
                return F(target - b, a - target)

            for a, b, target in ((24, 9, 12), (18, 12, 15), (18, 9, 12)):
                print(f"{a} carats mixed with {b} carats to reach {target}: weight ratio {weight_ratio(a, b, target)}")
        """,
    ),

    entry(
        databaseId = 51,
        kind = Kind.PROJECT,
        section = "Astrology",
        title = "The planetary hours of a Sunday",
        originalTitle = "Horae planetarum",
        author = "Astrologers of the period",
        year = 1536,
        summary = "The planetary hours give each hour of the day to one of the seven planets in a fixed order, and a short program counts how many of the 24 hours of a Sunday each planet rules.",
        details = "Astrologers of the period gave the hours of the day to the planets in a fixed order that began with the planet of the day, and the example follows that order. The program prints the count for each planet over one Sunday, which gives 4 hours each for the Sun, Venus and Mercury and 3 each for the other four planets.",
        instructions = """
            1. Save the example as planetary_hours.py.
            2. Run it: python3 planetary_hours.py
        """,
        example = """
            ORDER = ["Saturn", "Jupiter", "Mars", "Sun", "Venus", "Mercury", "Moon"]   # the planets in the order of the hours

            def ruler_of_hour(day_index, hour):
                # The first hour of a day belongs to the planet of that day, and each later hour goes on to the next planet in the order.
                return ORDER[(day_index + hour) % 7]

            SUNDAY = ORDER.index("Sun")   # the first hour of Sunday is ruled by the Sun
            counts = {planet: 0 for planet in ORDER}
            for hour in range(24):
                counts[ruler_of_hour(SUNDAY, hour)] += 1
            for planet in ORDER:
                print(f"{planet} {counts[planet]}")
            print(f"total {sum(counts.values())}")
        """,
    ),

)
