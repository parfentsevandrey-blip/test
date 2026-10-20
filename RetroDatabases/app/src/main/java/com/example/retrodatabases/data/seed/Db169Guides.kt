package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 350: the forty fasting days of Lent, counted back from Easter in a short program. */
val db169Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.GUIDE,
        section = "Lent",
        title = "How the forty fasting days of Lent are counted back from Easter",
        originalTitle = "Quadragesima",
        author = "The churches of the fourth century",
        year = 350,
        summary = "Lent has forty fasting days before Easter, and because the six Sundays are not fasting days, Ash Wednesday falls 46 days before Easter Sunday.",
        details = "The rule is the same in every year, and the record uses 350 because that is the rounded date of Cyril's course in Jerusalem, which keeps the year in the decade. The example takes Easter as a fixed day of the year, so the count is the same on every run, and it prints the days, the Sundays and the fasting days.",
        instructions = """
            1. Save the example as lent.py.
            2. Run it: python3 lent.py
        """,
        example = """
            ASH_WEDNESDAY = 54   # day of the year, illustrative
            EASTER = 100         # Easter Sunday as a day of the year, illustrative
            days = EASTER - ASH_WEDNESDAY
            sundays = sum(1 for d in range(days) if (3 + d) % 7 == 0)   # Ash Wednesday is a Wednesday; weekday 0 is Sunday
            print("days from Ash Wednesday to Easter:", days)
            print("Sundays in Lent:", sundays)
            print("fasting days:", days - sundays)
        """,
    ),

)
