package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 250 to 260: four dated events of the decade, sorted by year and spaced in code. */
val db179Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 179,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four events of the 250s put in order and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 260,
        summary = "Four dated events from 250 to 260, Decius's edict, Decius's death at Abritus, Valerian's edict and Valerian's capture, are sorted by year and spaced by a short program.",
        details = "The events are listed out of order in the example, and the program sorts them and prints the years between neighbours, so the gaps are 1, 7 and 2 years and the span is 10 years. The years are the commonly given ones, and the program counts whole years only, so the timeline says nothing about the months in which each event fell.",
        instructions = """
            1. Save the example as decade_events.py.
            2. Run it: python3 decade_events.py
        """,
        example = """
            events = [
                (260, "Valerian taken at Edessa"),
                (250, "Decius's edict of sacrifice"),
                (258, "Valerian's edict ordering the death of clergy"),
                (251, "Decius killed at Abritus"),
            ]
            events.sort()
            previous = None
            for year, title in events:
                if previous is None:
                    print(year, title)
                else:
                    print(year, title, "(+" + str(year - previous) + ")")
                previous = year
            print("span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
