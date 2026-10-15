package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1510 to 1517: the books of the Hebrew Bible, Roman numerals for the decade, gematria, the Julian drift, Hebrew years and the Armenian letters. */
val db53Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Scripture",
        title = "Psalm 119, an acrostic of twenty-two stanzas of eight verses",
        originalTitle = "Psalm 119",
        author = "The psalmist, as printed in the Hebrew Bibles of the period",
        year = 1516,
        summary = "Psalm 119 has twenty-two stanzas of eight verses, one stanza for each letter of the Hebrew alphabet, and a short program totals its verses.",
        details = "Each stanza begins with the same letter, in order from alef to tav, which is why the psalm is called an acrostic. The example multiplies the stanzas by the verses in each stanza and prints the total of 176 verses.",
        instructions = """
            1. Save the example as psalm_119.py.
            2. Run it: python3 psalm_119.py
        """,
        example = """
            LETTERS = "alef bet gimel dalet he vav zayin het tet yod kaf lamed mem nun samekh ayin pe tsadi qof resh shin tav".split()
            VERSES_PER_STANZA = 8

            print("stanzas:", len(LETTERS))
            print("verses per stanza:", VERSES_PER_STANZA)
            print("total verses:", len(LETTERS) * VERSES_PER_STANZA)
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Hebrew leap years between 5270 and 5280",
        originalTitle = "Leap years of the Hebrew calendar",
        author = "Hebrew calendar reckoners",
        year = 1516,
        summary = "The Hebrew calendar adds a leap month in seven years of every nineteen, and a short program finds the leap years among the Hebrew years 5270 to 5280.",
        details = "The Hebrew years 5270 to 5280 fall in the same decade as 1510 to 1520 in the Christian count, give or take one year at the autumn new year. The example applies the rule as a remainder test after division by nineteen, and prints the years that pass it.",
        instructions = """
            1. Save the example as hebrew_leap.py.
            2. Run it: python3 hebrew_leap.py
        """,
        example = """
            def is_leap(year):
                # The Hebrew year is a leap year when seven times the year plus one leaves a remainder below seven after division by nineteen.
                return (7 * year + 1) % 19 < 7

            leap = [year for year in range(5270, 5281) if is_leap(year)]
            print("leap years:", " ".join(str(year) for year in leap))
            print("count:", len(leap))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Letters as numbers",
        title = "Gematria, the number values of the Hebrew alphabet",
        originalTitle = "Gematriya",
        author = "Hebrew scholars of the Middle Ages",
        year = 1517,
        summary = "Gematria gives each Hebrew letter a number, and the twenty-two letters of the alphabet add up to 1495 when the standard values are used.",
        details = "The values run from 1 for aleph to 400 for tav, with tens and hundreds for the letters in between. The example lists the letters with their values and prints the count, the total and the sum of the first and last values.",
        instructions = """
            1. Save the example as gematria.py.
            2. Run it: python3 gematria.py
        """,
        example = """
            LETTERS = "alef bet gimel dalet he vav zayin het tet yod kaf lamed mem nun samekh ayin pe tsadi qof resh shin tav".split()
            VALUES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 200, 300, 400]

            total = 0
            for name, value in zip(LETTERS, VALUES):
                total += value
            print("letters:", len(LETTERS))
            print("total value:", total)
            print("alef and tav:", VALUES[0] + VALUES[-1])
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "How far the Julian year drifts in a decade",
        originalTitle = "Julian calendar",
        author = "Julius Caesar's reform",
        year = 1510,
        summary = "The Julian year of 365.25 days runs about 0.0078 days longer than the solar year, and a short program works out the drift over a decade with exact fractions.",
        details = "The drift adds up to one day in about 128 years, which is why calendar reform was discussed for centuries before the Gregorian change of 1582. The example uses exact fractions, so the figures it prints are not rounded before they are shown.",
        instructions = """
            1. Save the example as julian_drift.py.
            2. Run it: python3 julian_drift.py
        """,
        example = """
            from fractions import Fraction

            JULIAN_YEAR = Fraction(36525, 100)     # 365.25 days, exact
            SOLAR_YEAR = Fraction(3652422, 10000)  # 365.2422 days, a standard figure for the solar year

            drift = JULIAN_YEAR - SOLAR_YEAR
            print("drift per year:", f"{float(drift):.4f}", "days")
            print("drift over ten years:", f"{float(10 * drift):.4f}", "days")
            print("years for one day:", int(1 / drift))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Calendars",
        title = "Converting 1516 to a Hebrew year counted from creation",
        originalTitle = "Anno Mundi",
        author = "Hebrew calendar reckoners",
        year = 1516,
        summary = "The Hebrew year counts from creation, and a short program turns two dates of 1516 into their Hebrew years, one before and one after the autumn new year.",
        details = "A date before the autumn new year falls in the Christian year plus 3760, and a date after it falls in that year plus 3761. The example splits the year at the month, which is enough for the two dates chosen here.",
        instructions = """
            1. Save the example as anno_mundi.py.
            2. Run it: python3 anno_mundi.py
        """,
        example = """
            def anno_mundi(year, month):
                # Dates before the autumn new year, roughly January to August, belong to the year plus 3760; later dates to plus 3761.
                return year + 3760 if month <= 8 else year + 3761

            for year, month, label in ((1516, 3, "1 March 1516"), (1516, 11, "1 November 1516")):
                print(label, "is in the Hebrew year", anno_mundi(year, month))
        """,
    ),

    entry(
        databaseId = 53,
        kind = Kind.EXAMPLE,
        section = "Armenian",
        title = "Mesrop's thirty-six letters of the Armenian alphabet",
        originalTitle = "Mesrop's alphabet",
        author = "Mesrop Mashtots",
        year = 1512,
        summary = "Mesrop's Armenian alphabet had thirty-six letters in its first form, and a short program lists them by their transliterated names and counts them.",
        details = "The alphabet was devised around 405, and the Armenian book printed in Venice in 1512 is set in these letters. The example uses transliterated names only, so the list can be read in plain ASCII.",
        instructions = """
            1. Save the example as armenian_letters.py.
            2. Run it: python3 armenian_letters.py
        """,
        example = """
            LETTERS = [
                "ayb", "ben", "gim", "da", "ech", "za", "eh", "et", "to", "zhe",
                "ini", "liwn", "xeh", "ca", "ken", "ho", "ja", "ghad", "cheh", "men",
                "yi", "now", "sha", "vo", "cha", "peh", "jheh", "ra", "seh", "vew",
                "tiwn", "reh", "co", "yiwn", "piwr", "keh",
            ]

            print("letters:", len(LETTERS))
            print("first:", LETTERS[0])
            print("tenth:", LETTERS[9])
        """,
    ),

)
