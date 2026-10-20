package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide placed at 250 by its anchor: the Egyptian civil year, with its twelve months and five added days. */
val db179Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How a day of the Egyptian civil year gets its month, with five days added at the end",
        originalTitle = "Egyptian civil year",
        author = "Scribes of Roman Egypt",
        year = 250,
        summary = "The Egyptian civil year had twelve months of thirty days and five added days, and a short program names the month and day for seven days of the year.",
        details = "The year runs from Thoth 1 as day 1 to Mesore 30 as day 360, and the five added days follow as day 361 to day 365. The method is older than the example, so 250 is the placed year: it is the year of Decius's edict, the anchor for the example, and not a date for the calendar.",
        instructions = """
            1. Save the example as egypt_year.py.
            2. Run it: python3 egypt_year.py
        """,
        example = """
            MONTHS = ["Thoth", "Phaophi", "Athyr", "Choiak", "Tybi", "Mechir", "Phamenoth",
                      "Pharmouthi", "Pachon", "Payni", "Epiphi", "Mesore"]

            def egyptian_date(day):
                # day counts from 1 to 365; the last five days are the added days
                if day > 360:
                    return "Epagomenal day " + str(day - 360)
                month, dayofmonth = divmod(day - 1, 30)
                return MONTHS[month] + " " + str(dayofmonth + 1)

            for day in [1, 30, 31, 150, 360, 361, 365]:
                print(day, egyptian_date(day))
        """,
        rare = true,
    ),

)
