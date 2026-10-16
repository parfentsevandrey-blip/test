package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1390 to 1400: a count of the decade's records by year, a clock keeper's wages, the overlapping reigns of four rulers and the ages of three devices. */
val db65Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.PROJECT,
        section = "Records",
        title = "Counting the dated records of this decade by year",
        originalTitle = "Records by year",
        author = "Compilers of this database",
        year = 1399,
        summary = "The dated records of this decade are counted by year, and a short program prints how many fall in each year.",
        details = "The list holds the years of the twenty reading records in this database, one entry each. The output is fixed, so the counts can be checked by hand against the list.",
        instructions = """
            1. Save the example as records_by_year.py.
            2. Run it: python3 records_by_year.py
        """,
        example = """
            from collections import Counter

            years = [1390, 1390, 1390, 1390, 1390, 1390,
                     1391, 1391,
                     1392, 1392, 1392,
                     1393, 1393,
                     1395, 1395,
                     1397, 1397,
                     1398, 1398,
                     1399]
            counts = Counter(years)
            for year in sorted(counts):
                print(f"{year}: {counts[year]}")
            print("total:", len(years))
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.PROJECT,
        section = "Wages",
        title = "A clock keeper's wages for a month, in pence and shillings",
        originalTitle = "Wages ledger",
        author = "The chapter of a cathedral",
        year = 1390,
        summary = "A clock keeper is paid four pence a working day, and a short program totals a month's wages and turns the pence into shillings.",
        details = "The wage and the days are invented for the test, so the example shows the arithmetic of a ledger rather than a real payment. Twelve pence make a shilling, and the program prints the shillings and the pence left over.",
        instructions = """
            1. Save the example as wages.py.
            2. Run it: python3 wages.py
        """,
        example = """
            DAY_WAGE_PENCE = 4
            DAYS = 26
            total = DAY_WAGE_PENCE * DAYS
            print("days worked:", DAYS)
            print("total pence:", total)
            print("shillings:", total // 12)
            print("pence left over:", total % 12)
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.PROJECT,
        section = "Reigns",
        title = "Overlapping reigns of four rulers of the decade, in calendar years",
        originalTitle = "Overlapping reigns",
        author = "The four rulers named in the example",
        year = 1398,
        summary = "The reigns of four rulers who held power in the 1390s are compared pair by pair, and a short program prints how many calendar years each pair reigned together.",
        details = "The dates are the usual dates of each reign: Hongwu from 1368 to 1398, Charles VI from 1380 to 1422, Yoshimitsu as shogun from 1368 to 1394 and Taejo of Joseon from 1392 to 1398. The overlap of a pair is the earlier of the two ends minus the later of the two starts, in calendar years, so it is not an exact count of days.",
        instructions = """
            1. Save the example as overlaps.py.
            2. Run it: python3 overlaps.py
        """,
        example = """
            from itertools import combinations

            reigns = [
                ("Hongwu", 1368, 1398),
                ("Charles VI", 1380, 1422),
                ("Yoshimitsu", 1368, 1394),
                ("Taejo", 1392, 1398),
            ]
            best = None
            for (name_a, start_a, end_a), (name_b, start_b, end_b) in combinations(reigns, 2):
                overlap = min(end_a, end_b) - max(start_a, start_b)
                if overlap > 0:
                    print(name_a, "and", name_b + ":", overlap)
                    if best is None or overlap > best[2]:
                        best = (name_a, name_b, overlap)
            print("longest overlap:", best[0], "and", best[1], best[2])
        """,
    ),

    entry(
        databaseId = 65,
        kind = Kind.PROJECT,
        section = "Ages",
        title = "The decade's clocks and cards by age in 1400",
        originalTitle = "Ages in 1400",
        author = "Compilers of this database",
        year = 1400,
        summary = "Three devices of this decade are given their ages in the year 1400, and a short program prints each age from its date.",
        details = "The dates are those given for the Wells clock, the Merton astrolabe and the Gringonneur cards, and the ages are plain subtractions. The Wells date is about 1390 in most accounts, so its age is approximate.",
        instructions = """
            1. Save the example as ages.py.
            2. Run it: python3 ages.py
        """,
        example = """
            AGE_YEAR = 1400
            devices = [("Wells clock", 1390), ("Merton astrolabe", 1390), ("Gringonneur cards", 1392)]
            for name, year in devices:
                print(name, "age", AGE_YEAR - year)
        """,
    ),

)
