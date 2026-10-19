package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 882: four accessions and coronations of the 880s, listed in order, with the years between them measured in code. */
val db116Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Four accessions and coronations of 882 to 887, listed in order with their gaps",
        originalTitle = "Four accessions and coronations of 882 to 887, listed in order with their gaps",
        author = "Marinus I, Koko, Leo VI and Uda",
        year = 882,
        summary = "Four accessions and coronations of the 880s are listed in order, and a short program prints the years between neighbours and the span of the list.",
        details = "The dates are those usually given: Marinus I became pope in 882, Koko was enthroned in Japan in 884, Leo VI became sole ruler of Byzantium in 886 after his father's death, and Uda succeeded in Japan in 887. The program prints the gap between each pair of neighbours and the span from the first event to the last, which comes to five years.",
        instructions = """
            1. Save the example as accessions.py.
            2. Run it: python3 accessions.py
        """,
        example = """
            EVENTS = [
                ("Marinus I elected pope", 882),
                ("Koko enthroned in Japan", 884),
                ("Leo VI sole ruler of Byzantium", 886),
                ("Uda succeeded in Japan", 887),
            ]
            for (name, year), (_, following) in zip(EVENTS, EVENTS[1:]):
                print(f"{name} to the next, in years: {following - year}")
            print("events listed:", len(EVENTS))
            print("span from the first to the last:", EVENTS[-1][1] - EVENTS[0][1])
        """,
    ),

)
