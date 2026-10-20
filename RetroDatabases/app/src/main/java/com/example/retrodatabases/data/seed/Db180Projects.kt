package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 241 to 249: four dated events of the decade, sorted by year and spaced in code. */
val db180Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Four events of 241 to 249 in year order, with the gaps between them counted",
        originalTitle = "Year order of four events",
        author = "No single author",
        year = 241,
        summary = "The stone classics, Gordian's death, the Secular Games and the Gaoping coup are sorted by year, and a short program prints the gap to the next event and the whole span.",
        details = "The years are the ones given for each event, and the program sorts the list itself, so the order does not depend on how the events are written down. The gaps come to 3, 4 and 1 years, and the span from the first event to the last is 8 years, which can be checked by hand.",
        instructions = """
            1. Save the example as events.py.
            2. Run it: python3 events.py
        """,
        example = """
            events = [
                (248, "the Secular Games of Philip the Arab"),
                (241, "the Zhengshi stone classics"),
                (249, "the Gaoping Tombs coup of Sima Yi"),
                (244, "the death of Gordian III near Misiche"),
            ]
            events.sort()
            for index, (year, label) in enumerate(events[:-1]):
                gap = events[index + 1][0] - year
                print(year, label, "gap", gap, "years")
            last_year, last_label = events[-1]
            print(last_year, last_label, "is the last event")
            print("span from first to last:", last_year - events[0][0], "years")
        """,
    ),

)
