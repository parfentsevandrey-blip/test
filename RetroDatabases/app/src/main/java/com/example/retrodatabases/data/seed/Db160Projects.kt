package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 440 to 450: four dated events from the death of Cyril of Alexandria to the death of Theodosius II, sorted and spaced in code. */
val db160Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events from the death of Cyril of Alexandria in 444 to the death of Theodosius II in 450, sorted and spaced in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 444,
        summary = "A short program sorts four events of 444 to 450 by year and prints the gap before each one, so the spacing can be checked by hand.",
        details = "The events are the death of Cyril of Alexandria in 444, the condemnation of Eutyches at the Home Synod of Constantinople in 448, and the accession of Marcian with the death of Theodosius II in 450. The gaps are counted in whole years, and the two events of 450 come out with a gap of zero.",
        instructions = """
            1. Save the example as events_444_450.py.
            2. Run it: python3 events_444_450.py
        """,
        example = """
            events = [
                (450, "Theodosius II dies"),
                (444, "Cyril of Alexandria dies"),
                (448, "Home Synod condemns Eutyches at Constantinople"),
                (450, "Marcian becomes emperor"),
            ]
            events.sort()
            previous = None
            for year, text in events:
                if previous is None:
                    print(year, text)
                else:
                    print(year, text, "(+" + str(year - previous) + " years)")
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
