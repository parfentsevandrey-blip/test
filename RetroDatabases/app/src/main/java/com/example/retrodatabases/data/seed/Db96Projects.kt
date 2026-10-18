package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1080 to 1090: the weekdays of Christmas Day in the Julian reckoning for each year of the decade. */
val db96Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "The weekdays of Christmas Day in the Julian reckoning for the years 1080 to 1090",
        originalTitle = "Julian calendar",
        author = "Julius Caesar and Gregory XIII",
        year = 1080,
        summary = "A short program lists the weekday of Christmas Day in the Julian reckoning for each year from 1080 to 1090 and counts how often each weekday falls.",
        details = "Each Julian Christmas is moved forward by the six-day gap of the 1080s, and its weekday is read from Python's calendar. The list covers eleven years, and it checks the calendar arithmetic rather than making a claim about any event.",
        instructions = """
            1. Save the example as christmas_weekdays.py.
            2. Run it: python3 christmas_weekdays.py
        """,
        example = """
            from datetime import date, timedelta

            NAMES = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
            GAP = 6    # the six-day gap of the 1080s

            counts = {}
            for year in range(1080, 1091):
                gregorian = date(year, 12, 25) + timedelta(days=GAP)
                name = NAMES[gregorian.weekday()]
                counts[name] = counts.get(name, 0) + 1
                print(year, "Christmas falls on", name)
            for name in NAMES:
                if name in counts:
                    print(name, counts[name])
        """,
    ),

)
