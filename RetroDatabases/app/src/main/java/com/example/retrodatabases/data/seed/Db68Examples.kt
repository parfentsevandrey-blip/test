package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1362 to 1369: the popes whose reigns overlapped the 1360s, a kiln firing count and a papal fee paid in three parts. */
val db68Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.EXAMPLE,
        section = "Popes",
        title = "How many popes reigned during the 1360s, counted by their years",
        originalTitle = "Pontificates counted by years",
        author = "The papal chancery at Avignon",
        year = 1362,
        summary = "Four popes of the period are listed with their reign years, and a short program counts the two whose reigns overlapped the 1360s.",
        details = "The reign years are the years of election and death commonly given: Innocent VI from 1352 to 1362 and Urban V from 1362 to 1370, while Clement VI and Gregory XI do not reach the decade. The program compares whole years only, so a reign that starts or ends within the decade counts as overlapping.",
        instructions = """
            1. Save the example as popes.py.
            2. Run it: python3 popes.py
        """,
        example = """
            # Reign years as commonly given, in whole years; a reign overlaps the decade if it starts by 1369 and ends in 1360 or later.
            POPES = [
                ("Clement VI", 1342, 1352),
                ("Innocent VI", 1352, 1362),
                ("Urban V", 1362, 1370),
                ("Gregory XI", 1370, 1378),
            ]
            FIRST, LAST = 1360, 1369
            for name, start, end in POPES:
                overlaps = start <= LAST and end >= FIRST
                print(f"{name} {start} to {end}: {'yes' if overlaps else 'no'}")
            names = [name for name, start, end in POPES if start <= LAST and end >= FIRST]
            print("popes in the decade:", len(names))
            print("names:", ", ".join(names))
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.EXAMPLE,
        section = "Kilns",
        title = "How many firings a kiln makes in a year on a fixed cycle",
        originalTitle = "Firing cycle",
        author = "Potters at Jingdezhen",
        year = 1369,
        summary = "A kiln fired once every ten days makes a fixed number of firings in a year, and a short program counts the firings and the days left over.",
        details = "The cycle of ten days is illustrative and is not a record of any Jingdezhen kiln. The example checks the count by multiplying back, so the leftover days are shown to add up to the year.",
        instructions = """
            1. Save the example as firings.py.
            2. Run it: python3 firings.py
        """,
        example = """
            # Illustrative cycle: one firing every ten days, with the leftover days counted.
            DAYS_IN_YEAR = 365
            CYCLE_DAYS = 10
            firings = DAYS_IN_YEAR // CYCLE_DAYS
            left_over = DAYS_IN_YEAR % CYCLE_DAYS
            print("firings in a year:", firings)
            print("days left over:", left_over)
            print("check:", firings * CYCLE_DAYS + left_over)
        """,
    ),

    entry(
        databaseId = 68,
        kind = Kind.EXAMPLE,
        section = "Fees",
        title = "A papal fee of 1362 paid in three yearly parts",
        originalTitle = "Installment payments",
        author = "The papal chancery at Avignon",
        year = 1362,
        summary = "A fee of 100 florins paid in three yearly parts gives two equal parts and a last part with the remainder, and a short program prints each payment and the total.",
        details = "The sum and the number of parts are illustrative and are not the terms of any recorded fee. Splitting a sum with a remainder into the last part is a common way of handling fees paid over several years.",
        instructions = """
            1. Save the example as installments.py.
            2. Run it: python3 installments.py
        """,
        example = """
            # Illustrative fee of 100 florins paid in three parts; the remainder is added to the last part.
            FEE = 100
            PARTS = 3
            share = FEE // PARTS
            last = FEE - share * (PARTS - 1)
            payments = [share] * (PARTS - 1) + [last]
            for year, amount in enumerate(payments, start=1):
                print(f"payment {year}: {amount} florins")
            print("total:", sum(payments))
        """,
    ),

)
