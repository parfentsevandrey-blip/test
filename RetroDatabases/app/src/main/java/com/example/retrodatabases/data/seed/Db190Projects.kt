package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 140 to 150: four dated events from 142 to 148, from the Antonine Wall to An Shigao at Luoyang, sorted and spaced in code. */
val db190Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.PROJECT,
        section = "Timelines",
        title = "A timeline of 142 to 148 from a British wall to a Chinese translator, in code",
        originalTitle = "Timeline of four events",
        author = "No single author",
        year = 142,
        summary = "Four dated events from 142 to 148 are sorted and spaced by a short program, from the Antonine Wall in Britain to An Shigao at Luoyang.",
        details = "The Antonine Wall was begun in 142, Shun of Han died in 144, Huan took the Han throne in 146, and An Shigao reached Luoyang in 148, with accounts differing on that last date. The program sorts the four events and prints the gap before each one, which is two years between each pair.",
        rare = true,
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            events = [
                (148, "An Shigao at Luoyang"),
                (142, "Antonine Wall begun"),
                (146, "Han emperor Huan takes the throne"),
                (144, "Han emperor Shun dies"),
            ]
            events.sort()
            previous = None
            for year, name in events:
                if previous is None:
                    print(year, name)
                else:
                    print(year, name, "after", year - previous, "years")
                previous = year
            print("span:", events[-1][0] - events[0][0], "years")
        """,
    ),

)
