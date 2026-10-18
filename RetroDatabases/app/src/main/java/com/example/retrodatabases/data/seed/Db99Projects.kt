package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Project of 1054: a short program that sorts three dated events from 1054 to 1066 and prints the days between them. */
val db99Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.PROJECT,
        section = "Lists",
        title = "Three dated events from 1054 to 1066, sorted with the days between them",
        originalTitle = "Sorted dates with gaps",
        author = "No single author",
        year = 1054,
        summary = "A short program sorts three dated events, a Chinese record of a guest star in 1054 and the battles of Stamford Bridge and Hastings in 1066, and prints the gaps between them.",
        details = "The dates are as commonly stated in the Julian reckoning of the time, and the Python calendar gives the same gaps because all three dates fall before 1100. The guest star was first recorded by Chinese astronomers on 4 July 1054, and the two battles fall on 25 September and 14 October 1066.",
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            from datetime import date

            # dates as commonly given, in the Julian reckoning of the time
            events = [
                ("battle of Hastings", date(1066, 10, 14)),
                ("first Chinese record of a guest star", date(1054, 7, 4)),
                ("battle of Stamford Bridge", date(1066, 9, 25)),
            ]
            events.sort(key=lambda event: event[1])
            for name, when in events:
                print(when.isoformat(), name)
            for (name_a, day_a), (name_b, day_b) in zip(events, events[1:]):
                print("from", name_a, "to", name_b + ":", (day_b - day_a).days, "days")
            print("whole span:", (events[-1][1] - events[0][1]).days, "days")
        """,
    ),

)
