package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 680 to 690: seven monastic foundations from Iona to Jarrow, with the gaps between their years counted by a program. */
val db136Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.PROJECT,
        section = "Insular",
        title = "Seven monasteries founded by Irish and Anglo-Saxon monks, from Iona in 563 to Jarrow in 681, with the gaps between them counted by a program",
        originalTitle = "Monastic foundations",
        author = "Columba, Columbanus, Aidan, Hild and Benedict Biscop",
        year = 681,
        summary = "A short program lists seven monastic foundations from Iona to Jarrow and prints the gap between each pair of founding years.",
        details = "The founding years used are the usual dates: Iona 563, Luxeuil 590, Bobbio 614, Lindisfarne 635, Whitby 657, Monkwearmouth 674 and Jarrow 681. The program prints the six gaps, the total span of 118 years, and the longest and shortest gaps, so its results depend only on these dates.",
        instructions = """
            1. Save the example as monastery_gaps.py.
            2. Run it: python3 monastery_gaps.py
        """,
        example = """
            FOUNDATIONS = [
                ("Iona", 563),
                ("Luxeuil", 590),
                ("Bobbio", 614),
                ("Lindisfarne", 635),
                ("Whitby", 657),
                ("Monkwearmouth", 674),
                ("Jarrow", 681),
            ]
            gaps = []
            for (name, first), (other, second) in zip(FOUNDATIONS, FOUNDATIONS[1:]):
                gap = second - first
                gaps.append((gap, name + " to " + other))
                print(name, "to", other, ":", gap, "years")
            print("total span:", FOUNDATIONS[-1][1] - FOUNDATIONS[0][1], "years")
            longest = max(gaps)
            shortest = min(gaps)
            print("longest gap:", longest[1], longest[0], "years")
            print("shortest gap:", shortest[1], shortest[0], "years")
        """,
    ),

)
