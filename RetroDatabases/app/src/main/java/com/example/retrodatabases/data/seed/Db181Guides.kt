package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 230 to 240: the Egyptian civil year of twelve months and five extra days, placed at 231 on Origen's departure from Alexandria. */
val db181Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 181,
        kind = Kind.GUIDE,
        section = "Calendars",
        title = "How an Egyptian civil month and day become a day of the year",
        originalTitle = "Egyptian civil year",
        author = "Egyptian scribes of the Roman period",
        year = 231,
        summary = "The Egyptian civil year has twelve months of thirty days and five extra days at the end, and a short program turns a month and a day into a day of the year.",
        details = "The method is far older than the 230s and was still in use in Roman Egypt, but the sources do not date it, so 231 is the year of the anchor, Origen's departure from Alexandria, and not the date of the reckoning. The example uses the Greek forms of the month names and checks three dates together with the total of 365 days.",
        instructions = """
            1. Save the example as egyptian_day.py.
            2. Run it: python3 egyptian_day.py
        """,
        example = """
            MONTHS = ["Thoth", "Phaophi", "Hathyr", "Choiak", "Tybi", "Mechir",
                      "Phamenoth", "Pharmouthi", "Pachon", "Payni", "Epiphi", "Mesore"]

            def day_of_year(month, day):
                # Each of the twelve months has thirty days, and the five extra days come after Mesore.
                return MONTHS.index(month) * 30 + day

            for month, day in [("Thoth", 1), ("Choiak", 12), ("Mesore", 30)]:
                print(month, day, "is day", day_of_year(month, day))
            extra_days = 5
            print("extra days after Mesore:", extra_days)
            print("days in the year:", len(MONTHS) * 30 + extra_days)
        """,
        rare = true,
    ),

)
