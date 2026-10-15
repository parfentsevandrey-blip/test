package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1500 to 1510: Julian weekdays, Venetian money, perfect-time note values, a twelve-sheet grid, a monochord and the rhymes of a Petrarchan sonnet. */
val db54Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "Weekdays of New Year's Day from 1500 to 1510",
        originalTitle = "Julian calendar",
        author = "Julius Caesar",
        year = 1500,
        summary = "The Julian calendar was still in use in 1500, and a short program works out the weekday of 1 January for each year from 1500 to 1510.",
        details = "The Julian calendar counted every fourth year as a leap year, and the weekday of each New Year's Day follows from that count. The example uses Zeller's rule for the Julian calendar, and its output is fixed, so it never changes.",
        instructions = """
            1. Save the example as julian_weekday.py.
            2. Run it: python3 julian_weekday.py
        """,
        example = """
            DAYS = ["Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]

            def julian_weekday(year, month, day):
                # Zeller's rule for the Julian calendar; January and February count as months 13 and 14 of the year before.
                if month < 3:
                    month += 12
                    year -= 1
                k = year % 100
                j = year // 100
                h = (day + (13 * (month + 1)) // 5 + k + k // 4 + 5 + 6 * j) % 7
                return DAYS[h]

            for year in range(1500, 1511):
                print(f"{year}: {julian_weekday(year, 1, 1)}")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Venetian money of account in denari",
        originalTitle = "Lire, soldi and denari",
        author = "Venetian accountants",
        year = 1500,
        summary = "Venetian accounts counted money in lire, soldi and denari, and a short program turns three sums into denari so that they can be compared.",
        details = "One lira was twenty soldi and one soldo was twelve denari, so a sum has to be multiplied out to denari before it is compared. The three sums are illustrative and not taken from a ledger, so the output is fixed.",
        instructions = """
            1. Save the example as venetian_money.py.
            2. Run it: python3 venetian_money.py
        """,
        example = """
            # One lira is twenty soldi and one soldo is twelve denari, so one lira is 240 denari.
            def to_denari(lire, soldi, denari):
                return lire * 240 + soldi * 12 + denari

            sums = [(25, 13, 7), (4, 0, 9), (100, 19, 11)]
            for lire, soldi, denari in sums:
                print(f"{lire} lire {soldi} soldi {denari} denari = {to_denari(lire, soldi, denari)} denari")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "Note values in a perfect-time bar of Petrucci's print",
        originalTitle = "Harmonice musices Odhecaton A",
        author = "Ottaviano Petrucci",
        year = 1501,
        summary = "In perfect time and perfect prolation a breve holds three semibreves and a semibreve holds three minims, so a short program adds the minims in three bars.",
        details = "Petrucci printed the Odhecaton at Venice in 1501 with mensural notation, in which the value of each note depends on the time and the prolation. The three bars are invented for the purpose, and the example adds their note values in minims.",
        instructions = """
            1. Save the example as note_values.py.
            2. Run it: python3 note_values.py
        """,
        example = """
            # In perfect time a breve holds three semibreves; in perfect prolation a semibreve holds three minims.
            minims = {"breve": 9, "semibreve": 3, "minim": 1}

            bars = [
                ["breve", "semibreve", "semibreve"],
                ["semibreve", "semibreve", "semibreve"],
                ["breve", "minim", "minim", "minim"],
            ]
            for number, notes in enumerate(bars, start=1):
                print(f"bar {number}: {sum(minims[note] for note in notes)} minims")
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Maps",
        title = "Twelve sheets of Waldseemuller's world map on a grid",
        originalTitle = "Universalis Cosmographia",
        author = "Martin Waldseemuller",
        year = 1507,
        summary = "The 1507 world map was printed from twelve woodblock sheets, and a short program prints an illustrative layout of the sheets in three rows of four.",
        details = "The map was printed at Saint-Die in 1507 on twelve sheets that were joined into one large wall map. The grid is illustrative and does not claim the sheets' real arrangement, so the output only checks the counting.",
        instructions = """
            1. Save the example as sheet_grid.py.
            2. Run it: python3 sheet_grid.py
        """,
        example = """
            # An illustrative layout of the twelve sheets in three rows of four, not the printed arrangement.
            ROWS, COLUMNS = 3, 4
            for row in range(ROWS):
                print(" ".join(f"{row * COLUMNS + col + 1:2d}" for col in range(COLUMNS)))
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Music",
        title = "String lengths of a monochord for the musical intervals",
        originalTitle = "Monochord",
        author = "Renaissance music theorists",
        year = 1500,
        summary = "A monochord string of 72 units sounds the octave, fifth, fourth and whole tone at lengths of 36, 48, 54 and 64 units, and a short program prints these lengths.",
        details = "The frequency ratios 2 to 1, 3 to 2, 4 to 3 and 9 to 8 were the intervals of the Pythagorean tradition, which Renaissance theorists explained with the monochord. The example uses a string of 72 units, so each length comes out as a whole number.",
        instructions = """
            1. Save the example as monochord.py.
            2. Run it: python3 monochord.py
        """,
        example = """
            # A string of 72 units; each interval shortens the string by its frequency ratio.
            STRING = 72
            intervals = [("octave", 2, 1), ("fifth", 3, 2), ("fourth", 4, 3), ("whole tone", 9, 8)]
            for name, top, bottom in intervals:
                print(name, STRING * bottom // top)
        """,
    ),

    entry(
        databaseId = 54,
        kind = Kind.EXAMPLE,
        section = "Verse",
        title = "Counting the rhymes of a Petrarchan sonnet",
        originalTitle = "Canzoniere",
        author = "Francesco Petrarca",
        year = 1501,
        summary = "A Petrarchan sonnet has fourteen lines built on a few rhymes, and a short program counts the lines and the rhymes of the usual scheme.",
        details = "The sonnet of two quatrains and two tercets was the model that Bembo and the Italian poets of his time followed in their verse. The example counts the rhymes of the scheme ABBA ABBA CDE CDE, and the output is fixed.",
        instructions = """
            1. Save the example as sonnet_rhymes.py.
            2. Run it: python3 sonnet_rhymes.py
        """,
        example = """
            scheme = "ABBAABBACDECDE"
            counts = {letter: scheme.count(letter) for letter in sorted(set(scheme))}
            print("lines:", len(scheme))
            print("rhymes:", len(counts))
            for letter, count in counts.items():
                print(letter, count)
        """,
    ),

)
