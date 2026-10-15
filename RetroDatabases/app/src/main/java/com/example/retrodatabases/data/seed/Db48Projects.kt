package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1560 to 1570: energy for a water lift, Venetian years, interest on a loan and a sorted list of the batch's records. */
val db48Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.PROJECT,
        section = "Hydraulics",
        title = "Energy for a water lift of one hundred metres",
        originalTitle = "Artificio de Juanelo",
        author = "Juanelo Turriano",
        year = 1565,
        summary = "Lifting water needs energy equal to density times gravity times height times volume, and a short program works this out for three flows.",
        details = "Juanelo Turriano's machine raised water about a hundred metres at Toledo, so the lift height here follows that account. The constants are illustrative round numbers, and the example prints the energy for three flows per hour.",
        instructions = """
            1. Save the example as water_lift_energy.py.
            2. Run it: python3 water_lift_energy.py
        """,
        example = """
            # Illustrative constants: water density 1000 kg per cubic metre, gravity 10 m per second squared, lift 100 m.
            DENSITY = 1000
            GRAVITY = 10
            LIFT = 100

            def energy_kj(cubic_metres):
                # Energy in kilojoules: density times gravity times height times volume, divided by 1000.
                return DENSITY * GRAVITY * LIFT * cubic_metres // 1000

            for flow in (2, 5, 12):
                print(f"flow {flow} m3 per hour: {energy_kj(flow)} kJ per hour")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "Venetian years for dates in 1563 and 1564",
        originalTitle = "Venetian reckoning of the year",
        author = "Venetian officials",
        year = 1564,
        summary = "In Venice the year changed on 1 March, so January and February dates belonged to the year before, and a short program applies that rule.",
        details = "The rule was used in Venetian documents of the period, and the example applies it to four dates of 1563 and 1564. The program needs only the month and the year of each date.",
        instructions = """
            1. Save the example as venetian_year.py.
            2. Run it: python3 venetian_year.py
        """,
        example = """
            from datetime import date

            def venetian_year(day):
                # Venice began the year on 1 March, so January and February belong to the year before.
                return day.year if day.month >= 3 else day.year - 1

            for day in (date(1564, 2, 18), date(1564, 3, 1), date(1563, 12, 25), date(1564, 12, 31)):
                print(f"{day.isoformat()}: Venetian year {venetian_year(day)}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.PROJECT,
        section = "Finance",
        title = "Interest on a loan of a thousand florins",
        originalTitle = "Florins lent at interest",
        author = "Merchant bankers of the period",
        year = 1568,
        summary = "Simple and compound interest on a loan of a thousand florins at six per cent are worked out with exact fractions over five years.",
        details = "Merchant bankers of the period lent money at interest, and the six per cent rate is illustrative rather than one recorded for the Fugger house. The example prints the simple interest for five years and the balance at compound interest, rounded to two decimals.",
        instructions = """
            1. Save the example as loan_interest.py.
            2. Run it: python3 loan_interest.py
        """,
        example = """
            from fractions import Fraction as F

            PRINCIPAL = 1000
            RATE = F(6, 100)
            YEARS = 5

            simple = PRINCIPAL * RATE * YEARS
            balance = F(PRINCIPAL)
            for _ in range(YEARS):
                balance *= 1 + RATE
            print("simple interest over 5 years:", simple)
            print("balance after 5 years at compound interest:", f"{float(balance):.2f}")
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.PROJECT,
        section = "Lists",
        title = "Sorting the decade's records by kind and year",
        originalTitle = "Records of the 1560s",
        author = "Compilers of the batch",
        year = 1564,
        summary = "A list of records sorts by kind and then by year, and a short program prints five records of the decade in that order.",
        details = "Sorting by a pair of keys is a basic task for any catalogue of records, and Python's sorted function compares the pairs in turn. The example uses five records of this decade, and its output lists them by kind, then year, then name.",
        instructions = """
            1. Save the example as sort_records.py.
            2. Run it: python3 sort_records.py
        """,
        example = """
            records = [
                ("LANGUAGE", 1563, "Porta"),
                ("DEVICE", 1564, "Moscow press"),
                ("SYSTEM", 1563, "Escorial"),
                ("TOOL", 1561, "Lopez"),
                ("LANGUAGE", 1564, "Dee"),
            ]

            for kind, year, name in sorted(records):
                print(kind, year, name)
        """,
    ),

)
