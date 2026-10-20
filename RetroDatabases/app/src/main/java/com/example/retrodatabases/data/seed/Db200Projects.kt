package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 42 to 48: four dated events of the decade in Dalmatia, Britain, Judea and Lyon, sorted and spaced by a short program. */
val db200Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 200,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events of 42 to 48 in Dalmatia, Britain, Judea and Lyon, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 42,
        summary = "Four events from 42 to 48 are put in date order and spaced by the years between them, and a short program prints the gaps and the span of the list.",
        details = "The events are the revolt in Dalmatia in 42, the invasion of Britain in 43, the death of Herod Agrippa I in 44 and Claudius's speech at Lyon in 48, each given by its year only. The program sorts them first, so the output does not depend on the order in which the events are typed.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [(48, "Claudius speaks at Lyon"), (42, "revolt in Dalmatia"),
                      (44, "death of Herod Agrippa I"), (43, "invasion of Britain")]
            events.sort()
            for index, (year, name) in enumerate(events):
                if index == 0:
                    print(year, name, "first")
                else:
                    print(year, name, "gap", year - events[index - 1][0])
            print("span in years", events[-1][0] - events[0][0])
        """,
    ),

)
