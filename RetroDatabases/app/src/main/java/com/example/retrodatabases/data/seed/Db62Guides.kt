package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1420 to 1430: base sixty digits as Al-Kashi wrote them, the radii of a dome's rings, Greek letters used as numbers, handshakes among envoys and the indiction number of a year. */
val db62Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.GUIDE,
        section = "Sexagesimal fractions",
        title = "How base sixty digits make a fraction",
        originalTitle = "Base sixty places",
        author = "Jamshid al-Kashi",
        year = 1424,
        summary = "A sexagesimal number such as 6;16,59 is a sum of its digits divided by powers of sixty, and a short program turns those digits into an exact fraction.",
        details = "Al-Kashi wrote the digits of 2 pi in base sixty in his treatise of 1424, and the example uses the first three of them. The program keeps exact fractions, so the output is the same on every run.",
        instructions = """
            1. Save the example as sexagesimal.py.
            2. Run it: python3 sexagesimal.py
        """,
        example = """
            from fractions import Fraction

            digits = [6, 16, 59]   # the integer part, then the first two sexagesimal places
            value = sum(Fraction(d, 60 ** i) for i, d in enumerate(digits))
            print(value)
            print(round(float(value), 4))
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.GUIDE,
        section = "Architecture",
        title = "How a dome's ring radius shrinks with height",
        originalTitle = "Cupola of Florence",
        author = "Builders of the dome of Florence",
        year = 1420,
        summary = "A ring of a hemispherical dome at height h has radius equal to the square root of the radius squared minus h squared, which a short program finds with exact whole numbers.",
        details = "The dome of Florence Cathedral was begun in 1420, and its rings narrow as they rise. The example uses a hemisphere of radius 5 and three heights that give whole-number radii, so the output is exact and the figures are illustrative.",
        instructions = """
            1. Save the example as dome_rings.py.
            2. Run it: python3 dome_rings.py
        """,
        example = """
            from math import isqrt

            R = 5   # radius of the hemisphere, in illustrative units
            for height in [0, 3, 4, 5]:
                # The ring at this height has radius sqrt(R * R - height * height), exact here.
                radius = isqrt(R * R - height * height)
                print(f"height {height}: ring radius {radius}")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.GUIDE,
        section = "Numerals",
        title = "How Greek letters stand for numbers",
        originalTitle = "Greek numerals",
        author = "Greek scribes and their Latin copyists",
        year = 1420,
        summary = "Greek letters stood for numbers, so the letters of a word could be added to give a number, and a short program sums the values of three words.",
        details = "Greek manuscripts used their letters as numbers for page marks, dates and sums. The year 1420 is a convention for the decade, and the example uses a short list of illustrative words.",
        instructions = """
            1. Save the example as greek_numerals.py.
            2. Run it: python3 greek_numerals.py
        """,
        example = """
            values = {"alpha": 1, "beta": 2, "gamma": 3, "delta": 4, "epsilon": 5,
                      "zeta": 7, "eta": 8, "theta": 9, "iota": 10, "kappa": 20}
            for word in [("beta", "iota", "kappa"), ("alpha", "delta"), ("zeta", "eta", "theta")]:
                print(" ".join(word) + ":", sum(values[name] for name in word))
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.GUIDE,
        section = "Diplomacy",
        title = "How handshakes among envoys are counted",
        originalTitle = "Handshakes among envoys",
        author = "Envoys of the courts",
        year = 1420,
        summary = "Each pair of envoys shakes hands once, so n envoys make n times n minus one over two handshakes, and a short program prints the count for three group sizes.",
        details = "A count of this kind helps plan the greetings of an embassy, and the figures here are illustrative. The year 1420 is a convention for the decade and is not the date of any recorded embassy.",
        instructions = """
            1. Save the example as handshakes.py.
            2. Run it: python3 handshakes.py
        """,
        example = """
            for n in [3, 5, 7]:
                # Every pair of envoys shakes hands once, so the count is n times n minus one over two.
                print(f"{n} envoys shake hands {n * (n - 1) // 2} times")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How the indiction number of a year is found",
        originalTitle = "Indictio",
        author = "Roman tax officials and medieval notaries",
        year = 1420,
        summary = "A year's indiction number is its place in a fifteen-year cycle, found by adding two to the year, taking the remainder on division by fifteen and adding one.",
        details = "The cycle began as a Roman tax rhythm, and medieval notaries and chanceries used it to date documents, with the start of the year varying from place to place. This guide uses the January start, so 1420 is indiction 13, 1422 is indiction 15 and 1430 is indiction 8; the record uses 1420, the first year of the example.",
        instructions = """
            1. Save the example as indiction.py.
            2. Run it: python3 indiction.py
        """,
        example = """
            def indiction(year):
                # A fifteen-year cycle: shift the year by two, take the remainder on division by fifteen, add one.
                return (year + 2) % 15 + 1

            for year in [1420, 1422, 1427, 1430]:
                print(f"{year}: indiction {indiction(year)}")
        """,
    ),

)
