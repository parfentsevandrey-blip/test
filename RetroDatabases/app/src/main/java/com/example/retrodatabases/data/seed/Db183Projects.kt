package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 210 to 220: a timeline of four Roman events from 211 to 217, sorted and spaced by a short program. */
val db183Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 183,
        kind = Kind.PROJECT,
        section = "Events",
        title = "A timeline of four Roman events from York to Carrhae, 211 to 217, in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 217,
        summary = "Four events of the years 211 to 217, from Severus's death at York to Caracalla's murder near Carrhae, are sorted by year and spaced by a short program.",
        details = "The events are Severus's death at York in 211, the Constitutio Antoniniana in 212, Caracalla's massacre at Alexandria in 215 and his murder near Carrhae in 217. The program sorts them by year, prints the gap since the event before for each event after the first, and prints the span from the first to the last.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (217, "Caracalla is murdered near Carrhae"),
                (211, "Septimius Severus dies at York"),
                (215, "Caracalla's massacre at Alexandria"),
                (212, "the Constitutio Antoniniana extends citizenship"),
            ]
            events.sort()
            previous = None
            for year, text in events:
                if previous is None:
                    print(year, text)
                else:
                    print(year, text, "gap", year - previous)
                previous = year
            print("span", events[-1][0] - events[0][0])
        """,
    ),

)
