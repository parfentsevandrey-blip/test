package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Project of 833: the reign lengths of eight rulers who held power in 833, summed and ranked in code. */
val db121Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Reign lengths of eight rulers who held power in 833, summed and ranked",
        originalTitle = "Reign lengths of eight rulers who held power in 833, summed and ranked",
        author = "The rulers listed",
        year = 833,
        summary = "Eight rulers in Europe, Asia and Japan held power in 833, and a short program sums their reign lengths and ranks them.",
        details = "The longest reign on the list is that of Egbert of Wessex, at 37 years, and the shortest is that of al-Mu'tasim, at 9, using the reign dates usually given for each ruler. The program prints the number of rulers, their total reign years, and the longest and shortest reigns.",
        instructions = """
            1. Save the example as reigns_833.py.
            2. Run it: python3 reigns_833.py
        """,
        example = """
            RULERS = [
                ("Egbert of Wessex", 802, 839),
                ("Louis the Pious", 814, 840),
                ("Wenzong of Tang", 826, 840),
                ("Junna of Japan", 823, 833),
                ("Theophilos", 829, 842),
                ("al-Ma'mun", 813, 833),
                ("al-Mu'tasim", 833, 842),
                ("Ninmyo of Japan", 833, 850),
            ]
            lengths = [(end - start, name) for name, start, end in RULERS]
            print("rulers:", len(RULERS))
            print("total years:", sum(years for years, _ in lengths))
            longest = max(lengths)
            shortest = min(lengths)
            print("longest:", longest[1], longest[0], "years")
            print("shortest:", shortest[1], shortest[0], "years")
        """,
    ),

)
