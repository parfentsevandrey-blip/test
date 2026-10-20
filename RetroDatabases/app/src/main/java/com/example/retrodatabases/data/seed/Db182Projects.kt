package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 220 to 230: four dated events of 221 to 229 in China and Rome, sorted and spaced by a short program. */
val db182Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "Four dated events of 221 to 229 in China and Rome, sorted and spaced in code",
        originalTitle = "Four dated events of 221 to 229 in China and Rome, sorted and spaced in code",
        author = "No single author",
        year = 221,
        summary = "Four dated events of 221 to 229, from Shu, Rome, Wei and Wu, are sorted by year and spaced by a short program.",
        details = "The events are Liu Bei's taking of the title of emperor of Shu in 221, the accession of Severus Alexander in Rome in 222, the succession of Cao Rui in Wei in 226, and Sun Quan's title of emperor of Wu in 229. Each event is given by its year only, so the gaps between them are whole years, and the program prints them in order.",
        instructions = """
            1. Save the example as four_events.py.
            2. Run it: python3 four_events.py
        """,
        example = """
            # Four dated events, each given by its year, sorted and spaced by a short program.
            EVENTS = [
                (229, "Sun Quan takes the title of emperor of Wu"),
                (221, "Liu Bei takes the title of emperor of Shu"),
                (226, "Cao Rui succeeds Cao Pi in Wei"),
                (222, "Severus Alexander succeeds Elagabalus in Rome"),
            ]
            events = sorted(EVENTS)
            for year, text in events:
                print(year, text)
            for (y1, _), (y2, _) in zip(events, events[1:]):
                print(f"from {y1} to {y2}: {y2 - y1}")
            print("span from first to last:", events[-1][0] - events[0][0])
        """,
    ),

)
