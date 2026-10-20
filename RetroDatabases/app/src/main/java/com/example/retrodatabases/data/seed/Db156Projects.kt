package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 480 to 490: a short timeline of three dated events from Rome, Persia and Italy, none of them a record of this database. */
val db156Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Three dated events of 483 to 489 from Rome, Persia and Italy, put in order with the years between them",
        originalTitle = "Timeline of three events",
        author = "No single author",
        year = 489,
        summary = "A short program puts three events of 483 to 489 from Rome, Persia and Italy in date order and counts the years between them, and the example prints the timeline.",
        details = "The three events are the death of Pope Simplicius in 483, the death of the Persian king Peroz in battle with the Hephthalites in 484, and the battle of Verona in 489, each given by its year. None of them is a record of this database, and the example takes the years as given.",
        rare = true,
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (489, "battle of Verona, Theoderic defeats Odoacer"),
                (483, "death of Pope Simplicius"),
                (484, "death of Peroz I of Persia in battle with the Hephthalites"),
            ]
            events.sort()
            previous = None
            for year, label in events:
                if previous is None:
                    print(year, label, "| first event")
                else:
                    print(year, label, "| years since previous:", year - previous)
                previous = year
            print("span from first to last:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
