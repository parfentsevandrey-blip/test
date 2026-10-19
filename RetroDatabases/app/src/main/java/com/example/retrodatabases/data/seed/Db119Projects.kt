package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 850 to 860, a placed record: a small program over four dated events of the 850s, with the gaps between them. */
val db119Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Four dated events of the 850s, sorted, with the gaps between them",
        originalTitle = "Four dated events of the 850s, sorted, with the gaps between them",
        author = "Muhammad I of Cordoba, Lothair I and Photius",
        year = 852,
        summary = "A small program sorts four dated events of the 850s from different realms and prints the gaps between them, the longest gap and the total span.",
        details = "This is a placed record: the year 852 is the accession of Muhammad I at Cordoba, the anchor for placement and the first event on the list, and not a date for the program. The other events are the division of Lothair I's realm in 855, Photius's elevation to the patriarchate of Constantinople in 858 and the Rus raid on Constantinople in 860.",
        rare = true,
        instructions = """
            1. Save the example as dated_events.py.
            2. Run it: python3 dated_events.py
        """,
        example = """
            events = [
                (860, "Rus raid on Constantinople"),
                (852, "Muhammad I succeeds at Cordoba"),
                (858, "Photius becomes patriarch of Constantinople"),
                (855, "Lothair I divides his realm among his sons"),
            ]
            events.sort()
            gaps = []
            for i, (year, label) in enumerate(events):
                print(year, label)
                if i > 0:
                    gap = year - events[i - 1][0]
                    gaps.append(gap)
                    print("  gap of", gap, "years since the last event")
            print("events:", len(events))
            print("longest gap:", max(gaps), "years")
            print("span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
