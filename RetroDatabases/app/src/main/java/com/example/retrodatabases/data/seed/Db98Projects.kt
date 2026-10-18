package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1066: four English dates of one year put in order, with the days between them. */
val db98Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "The gaps between four dates of 1066, from Harold's crowning to Hastings",
        originalTitle = "Days between dated events",
        author = "Harold Godwineson and William of Normandy",
        year = 1066,
        summary = "A short program lists four English dates of 1066 in order and prints the days between each pair, from Harold's crowning on 6 January to the battle of Hastings on 14 October, which is 281 days.",
        details = "The dates are taken as commonly given in the Julian calendar used in England, and 1066 is not a leap year, so the day counts are the same in either calendar. The example uses Python's date type, which counts the same days within a single non-leap year.",
        instructions = """
            1. Save the example as gaps_1066.py.
            2. Run it: python3 gaps_1066.py
        """,
        example = """
            from datetime import date

            events = [
                ("Harold crowned", date(1066, 1, 6)),
                ("Stamford Bridge", date(1066, 9, 25)),
                ("Pevensey landing", date(1066, 9, 28)),
                ("Hastings", date(1066, 10, 14)),
            ]
            previous = None
            for name, day in events:
                if previous is None:
                    print(day.isoformat(), name)
                else:
                    print(day.isoformat(), name, "(" + str((day - previous).days) + " days after the last)")
                previous = day
            print("from crowning to Hastings:", (events[-1][1] - events[0][1]).days, "days")
        """,
    ),

)
