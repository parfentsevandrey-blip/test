package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1281 to 1283: Silla years across the missing year zero, the Samguk yusa's gap to its printing, Kamakura years, the Shoushi year in seconds, knight jumps on a whole board and the drift of the Julian year. */
val db76Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Years",
        title = "Years of Silla from its founding in 57 BC to its fall in 935",
        originalTitle = "Silla",
        author = "Chroniclers of Silla and Korea",
        year = 1281,
        summary = "Silla is dated from 57 BC to its fall in 935, and a short program counts the years between the two dates both as elapsed time and with both end years counted.",
        details = "There is no year zero, so the count from 57 BC to AD 935 needs care: the elapsed span from the start of 57 BC to the start of AD 935 is 991 years, and counting both end years gives 992. The founding in 57 BC is the traditional date, and the fall of Silla in 935 is the date usually given.",
        instructions = """
            1. Save the example as silla.py.
            2. Run it: python3 silla.py
        """,
        example = """
            # astronomical numbering: 1 BC is year 0, so 57 BC is year -56
            FOUNDED = -56     # 57 BC, the traditional founding of Silla
            FALLEN = 935      # AD 935, the fall of Silla
            elapsed = FALLEN - FOUNDED
            inclusive = elapsed + 1
            print("years elapsed from 57 BC to AD 935:", elapsed)
            print("years counted inclusively:", inclusive)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Years",
        title = "Years from the writing of the Samguk yusa to its oldest printing",
        originalTitle = "Samguk yusa",
        author = "Readers and printers of the Samguk yusa",
        year = 1281,
        summary = "The Samguk yusa was begun about 1281, and a short program counts the years from that date to its oldest surviving printing, once with 1512 and once with 1513.",
        details = "The starting year is the lower end of the composition range, and the printing date is given as 1512 in one account and 1513 in another. The program prints both counts so that the difference between the accounts is visible.",
        instructions = """
            1. Save the example as samguk_gap.py.
            2. Run it: python3 samguk_gap.py
        """,
        example = """
            WRITTEN = 1281     # lower end of the range given for the composition
            PRINTED_A = 1512   # oldest surviving printing, by one account
            PRINTED_B = 1513   # oldest surviving printing, by another account
            print("years to 1512:", PRINTED_A - WRITTEN)
            print("years to 1513:", PRINTED_B - WRITTEN)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Years",
        title = "Years of the Kamakura shogunate, from 1185 or 1192 to 1333",
        originalTitle = "Kamakura bakufu",
        author = "The Kamakura shogunate",
        year = 1283,
        summary = "The Kamakura shogunate, the warrior government under which the Shasekishu was written, is counted here from 1185 or 1192 to its fall in 1333.",
        details = "Accounts differ on the start, since some date the government from 1185 and others from Minamoto no Yoritomo's title of shogun in 1192, so the program counts both. The fall of the Kamakura government in 1333 is the date usually given.",
        instructions = """
            1. Save the example as kamakura.py.
            2. Run it: python3 kamakura.py
        """,
        example = """
            START_A = 1185   # start of the Kamakura government, by one account
            START_B = 1192   # the shogun title, by another account
            FALL = 1333      # fall of the Kamakura government
            print("years from 1185 to 1333:", FALL - START_A)
            print("years from 1192 to 1333:", FALL - START_B)
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Time",
        title = "The Shoushi year of 365.2425 days in seconds",
        originalTitle = "Shoushi year",
        author = "Guo Shoujing and the Yuan astronomers",
        year = 1281,
        summary = "A Shoushi year of 365.2425 days is 31556952 seconds, and a short program converts it into days, hours, minutes and seconds.",
        details = "The year length is the value used by the Shoushi calendar, and the same value was adopted by the Gregorian calendar of 1582. The program uses exact fractions, so the output has no rounding error.",
        instructions = """
            1. Save the example as shoushi_seconds.py.
            2. Run it: python3 shoushi_seconds.py
        """,
        example = """
            from fractions import Fraction

            YEAR_DAYS = Fraction(3652425, 10000)   # 365.2425 days, the Shoushi year
            SECONDS_PER_DAY = 86400
            print("seconds in one year:", YEAR_DAYS * SECONDS_PER_DAY)
            whole = int(YEAR_DAYS)                              # whole days
            rest = int((YEAR_DAYS - whole) * SECONDS_PER_DAY)   # seconds left over
            hours, rest = divmod(rest, 3600)
            minutes, seconds = divmod(rest, 60)
            print(f"{whole} days {hours} hours {minutes} minutes {seconds} seconds")
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Chess",
        title = "Knight jumps counted over a whole chessboard of the medieval game",
        originalTitle = "Caballo",
        author = "Players of the medieval chess",
        year = 1283,
        summary = "The knight jumps in an L-shape, and a short program counts its jumps from two squares and totals them over all sixty-four squares of a board.",
        details = "A knight on an empty board reaches at most eight squares, and the total over the whole board is 336 jumps. The example uses the standard L-shaped jump of the knight, not a reconstruction of any period board.",
        instructions = """
            1. Save the example as knight.py.
            2. Run it: python3 knight.py
        """,
        example = """
            MOVES = [(1, 2), (2, 1), (2, -1), (1, -2), (-1, -2), (-2, -1), (-2, 1), (-1, 2)]

            def jumps(file, rank):
                count = 0
                for df, dr in MOVES:
                    if 0 <= file + df < 8 and 0 <= rank + dr < 8:
                        count += 1
                return count

            print("jumps from a1:", jumps(0, 0))
            print("jumps from d4:", jumps(3, 3))
            print("jumps over the whole board:", sum(jumps(f, r) for f in range(8) for r in range(8)))
        """,
    ),

    entry(
        databaseId = 76,
        kind = Kind.EXAMPLE,
        section = "Drift",
        title = "The Julian year against the Shoushi year over four centuries",
        originalTitle = "Julian year",
        author = "Sosigenes and Guo Shoujing",
        year = 1281,
        summary = "The Julian year of 365.25 days runs ahead of the Shoushi year of 365.2425 days by three days in four centuries, and a short program works out that drift.",
        details = "The drift is 0.0075 days a year, which comes to three days in 400 years, the same gap that the Gregorian reform of 1582 removed by dropping three leap days in every 400 years. The program uses exact fractions, so the output has no rounding error.",
        instructions = """
            1. Save the example as drift.py.
            2. Run it: python3 drift.py
        """,
        example = """
            from fractions import Fraction

            JULIAN = Fraction(36525, 100)        # 365.25 days
            SHOUSHI = Fraction(3652425, 10000)   # 365.2425 days
            per_year = JULIAN - SHOUSHI
            print("drift per year in days:", float(per_year))
            print("drift over 400 years in days:", float(per_year * 400))
        """,
    ),

)
