package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1420 to 1430: the days from the Treaty of Troyes to the death of Henry V, the days from the relief of Orleans to the crowning at Reims, and the decimal size of nine sexagesimal places. */
val db62Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.FLEX,
        section = "Treaties",
        title = "Days from the Treaty of Troyes to Henry V's death",
        originalTitle = "Traite de Troyes",
        author = "England and France",
        year = 1420,
        summary = "The Treaty of Troyes of 21 May 1420 made Henry V heir to the French crown, and he died on 31 August 1422, so a short program counts the days between the two dates.",
        details = "The treaty is dated 21 May 1420 and Henry V died at Vincennes on 31 August 1422, as commonly given. The example counts the days with Python's calendar, so the count is a modern calendar count of fixed dates.",
        instructions = """
            1. Save the example as troyes_days.py.
            2. Run it: python3 troyes_days.py
        """,
        example = """
            from datetime import date

            troyes = date(1420, 5, 21)   # the treaty is dated 21 May 1420
            death = date(1422, 8, 31)    # Henry V died on 31 August 1422
            print(f"days from the treaty to the death of Henry V: {(death - troyes).days}")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.FLEX,
        section = "Campaigns",
        title = "Days from the relief of Orleans to the crowning at Reims",
        originalTitle = "Siege of Orleans",
        author = "Joan of Arc and the French army",
        year = 1429,
        summary = "The siege of Orleans was lifted on 8 May 1429 and Charles VII was crowned at Reims on 17 July 1429, and a short program counts the days between the two dates.",
        details = "Both dates are given in most accounts of Joan of Arc's campaign. The example counts the days with Python's calendar, so the count is a modern calendar count of the two dates.",
        instructions = """
            1. Save the example as orleans_days.py.
            2. Run it: python3 orleans_days.py
        """,
        example = """
            from datetime import date

            relief = date(1429, 5, 8)      # the siege of Orleans was lifted on 8 May 1429
            crowning = date(1429, 7, 17)   # Charles VII was crowned at Reims on 17 July 1429
            print(f"days from the relief of Orleans to the crowning at Reims: {(crowning - relief).days}")
        """,
    ),

    entry(
        databaseId = 62,
        kind = Kind.FLEX,
        section = "Sexagesimal fractions",
        title = "Sixty to the ninth power in decimal digits",
        originalTitle = "Sexagesimal places",
        author = "Jamshid al-Kashi",
        year = 1424,
        summary = "Nine places of base sixty make a number of seventeen decimal digits, and a short program prints sixty to the ninth power and counts its digits.",
        details = "Al-Kashi's value of 2 pi was given in nine sexagesimal places, and the example shows how large the base sixty denominator is. The number is exact, so the output never changes.",
        instructions = """
            1. Save the example as sixty_power.py.
            2. Run it: python3 sixty_power.py
        """,
        example = """
            value = 60 ** 9
            print(value)
            print(len(str(value)))
        """,
    ),

)
