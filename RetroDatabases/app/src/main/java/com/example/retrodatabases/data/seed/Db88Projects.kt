package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1160 to 1170: the claimants to the papal see, the reigns of three emperors and three rulers who came to power in the decade. */
val db88Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 88,
        kind = Kind.PROJECT,
        section = "Papacy",
        title = "The popes and antipopes of the 1160s, counted year by year",
        originalTitle = "Schisma of 1159",
        author = "The cardinals and the rival claimants",
        year = 1160,
        summary = "A short program lists which claimants held the papal see in each year from 1160 to 1170 and totals the claimant-years of the schism.",
        details = "The list gives Alexander III, Victor IV, Paschal III and Callixtus III, with each reign shown from its first year to its last, and years are counted whole. Three claimants held the see in 1164 and in 1168, and the program prints a total of 24 claimant-years for the decade.",
        instructions = """
            1. Save the example as popes.py.
            2. Run it: python3 popes.py
        """,
        example = """
            CLAIMANTS = [
                ("Alexander III", 1159, 1181),
                ("Victor IV", 1159, 1164),
                ("Paschal III", 1164, 1168),
                ("Callixtus III", 1168, 1178),
            ]
            total = 0
            three = []
            for year in range(1160, 1171):
                holders = [name for name, start, end in CLAIMANTS if start <= year <= end]
                total += len(holders)
                if len(holders) == 3:
                    three.append(year)
                print(year, len(holders), ", ".join(holders))
            print("claimant-years:", total)
            print("years with three claimants:", three)
        """,
    ),

    entry(
        databaseId = 88,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "The emperors Nijo, Rokujo and Takakura, and their years in the 1160s",
        originalTitle = "Tenno",
        author = "The Japanese court",
        year = 1165,
        summary = "A short program measures the reigns of three emperors and counts the years each reign lies inside the decade from 1160 to 1170.",
        details = "Nijo reigned from 1158 to 1165, Rokujo from 1165 to 1168 and Takakura from 1168 to 1180, so the three reigns meet without overlap inside the decade. The program prints each reign's full span and its part inside the decade, and the parts add up to ten years.",
        instructions = """
            1. Save the example as emperors.py.
            2. Run it: python3 emperors.py
        """,
        example = """
            REIGNS = [("Nijo", 1158, 1165), ("Rokujo", 1165, 1168), ("Takakura", 1168, 1180)]
            DECADE_START = 1160
            DECADE_END = 1170
            total = 0
            for name, start, end in REIGNS:
                span = end - start
                inside = min(end, DECADE_END) - max(start, DECADE_START)
                total += inside
                print(name, start, end, "span", span, "inside", inside)
            print("years inside the decade:", total)
        """,
    ),

    entry(
        databaseId = 88,
        kind = Kind.PROJECT,
        section = "Rulers",
        title = "Three rulers who came to power in the 1160s, and the length of their reigns",
        originalTitle = "Reign lengths",
        author = "The courts of Jin, Song and the Almohads",
        year = 1161,
        summary = "A short program gives the reign lengths of Jin Shizong, Song Xiaozong and the Almohad caliph Yusuf, all of whom came to power in the 1160s.",
        details = "Shizong came to power in 1161 and reigned to 1189, Xiaozong from 1162 to 1189, and Yusuf from 1163 to 1184, and the program prints each length, the total and the average. The average is printed to one decimal place.",
        instructions = """
            1. Save the example as reign_lengths.py.
            2. Run it: python3 reign_lengths.py
        """,
        example = """
            RULERS = [("Jin Shizong", 1161, 1189), ("Song Xiaozong", 1162, 1189), ("Almohad Yusuf", 1163, 1184)]
            lengths = []
            for name, start, end in RULERS:
                lengths.append(end - start)
                print(name + ":", end - start, "years")
            print("total:", sum(lengths), "years")
            print("average:", f"{sum(lengths) / len(lengths):.1f}", "years")
        """,
    ),

)
