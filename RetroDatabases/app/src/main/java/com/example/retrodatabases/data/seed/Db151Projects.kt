package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 530 to 540: five dated events of Persia, Constantinople, Africa and Italy, sorted and spaced by a short program. */
val db151Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.PROJECT,
        section = "Byzantine",
        title = "Five events of 531 to 540 in Persia, Constantinople, Africa and Italy, sorted and spaced by a short program",
        originalTitle = "Five events of 531 to 540 in Persia, Constantinople, Africa and Italy, sorted and spaced by a short program",
        author = "Compiled from the standard histories of the period",
        year = 531,
        summary = "Five dated events, from the accession of Khosrow I in Persia to the surrender of Ravenna, are sorted by year and spaced by a short program.",
        details = "The events are the accession of Khosrow I in 531, the Nika riot of 532, Belisarius's victory at Ad Decimum in 533, the dedication of Hagia Sophia in 537 and the surrender of Ravenna in 540. The gaps and the span are simple subtraction, and the list is a short sample of dated events for the decade rather than a full chronology.",
        instructions = """
            1. Save the example as decade_events.py.
            2. Run it: python3 decade_events.py
        """,
        example = """
            EVENTS = [
                (537, "Hagia Sophia dedicated in Constantinople"),
                (531, "Khosrow I becomes king of Persia"),
                (540, "Belisarius takes Ravenna"),
                (533, "Ad Decimum, Belisarius's victory in Africa"),
                (532, "Nika riot in Constantinople"),
            ]

            previous = None
            for year, text in sorted(EVENTS):
                if previous is None:
                    print(year, text)
                else:
                    print(year, text, "gap", year - previous)
                previous = year
            span = sorted(EVENTS)[-1][0] - sorted(EVENTS)[0][0]
            print("span from the first to the last:", span, "years")
        """,
    ),

)
