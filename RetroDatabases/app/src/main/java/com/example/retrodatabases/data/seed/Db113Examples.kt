package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 910 to 920: the three years given for the Uttiramerur inscriptions, counted in code. */
val db113Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.EXAMPLE,
        section = "Dating",
        title = "The spread of the dates given for the Uttiramerur inscriptions, counted in code",
        originalTitle = "The spread of the dates given for the Uttiramerur inscriptions, counted in code",
        author = "Uttiramerur assembly",
        year = 919,
        summary = "Three different years are given for the Uttiramerur inscriptions, 919, 920 and 921, and the program prints their earliest, latest and spread.",
        details = "The dates come from accounts that disagree: one account gives 919 and 921 for the two main inscriptions, and another gives 920 for the same inscriptions. The program keeps the three distinct years and prints their spread, so the dispute shows in the output.",
        rare = true,
        instructions = """
            1. Save the example as spread.py.
            2. Run it: python3 spread.py
        """,
        example = """
            # the three years given for the Uttiramerur inscriptions in different accounts
            dates = {
                "first inscription, one account": 919,
                "account giving 920": 920,
                "second inscription, one account": 921,
            }
            years = sorted(dates.values())
            print("earliest date:", years[0])
            print("latest date:", years[-1])
            print("spread in years:", years[-1] - years[0])
            print("distinct dates given:", len(set(dates.values())))
        """,
    ),

)
