package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 360 to 370: three dated events of 361 to 366 in the Roman world, sorted and spaced by a short program. */
val db168Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.PROJECT,
        section = "Timeline",
        title = "Three events of 361 to 366 in the Roman world, sorted and spaced in code",
        originalTitle = "Three dated events",
        author = "No single author",
        year = 366,
        summary = "Three dated events of 361 to 366, the death of Constantius II, the revolt of Procopius against Valens and the election of Damasus at Rome, are sorted by year and spaced by a short program.",
        details = "Constantius II died in 361, Procopius revolted against Valens in 365, and Damasus was chosen bishop of Rome in 366. The program prints each event with the gap in years from the one before, and it does only subtraction, so the output can be checked by hand.",
        instructions = """
            1. Save the example as timeline.py.
            2. Run it: python3 timeline.py
        """,
        example = """
            # Three dated events of 361 to 366, sorted by year and spaced by the years between them.
            events = [
                (366, "Damasus chosen bishop of Rome"),
                (361, "death of Constantius II"),
                (365, "revolt of Procopius against Valens"),
            ]
            events.sort()
            previous = None
            for year, label in events:
                if previous is None:
                    print(year, label)
                else:
                    print(year, label, "(+%d)" % (year - previous))
                previous = year
            print("span in years:", events[-1][0] - events[0][0])
        """,
    ),

)
