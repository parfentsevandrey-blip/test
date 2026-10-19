package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 820: Japan's early legal codes, from the Taiho code of 701 to the Konin kyakushiki of 820, with the years between them. */
val db122Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.PROJECT,
        section = "Japan",
        title = "Japan's early legal codes, from the Taiho code of 701 to the Konin kyakushiki of 820, and the years between them",
        originalTitle = "Taiho ritsuryo, Yoro ritsuryo and Konin kyakushiki",
        author = "Compilers at the Nara and Heian courts",
        year = 820,
        summary = "Three Japanese legal codes, from the Taiho code of 701 to the Konin kyakushiki completed in 820, are listed here, and a short program measures the years between them.",
        details = "The Taiho code was completed in 701, and the Yoro code was compiled in 718 although it was put into force only in 757, so the program uses the compiling date. The Konin kyakushiki was completed in 820 after work that began about 803, and it collected the edicts issued from 701 to 819, with 830 given as the year it came into force.",
        instructions = """
            1. Save the example as legal_codes.py.
            2. Run it: python3 legal_codes.py
        """,
        example = """
            CODES = [
                ("Taiho code", 701),
                ("Yoro code", 718),
                ("Konin kyakushiki", 820),
            ]
            for (name, year), (_, following) in zip(CODES, CODES[1:]):
                print(f"{name} to the next code: {following - year} years")
            print("codes listed:", len(CODES))
            print("span from the first to the last:", CODES[-1][1] - CODES[0][1])
        """,
        rare = true,
    ),

)
