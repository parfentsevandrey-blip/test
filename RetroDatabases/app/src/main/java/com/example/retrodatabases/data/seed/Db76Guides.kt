package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1280 to 1286: how an alfil leaps, how second differences fill a table, how a tables piece moves by two dice, how Zhiyuan years become Western years and how the Hebrew letters are sorted. */
val db76Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.GUIDE,
        section = "Chess",
        title = "How an alfil leaps in medieval chess",
        originalTitle = "Alfil",
        author = "Players of the medieval chess",
        year = 1283,
        summary = "In the chess of the period the alfil leaps exactly two squares diagonally, and a short program lists its leaps from two squares and totals them over a board of sixty-four squares.",
        details = "The alfil jumps over the square between its start and its target, so it stays on squares of one colour. The piece belongs to the chess of Alfonso X's time, and the example lists the leaps from two squares and totals them for an empty board.",
        instructions = """
            1. Save the example as alfil.py.
            2. Run it: python3 alfil.py
        """,
        example = """
            FILES = "abcdefgh"

            def leaps(file, rank):
                # the alfil moves two squares diagonally, jumping over the square between
                targets = []
                for df in (-2, 2):
                    for dr in (-2, 2):
                        f, r = file + df, rank + dr
                        if 0 <= f < 8 and 0 <= r < 8:
                            targets.append(FILES[f] + str(r + 1))
                return sorted(targets)

            for square in ["a1", "d4"]:
                file = FILES.index(square[0])
                rank = int(square[1]) - 1
                print("from", square + ":", " ".join(leaps(file, rank)))

            total = 0
            for file in range(8):
                for rank in range(8):
                    total += len(leaps(file, rank))
            print("leaps on an empty board:", total)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.GUIDE,
        section = "Tables",
        title = "How second differences fill a gap in a table of sun positions",
        originalTitle = "Second differences",
        author = "Astronomers of the Yuan court",
        year = 1280,
        summary = "A table given at equal steps can be filled between its entries by first and second differences, a method that Chinese historians link with the Shoushi tables.",
        details = "The example uses three illustrative values, not Guo's data, and fills the gap at half a step and at a quarter step with a correction from the second difference. The output is printed with five decimal places, so each line can be checked by hand.",
        instructions = """
            1. Save the example as second_differences.py.
            2. Run it: python3 second_differences.py
        """,
        example = """
            values = [100, 103, 107]   # illustrative table at equal steps, not Guo's data
            d1 = values[1] - values[0]
            d2 = (values[2] - values[1]) - (values[1] - values[0])

            def fill(p):
                # p is the fraction of a step past the first entry
                return values[0] + p * d1 + p * (p - 1) / 2 * d2

            for p in (0.5, 0.25):
                print(f"at {p} of a step: {fill(p):.5f}")
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.GUIDE,
        section = "Tables",
        title = "How a piece moves round a track of twenty-four points by two dice",
        originalTitle = "Tables game",
        author = "Players of the tables game",
        year = 1283,
        summary = "In the tables game a piece advances by the total of two dice, and a short program moves a piece round a track of twenty-four points for three rolls.",
        details = "The track here is a loop of twenty-four points, the number used on a modern backgammon board, and the example does not reproduce the layout of the period. The rolls are fixed in the program so that each position can be checked by hand.",
        instructions = """
            1. Save the example as tables_track.py.
            2. Run it: python3 tables_track.py
        """,
        example = """
            TRACK = 24            # points on the loop, as on a modern board, illustrative
            rolls = [(3, 5), (4, 6), (2, 1)]
            point = 1
            moved = 0
            for a, b in rolls:
                steps = a + b
                point = (point - 1 + steps) % TRACK + 1
                moved += steps
                print(f"roll {a} and {b} moves {steps} to point {point}")
            print("points moved in all:", moved)
            print("final point:", point)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.GUIDE,
        section = "Reign years",
        title = "How Zhiyuan era years of the Yuan become Western years",
        originalTitle = "Zhiyuan nianhao",
        author = "Chroniclers of the Yuan court",
        year = 1281,
        summary = "The Zhiyuan era of the Yuan ran from 1264 to 1294, so a Zhiyuan year becomes a Western year by adding 1263, and a short program converts several years of the 1280s.",
        details = "Zhiyuan was the reign name that Kublai Khan used for his years in China, and the example converts the years 18, 24 and 25 along with the first and last years of the era. The conversion is the same at each step, so the program needs only one line of arithmetic.",
        instructions = """
            1. Save the example as zhiyuan.py.
            2. Run it: python3 zhiyuan.py
        """,
        example = """
            ERA_FIRST = 1264      # Zhiyuan year 1

            def western(reign_year):
                return ERA_FIRST - 1 + reign_year

            for year in (18, 24, 25, 1, 31):
                print(f"Zhiyuan {year} is {western(year)}")
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.GUIDE,
        section = "Letters",
        title = "How the Hebrew letters are sorted into mothers, doubles and simples",
        originalTitle = "Sefer Yetzirah",
        author = "The Book of Creation, anonymous",
        year = 1286,
        summary = "The Book of Creation sorts the twenty-two Hebrew letters into three mothers, seven doubles and twelve simples, and a short program prints the three groups and checks the total.",
        details = "The grouping comes from the older Book of Creation rather than from the Zohar itself, and it was discussed by the Castilian kabbalists of the 1280s. The example writes the letters in transliteration, so that the output stays in plain ASCII.",
        instructions = """
            1. Save the example as letters.py.
            2. Run it: python3 letters.py
        """,
        example = """
            MOTHERS = ["alef", "mem", "shin"]
            DOUBLES = ["bet", "gimel", "dalet", "kaf", "pe", "resh", "tav"]
            SIMPLES = ["he", "vav", "zayin", "chet", "tet", "yod", "lamed", "nun", "samekh", "ayin", "tzadi", "qof"]
            for name, group in [("mothers", MOTHERS), ("doubles", DOUBLES), ("simples", SIMPLES)]:
                print(name, len(group), " ".join(group))
            total = len(MOTHERS) + len(DOUBLES) + len(SIMPLES)
            print("letters in all:", total)
            print("alphabet check:", total == 22)
        """,
    ),

)
