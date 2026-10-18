package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1020 to 1030: a short program that sorts three dated deaths of 1024 and 1025 and counts the days between them. */
val db102Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Days between three deaths of 1024 and 1025, in date order",
        originalTitle = "Days between three deaths of 1024 and 1025, in date order",
        author = "Benedict VIII, Henry II and Basil II",
        year = 1024,
        summary = "Benedict VIII, Henry II and Basil II died on 9 April 1024, 13 July 1024 and 15 December 1025, and a short program sorts the three dates and counts the days between them.",
        details = "The dates are given as commonly stated, and Python's calendar does the counting, so the program checks date arithmetic and not the reigns of the three men. The result is 95 days from the first death to the second, 520 from the second to the third, and 615 in all.",
        instructions = """
            1. Save the example as rulers_deaths.py.
            2. Run it: python3 rulers_deaths.py
        """,
        example = """
            from datetime import date

            deaths = [
                ("Henry II", date(1024, 7, 13)),
                ("Benedict VIII", date(1024, 4, 9)),
                ("Basil II", date(1025, 12, 15)),
            ]
            deaths.sort(key=lambda item: item[1])
            for (name_a, day_a), (name_b, day_b) in zip(deaths, deaths[1:]):
                print("days from", name_a, "to", name_b + ":", (day_b - day_a).days)
            print("first to last:", (deaths[-1][1] - deaths[0][1]).days)
        """,
    ),

)
