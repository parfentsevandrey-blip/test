package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1115 to 1120: six dated events of the decade put in order, with the days between each event and the next. */
val db93Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Ordering the decade's dated events and counting the days between them",
        originalTitle = "Event list",
        author = "No single author",
        year = 1120,
        summary = "A short program sorts six dated events of 1115 to 1120 and prints the days between each event and the next.",
        details = "The events are the battle of Welfesholz, the death of Alexios I, the death of Pope Gelasius II, the battle of the Field of Blood, the battle of Bremule and the loss of the White Ship, with dates as commonly stated. The gaps add up to the span from the first event to the last, and the program prints that span at the end.",
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            from datetime import date

            EVENTS = [
                (date(1119, 8, 20), "Bremule"),
                (date(1118, 8, 15), "Alexios I dies"),
                (date(1120, 11, 25), "White Ship lost"),
                (date(1115, 2, 11), "Welfesholz"),
                (date(1119, 1, 29), "Gelasius II dies"),
                (date(1119, 6, 28), "Field of Blood"),
            ]
            events = sorted(EVENTS)
            for index, (when, label) in enumerate(events):
                if index + 1 < len(events):
                    gap = (events[index + 1][0] - when).days
                    print(f"{when.isoformat()} {label}: {gap} days to the next")
                else:
                    print(f"{when.isoformat()} {label}: last event")
            print("span in days:", (events[-1][0] - events[0][0]).days)
        """,
    ),

)
