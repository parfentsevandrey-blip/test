package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1330 to 1336: temporal hours, lunar mansions, a ruled page, indulgence days, and the five lines of a waka. */
val db71Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 71,
        kind = Kind.GUIDE,
        section = "Hours",
        title = "How the length of a temporal hour follows the daylight",
        originalTitle = "Temporal hours",
        author = "Medieval clerics and astronomers",
        year = 1330,
        summary = "A temporal hour is one twelfth of the daylight, so its length grows in summer and shrinks in winter, and a short program gives the length for three seasons.",
        details = "Medieval reckoners divided the daylight and the night into twelve parts each, so the hour changed with the season. The example uses three daylight lengths, midwinter, the equinox and midsummer, and prints the length of one temporal hour for each.",
        instructions = """
            1. Save the example as temporal_hours.py.
            2. Run it: python3 temporal_hours.py
        """,
        example = """
            DAYLIGHT_MINUTES = {"midwinter": 9 * 60, "equinox": 12 * 60, "midsummer": 15 * 60}
            for season, daylight in DAYLIGHT_MINUTES.items():
                hour = daylight // 12
                print(season, daylight // 60, "h of light: one temporal hour is", hour, "minutes")
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.GUIDE,
        section = "Stars",
        title = "How the lunar mansion of each night is counted",
        originalTitle = "Lunar mansions",
        author = "Arabic and Latin astrologers",
        year = 1330,
        summary = "The moon was reckoned to lodge in one of twenty-eight lunar mansions each night, and a short program counts the mansion for seven nights from a chosen start.",
        details = "The list of twenty-eight mansions repeats in a cycle, so the count returns to its first mansion after the twenty-eighth night. The example starts at the twenty-fifth mansion and prints the mansion for each of seven nights, so the wrap-around can be checked by hand.",
        instructions = """
            1. Save the example as lunar_mansions.py.
            2. Run it: python3 lunar_mansions.py
        """,
        example = """
            MANSIONS = 28
            START = 25   # the mansion of the first night, illustrative
            for night in range(1, 8):
                mansion = (START - 1 + night - 1) % MANSIONS + 1
                print("night", night, "mansion", mansion)
            print("mansions in a cycle:", MANSIONS)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How a scribe rules a page into equal lines",
        originalTitle = "Ruling a page",
        author = "Scribes of manuscript books",
        year = 1335,
        summary = "A scribe rules a page by dividing the height between the margins into equal spaces, and a short program finds the space between the rules of a 280 millimetre page.",
        details = "The ruled height is the page height less the two margins, and the rules sit at equal steps from the top margin to the bottom one. The example uses a page of 280 millimetres with margins of 30 millimetres and 23 rules, and prints the ruled height, the space between rules and the position of the last rule.",
        instructions = """
            1. Save the example as ruling.py.
            2. Run it: python3 ruling.py
        """,
        example = """
            from fractions import Fraction

            PAGE_MM = 280
            MARGIN_MM = 30
            RULES = 23
            ruled = PAGE_MM - 2 * MARGIN_MM
            spacing = Fraction(ruled, RULES - 1)
            print("ruled height:", ruled, "mm")
            print("space between rules:", spacing, "mm")
            print("bottom rule at", MARGIN_MM + (RULES - 1) * spacing, "mm")
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.GUIDE,
        section = "Indulgences",
        title = "How the days of pardon from an indulgence are added up",
        originalTitle = "Days of pardon",
        author = "Churches that granted indulgences for pilgrimage and good works",
        year = 1336,
        summary = "An indulgence granted a set number of days of pardon for each act, and a short program adds the days from several acts and turns the total into years and days.",
        details = "Forty days of pardon for one act was a common measure in the period, and the grants were added together by the day. The example uses illustrative numbers of acts and prints the total days of pardon and the same total as whole years and days, so the arithmetic can be checked by hand.",
        instructions = """
            1. Save the example as pardon_days.py.
            2. Run it: python3 pardon_days.py
        """,
        example = """
            DAYS_PER_YEAR = 365
            ACTS = [(2, 40), (3, 40), (5, 40)]   # (number of acts, days of pardon for each act), illustrative
            total = sum(count * days for count, days in ACTS)
            print("days of pardon:", total)
            print("whole years:", total // DAYS_PER_YEAR, "and days:", total % DAYS_PER_YEAR)
        """,
    ),

    entry(
        databaseId = 71,
        kind = Kind.GUIDE,
        section = "Poetry",
        title = "How a waka of thirty-one sounds is set in five lines",
        originalTitle = "Waka",
        author = "Poets of the Japanese court tradition",
        year = 1330,
        summary = "A waka is set in five lines of five, seven, five, seven and seven sounds, and a short program adds the lines to check that they make thirty-one.",
        details = "The waka was the standard short poem of the Japanese court tradition, and Kenko's essays were written in the same period. The example prints each line with its number of sounds and then the total, so the count can be checked by hand.",
        instructions = """
            1. Save the example as waka.py.
            2. Run it: python3 waka.py
        """,
        example = """
            PATTERN = [5, 7, 5, 7, 7]
            for number, sounds in enumerate(PATTERN, start=1):
                print("line", number, "has", sounds, "sounds")
            total = sum(PATTERN)
            print("total sounds:", total)
            print("matches thirty-one:", total == 31)
        """,
    ),

)
