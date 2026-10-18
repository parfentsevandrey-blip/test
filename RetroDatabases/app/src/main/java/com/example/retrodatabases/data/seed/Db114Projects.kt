package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 901 and 905: Japan's six national histories with their volumes and completion years, and the imperial anthologies from Ryounshu to Kokinshu with the gaps between them. */
val db114Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "The six national histories of Japan, their volumes and their gaps in years",
        originalTitle = "Rikkokushi",
        author = "Court historians of Japan",
        year = 901,
        summary = "Six national histories were completed in Japan between 720 and 901, and a short program adds up their volumes and measures the gaps between their completion years.",
        details = "The histories are the Nihon Shoki, Shoku Nihongi, Nihon Koki, Shoku Nihon Koki, Montoku Jitsuroku and Nihon Sandai Jitsuroku, with the completion years and volume counts usually given for each. The program adds the 190 volumes and prints the years between neighbouring completions, which add up to the span from 720 to 901.",
        instructions = """
            1. Save the example as histories.py.
            2. Run it: python3 histories.py
        """,
        example = """
            HISTORIES = [
                ("Nihon Shoki", 720, 30),
                ("Shoku Nihongi", 797, 40),
                ("Nihon Koki", 840, 40),
                ("Shoku Nihon Koki", 869, 20),
                ("Montoku Jitsuroku", 879, 10),
                ("Nihon Sandai Jitsuroku", 901, 50),
            ]
            print("volumes in all:", sum(volumes for _, _, volumes in HISTORIES))
            for (name, year, _), (_, following, _) in zip(HISTORIES, HISTORIES[1:]):
                print(f"{name} to the next history: {following - year} years")
            print("span from the first to the last:", HISTORIES[-1][1] - HISTORIES[0][1])
        """,
    ),

    entry(
        databaseId = 114,
        kind = Kind.PROJECT,
        section = "Poetry",
        title = "The imperial anthologies from Ryounshu to Kokinshu, and the gaps between them",
        originalTitle = "Ryounshu, Bunka Shureishu, Keikokushu, Kokinshu",
        author = "The emperors who commissioned them",
        year = 905,
        summary = "Four imperial anthologies from 814 to 905 are listed here, and a short program measures the years between each pair and the span of the list.",
        details = "The first three are anthologies of Chinese-style poems commissioned by emperors, and the Kokinshu that ends the list is the first imperial anthology of Japanese poems. The program prints each gap between neighbouring anthologies and the span from the first to the last.",
        instructions = """
            1. Save the example as anthologies.py.
            2. Run it: python3 anthologies.py
        """,
        example = """
            ANTHOLOGIES = [
                ("Ryounshu", 814),
                ("Bunka Shureishu", 818),
                ("Keikokushu", 827),
                ("Kokinshu", 905),
            ]
            for (name, year), (_, following) in zip(ANTHOLOGIES, ANTHOLOGIES[1:]):
                print(f"{name} to the next anthology: {following - year} years")
            print("anthologies listed:", len(ANTHOLOGIES))
            print("span from the first to the last:", ANTHOLOGIES[-1][1] - ANTHOLOGIES[0][1])
        """,
        rare = true,
    ),

)
