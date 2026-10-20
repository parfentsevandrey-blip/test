package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 190 to 198: four turning points, put in order by year with the years between them. */
val db185Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four turning points of 190 to 198 in China and Rome, in order with the years between them",
        originalTitle = "Four turning points of 190 to 198 in China and Rome, in order with the years between them",
        author = "No single author",
        year = 190,
        summary = "Four turning points of 190 to 198 are sorted by year, and a short program prints the years between neighbouring events.",
        details = "They are Dong Zhuo's burning of Luoyang in 190, Pertinax's murder in 193, Cao Cao's removal of the emperor to Xu in 196 and Severus's siege of Hatra in 198, each given by its year only. The siege of Hatra is dated variously in the sources, to 198 or to the years after, so this record uses 198, and the program prints the gap between each pair of neighbouring years.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (196, "Cao Cao takes the emperor to Xu"),
                (190, "Dong Zhuo burns Luoyang"),
                (198, "Severus besieges Hatra"),
                (193, "Pertinax is murdered"),
            ]
            events.sort()
            previous = None
            for year, label in events:
                if previous is None:
                    print(year, label)
                else:
                    print(year, label, "gap", year - previous)
                previous = year
            print("span in years", events[-1][0] - events[0][0])
        """,
    ),

)
