package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 410 to 418: a timeline of three dated events of the 410s, sorted and spaced in code. */
val db163Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "A timeline of three dated events from 410 to 415, spaced in code",
        originalTitle = "Timeline of three dated events",
        author = "No single author",
        year = 415,
        summary = "A short program sorts three dated events of the 410s by year and prints the gap between each pair and the span of the whole.",
        details = "The events are the sack of Rome by the Goths in 410, the conference of Carthage between Catholic and Donatist bishops in 411, and the killing of Hypatia in Alexandria in 415. The dates are years only, so the gaps are whole years, and the output is fixed so that it can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            EVENTS = [
                (415, "death of Hypatia in Alexandria"),
                (410, "sack of Rome by the Goths"),
                (411, "conference of Carthage between Catholic and Donatist bishops"),
            ]
            events = sorted(EVENTS)
            for year, what in events:
                print(year, what)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("gap from", first, "to", second, ":", second - first)
            print("span of the timeline:", events[-1][0] - events[0][0])
        """,
        rare = true,
    ),

)
