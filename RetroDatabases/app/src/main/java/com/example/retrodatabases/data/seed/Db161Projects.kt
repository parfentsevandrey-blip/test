package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 430 to 439: the gaps between six dated events of the decade, worked out in a short program. */
val db161Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.PROJECT,
        section = "Events",
        title = "The gaps between six events of 430 to 439, worked out in code",
        originalTitle = "Events of the 430s",
        author = "No single author",
        year = 430,
        summary = "Six events of the years 430 to 439 are placed in order, and a short program prints each one with the gap in years since the one before.",
        details = "The events are the death of Augustine at Hippo, the opening of the Council of Ephesus, the election of Sixtus III, the marriage of Valentinian III, the Theodosian Code and the Northern Wei conquest of the Northern Liang. The program sorts them by year and prints each gap, so the arithmetic can be checked by hand.",
        instructions = """
            1. Save the example as gaps.py.
            2. Run it: python3 gaps.py
        """,
        example = """
            EVENTS = [
                (439, "Northern Wei take the capital of the Northern Liang"),
                (430, "Augustine dies at Hippo"),
                (438, "the Theodosian Code is issued"),
                (432, "Sixtus III is chosen as pope"),
                (431, "the Council of Ephesus opens"),
                (437, "Valentinian III marries Licinia Eudoxia"),
            ]
            events = sorted(EVENTS)
            previous = None
            for year, label in events:
                if previous is None:
                    print(f"{year}: {label}")
                else:
                    print(f"{year}: {label} (gap {year - previous})")
                previous = year
            print("events:", len(events))
            print("years covered:", events[-1][0] - events[0][0])
        """,
    ),

)
