package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1364: a roll of university fees totalled by faculty. */
val db68Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 68,
        kind = Kind.PROJECT,
        section = "Fees",
        title = "A roll of university fees by faculty and year",
        originalTitle = "Fee roll",
        author = "Clerks of a medieval university",
        year = 1364,
        summary = "A roll of university fees is totalled by faculty, and a short program prints the total for each faculty and the grand total.",
        details = "The fees in the example are illustrative sums in florins for two years and three faculties, not the accounts of Krakow or of any other university. The faculties are sorted by name so the output is in a fixed order.",
        instructions = """
            1. Save the example as fee_roll.py.
            2. Run it: python3 fee_roll.py
        """,
        example = """
            # Illustrative fees in florins for two years and three faculties, not the accounts of any real university.
            FEES = {
                "arts": {1364: 12, 1365: 15},
                "law": {1364: 30, 1365: 24},
                "medicine": {1364: 18, 1365: 20},
            }
            for faculty in sorted(FEES):
                years = FEES[faculty]
                print(faculty, sum(years.values()))
            print("grand total:", sum(sum(years.values()) for years in FEES.values()))
        """,
    ),

)
