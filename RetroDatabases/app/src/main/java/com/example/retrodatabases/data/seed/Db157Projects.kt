package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 470 to 480: four events from Rome, Constantinople, the Vandal kingdom and Dalmatia, sorted and spaced by a short program. */
val db157Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.PROJECT,
        section = "Events",
        title = "Four dated events of 472 to 480 from Rome, Constantinople, the Vandals and Dalmatia, sorted in code",
        originalTitle = "Four dated events of 472 to 480 from Rome, Constantinople, the Vandals and Dalmatia, sorted in code",
        author = "No single author",
        year = 472,
        summary = "Four events of 472 to 480 are listed out of order, then sorted by year and printed with the gap in years from the one before.",
        details = "The events are Ricimer's siege of Rome, which ended with the death of Anthemius in 472, Zeno's reign at Constantinople from 474, the death of Gaiseric in 477, and the murder of Julius Nepos in Dalmatia in 480. The program prints each event with its gap from the one before, and then the count and the total span.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (477, "Gaiseric, king of the Vandals, dies"),
                (472, "Ricimer's siege of Rome ends with the death of Anthemius"),
                (480, "Julius Nepos is murdered in Dalmatia"),
                (474, "Zeno becomes sole emperor at Constantinople"),
            ]
            events.sort()
            previous = None
            for year, text in events:
                if previous is None:
                    print(year, text)
                else:
                    print(year, text, "after", year - previous, "years")
                previous = year
            print("events:", len(events))
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
