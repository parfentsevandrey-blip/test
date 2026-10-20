package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 630 to 640: three dated events of 630 to 634 from China, the steppe and Arabia, sorted and gapped by a short program. */
val db141Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 141,
        kind = Kind.PROJECT,
        section = "Dated events",
        title = "Three dated events of 630 to 634 from China, the Turkish steppe and Arabia, put in order with the years between them",
        originalTitle = "Tang, Eastern Turks, Muhammad and Umar",
        author = "The courts and armies named in the events",
        year = 630,
        summary = "Three events of 630 to 634, a Tang victory over the Eastern Turks, the death of Muhammad and the accession of Umar, are sorted by a short program that prints the years between them.",
        details = "The usual dates are 630 for the Tang campaign against the Eastern Turks, 632 for the death of Muhammad and 634 for Umar's accession, and the program sorts them and prints the gap between each pair. None of the three events is a record of this database, and the years are the ones given in the usual histories.",
        instructions = """
            1. Save the example as event_gaps.py.
            2. Run it: python3 event_gaps.py
        """,
        example = """
            EVENTS = [
                (634, "Umar becomes the second caliph"),
                (630, "the Tang army defeats the Eastern Turks"),
                (632, "the Prophet Muhammad dies"),
            ]

            events = sorted(EVENTS)
            for year, what in events:
                print(year, what)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("years between", first, "and", second, ":", second - first)
            print("span from the first to the last event:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
