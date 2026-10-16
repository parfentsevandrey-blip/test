package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1390 to 1400: day counts, the limb of an astrolabe, the stick grades of the Ming code and the years between dated works. */
val db65Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Calendar",
        title = "Days in the decade of the 1390s by the Julian rule",
        originalTitle = "Julian leap years",
        author = "The Julian calendar",
        year = 1390,
        summary = "Under the Julian rule every fourth year is a leap year, and a short program counts the days of the ten years from 1390 to 1399.",
        details = "The rule is applied to the years directly, without a date library, so the count is 3652 days with leap years in 1392 and 1396.",
        instructions = """
            1. Save the example as days_julian.py.
            2. Run it: python3 days_julian.py
        """,
        example = """
            years = range(1390, 1400)
            days = sum(366 if year % 4 == 0 else 365 for year in years)
            print("years:", len(years))
            print("days in the decade by the Julian rule:", days)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Minutes on the limb of a 1390 astrolabe",
        originalTitle = "Limb of the astrolabe",
        author = "Makers of astrolabes",
        year = 1390,
        summary = "A full circle of 360 degrees, each degree split into 60 minutes, makes 21600 minutes, and a quadrant makes 5400.",
        details = "This is the count used to mark the limb of an astrolabe, such as the 1390 instrument at Merton College, though the example does not measure that instrument. The program prints the full circle and the quadrant so both can be checked by hand.",
        instructions = """
            1. Save the example as limb.py.
            2. Run it: python3 limb.py
        """,
        example = """
            DEGREES = 360
            MINUTES_PER_DEGREE = 60
            circle = DEGREES * MINUTES_PER_DEGREE
            quadrant = circle // 4
            print("minutes on a full circle:", circle)
            print("minutes on a quadrant:", quadrant)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Law",
        title = "Strokes in the light and heavy stick grades of the Ming code",
        originalTitle = "Light and heavy sticks",
        author = "The Hongwu court",
        year = 1397,
        summary = "The light stick grades of the Ming code run from ten to fifty strokes and the heavy stick grades from sixty to a hundred, and a short program totals the strokes of each.",
        details = "The ranges are the ones given for the Ming code, and each set is taken as five grades in steps of ten. The totals are 150 for the light sticks and 400 for the heavy sticks.",
        rare = true,
        instructions = """
            1. Save the example as sticks.py.
            2. Run it: python3 sticks.py
        """,
        example = """
            light = [10, 20, 30, 40, 50]
            heavy = [60, 70, 80, 90, 100]
            print("light stick grades:", len(light), "strokes:", sum(light))
            print("heavy stick grades:", len(heavy), "strokes:", sum(heavy))
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Reign",
        title = "Richard II's reign counted in calendar years",
        originalTitle = "Years of a reign",
        author = "Richard II of England",
        year = 1399,
        summary = "Richard II came to the English throne in 1377 and was deposed in 1399, and a short program counts the calendar years between the two.",
        details = "The count uses calendar years only, so it gives 22 years and not the exact length of the reign in days. The date 1377 is the year of accession and 1399 the year of the deposition.",
        instructions = """
            1. Save the example as reign.py.
            2. Run it: python3 reign.py
        """,
        example = """
            accession = 1377
            deposition = 1399
            print("years from accession to deposition:", deposition - accession)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Years from Gower's Confessio to the Kalmar Union",
        originalTitle = "Years between two dated works",
        author = "John Gower and the Kalmar delegates",
        year = 1397,
        summary = "Gower's first Confessio of about 1390 and the Kalmar Union of 1397 are seven years apart, and a short program counts the gap.",
        details = "The count uses the usual date of each event, and the 1390 date is approximate, so the example says only that the gap is seven years by these dates.",
        instructions = """
            1. Save the example as confessio_kalmar.py.
            2. Run it: python3 confessio_kalmar.py
        """,
        example = """
            gower = 1390
            kalmar = 1397
            print("years from the Confessio to the Kalmar Union:", kalmar - gower)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.EXAMPLE,
        section = "Dates",
        title = "Years from the later Wycliffite Bible to the Ming code",
        originalTitle = "Years between two texts",
        author = "Wycliffite translators and the Hongwu court",
        year = 1397,
        summary = "The later Wycliffite Bible of about 1395 and the Ming code of 1397 are two years apart, and a short program counts the gap.",
        details = "Both dates are the usual dates given for the works, so the gap is a count of years rather than of days. The example is a plain subtraction, and its output can be checked at a glance.",
        instructions = """
            1. Save the example as wycliffe_ming.py.
            2. Run it: python3 wycliffe_ming.py
        """,
        example = """
            wycliffe = 1395
            ming = 1397
            print("years from the Wycliffite Bible to the Ming code:", ming - wycliffe)
        """,
    ),

)
