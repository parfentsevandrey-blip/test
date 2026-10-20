package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 490 to 500: the letter numerals of the Aryabhatiya, read back as numbers by a short program. */
val db155Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.GUIDE,
        section = "Numbers",
        title = "How Aryabhata's consonant letters stand for the numbers from 1 to 100",
        originalTitle = "Aryabhatiya letter numerals",
        author = "Aryabhata",
        year = 499,
        summary = "Aryabhata gives the consonants of the Sanskrit alphabet the values 1 to 25 and then 30 to 100, and a short program reads a list of letters back as numbers.",
        details = "The consonants ka to ma stand for 1 to 25 in the order of the alphabet, and ya, ra, la, va, sha, sa and ha stand for 30, 40, 50, 60, 70, 90 and 100. The example uses plain romanized letters, so the retroflex series of values 11 to 15 and the retroflex sibilant of value 80 are left out.",
        instructions = """
            1. Save the example as aryabhata_letters.py.
            2. Run it: python3 aryabhata_letters.py
        """,
        example = """
            VALUES = {"ka": 1, "kha": 2, "ga": 3, "gha": 4, "nga": 5,
                      "ca": 6, "cha": 7, "ja": 8, "jha": 9, "nya": 10,
                      "ta": 16, "tha": 17, "da": 18, "dha": 19, "na": 20,
                      "pa": 21, "pha": 22, "ba": 23, "bha": 24, "ma": 25,
                      "ya": 30, "ra": 40, "la": 50, "va": 60, "sha": 70,
                      "sa": 90, "ha": 100}
            for letter in ["ma", "ha", "ka", "ta", "sha"]:
                print(letter, VALUES[letter])
            print("letters with a value:", len(VALUES))
        """,
        rare = true,
    ),

)
