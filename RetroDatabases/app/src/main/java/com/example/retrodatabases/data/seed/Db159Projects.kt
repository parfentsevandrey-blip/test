package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of the 450s: five dated events of the early 450s, sorted in code, with the gaps in years between them. */
val db159Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.PROJECT,
        section = "Dates",
        title = "Five events of the early 450s, from Marcian's accession to the murder of Valentinian III",
        originalTitle = "Five events of the early 450s",
        author = "No single author",
        year = 450,
        summary = "A short program sorts five dated events of the early 450s, from Marcian's accession to the murder of Valentinian III, and prints the gaps in years between them.",
        details = "The events come from the history of the Eastern and Western empires and from Attila's campaigns, and none of them is a record of this database. Each year is the year usually given for the event, so the span and the gaps can be checked by hand.",
        instructions = """
            1. Save the example as timeline450.py.
            2. Run it: python3 timeline450.py
        """,
        example = """
            events = [
                (455, "Valentinian III murdered"),
                (450, "Marcian becomes emperor in the East"),
                (454, "Aetius killed by Valentinian III"),
                (452, "Attila turns back from Italy"),
                (453, "Attila dies"),
            ]
            events.sort()
            gaps = []
            previous = None
            for year, name in events:
                if previous is not None:
                    gaps.append(year - previous)
                print(year, name)
                previous = year
            print("events:", len(events))
            print("span in years:", events[-1][0] - events[0][0])
            print("gaps:", gaps)
        """,
    ),

)
