package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 260 to 268: four dated events of the 260s, sorted and spaced in code. */
val db178Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four dated events of 260 to 268, sorted and spaced in code",
        originalTitle = "Event timeline",
        author = "No single author",
        year = 260,
        summary = "A short program sorts four dated events of the 260s and prints the gaps between them.",
        details = "The events are the capture of Valerian at Edessa in 260, Liu Hui's commentary in 263, the start of the Western Jin in 266 and the presentation of the Jin code in 268, each at its usual year. The program sorts them, prints the gaps and the total span, which runs eight years from the first event to the last.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (266, "the Western Jin begins under Sima Yan"),
                (260, "Valerian is captured at Edessa"),
                (268, "the Jin code is presented"),
                (263, "Liu Hui completes his commentary"),
            ]
            events.sort()
            for year, text in events:
                print(year, text)
            for (first, _), (second, _) in zip(events, events[1:]):
                print("gap from", first, "to", second, "is", second - first, "years")
            print("span from", events[0][0], "to", events[-1][0], "is", events[-1][0] - events[0][0], "years")
        """,
    ),

)
