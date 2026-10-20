package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 380 to 390: a short timeline of four events, from the Fei River to Thessalonica. */
val db166Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.PROJECT,
        section = "Events",
        title = "A short timeline of four events of 383 to 390, from the Fei River to Thessalonica",
        originalTitle = "Timeline",
        author = "No single author",
        year = 383,
        summary = "Four dated events of 383 to 390, from a battle in China to a massacre in Thessalonica, are sorted and spaced by a short program that prints the years between them.",
        details = "The events are the battle of the Fei River and Magnus Maximus's proclamation in Britain, both in 383, Maximus's death at Aquileia in 388, and the massacre at Thessalonica in 390. The program sorts them by year and prints the gap between each pair, so the output is 0, 5 and 2 years, with a span of seven years.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            EVENTS = [
                (390, "Massacre of Thessalonica"),
                (383, "Magnus Maximus proclaimed in Britain"),
                (388, "Magnus Maximus killed at Aquileia"),
                (383, "Battle of the Fei River"),
            ]
            events = sorted(EVENTS)
            print("timeline:")
            for year, label in events:
                print(year, label)
            gaps = [later[0] - earlier[0] for earlier, later in zip(events, events[1:])]
            print("years between events:", " ".join(str(gap) for gap in gaps))
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
