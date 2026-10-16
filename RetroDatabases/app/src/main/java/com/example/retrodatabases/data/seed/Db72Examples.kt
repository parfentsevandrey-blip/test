package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1320 to 1330: a year in abjad letters, the years counted from Rome, the guilds of Florence, the liberal arts, Dante's age and the gematria of a name. */
val db72Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "The year 1321 written in abjad letters",
        originalTitle = "Abjad numerals",
        author = "Arabic writers of the abjad",
        year = 1321,
        summary = "Arabic numerals were written with the letters of the alphabet, and a short program splits four years of the 1320s into their abjad letters.",
        details = "Each letter carries a value, and the largest letter is taken first, so 1321 is written as ghayn, shin, kaf and alif with values 1000, 300, 20 and 1. The letter names are given in plain transliteration, and the program prints each year as a sum of letter values.",
        instructions = """
            1. Save the example as abjad.py.
            2. Run it: python3 abjad.py
        """,
        example = """
            LETTERS = [
                (1000, "ghayn"), (900, "dha"), (800, "dad"), (700, "dhal"), (600, "kha"), (500, "tha"),
                (400, "ta"), (300, "shin"), (200, "ra"), (100, "qaf"), (90, "sad"), (80, "fa"),
                (70, "ayn"), (60, "sin"), (50, "nun"), (40, "mim"), (30, "lam"), (20, "kaf"),
                (10, "ya"), (9, "ta"), (8, "ha"), (7, "zayn"), (6, "waw"), (5, "ha"), (4, "dal"),
                (3, "jim"), (2, "ba"), (1, "alif"),
            ]

            def decompose(number):
                parts = []
                for value, letter in LETTERS:
                    while number >= value:
                        parts.append(f"{letter} ({value})")
                        number -= value
                return parts

            for year in [1320, 1321, 1326, 1330]:
                print(year, "=", " + ".join(decompose(year)))
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Chronology",
        title = "The years of the 1320s counted from the founding of Rome",
        originalTitle = "Ab urbe condita",
        author = "Chroniclers who counted from Rome",
        year = 1320,
        summary = "Some chroniclers counted years from the founding of Rome, and a short program converts each year of the 1320s to that count by adding 753.",
        details = "The Roman count runs 753 years ahead of the Christian count, so each conversion adds 753 to the year. Only a few writers of the period used this count, and the example is a conversion exercise rather than a record of common practice.",
        instructions = """
            1. Save the example as rome_count.py.
            2. Run it: python3 rome_count.py
        """,
        example = """
            for year in range(1320, 1331):
                print(year, "= AUC", year + 753)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Guilds",
        title = "The guilds of Florence, seven greater and fourteen lesser",
        originalTitle = "Arti maggiori and arti minori",
        author = "The guilds of Florence",
        year = 1320,
        summary = "Florence's guilds were divided into seven greater arts and fourteen lesser arts, and a short program counts the greater arts and adds the two groups.",
        details = "The division into greater and lesser arts was the usual form of the Florentine guild system in the period, and the example prints the count of each group and the total of twenty-one guilds. The year 1320 is a round date within the period when the division was in force.",
        instructions = """
            1. Save the example as guilds.py.
            2. Run it: python3 guilds.py
        """,
        example = """
            GREATER_ARTS = ["Judges and Notaries", "Wool", "Silk", "Calimala", "Money-changers", "Doctors and Apothecaries", "Furriers"]
            LESSER_ARTS = 14
            print("greater arts", len(GREATER_ARTS))
            print("lesser arts", LESSER_ARTS)
            print("guilds in all", len(GREATER_ARTS) + LESSER_ARTS)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Schools",
        title = "The seven liberal arts, counted as trivium and quadrivium",
        originalTitle = "Septem artes liberales",
        author = "The schools of the Latin West",
        year = 1323,
        summary = "The seven liberal arts were divided into the three arts of the trivium and the four of the quadrivium, and a short program counts them.",
        details = "Logic, one of the three arts of the trivium, was the subject of Ockham's Summa logicae, dated about 1323. The count of seven is the usual division in the schools of the period.",
        instructions = """
            1. Save the example as arts.py.
            2. Run it: python3 arts.py
        """,
        example = """
            TRIVIUM = ["grammar", "logic", "rhetoric"]
            QUADRIVIUM = ["arithmetic", "geometry", "music", "astronomy"]
            print("trivium", len(TRIVIUM), "arts")
            print("quadrivium", len(QUADRIVIUM), "arts")
            print("liberal arts", len(TRIVIUM) + len(QUADRIVIUM))
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Lives",
        title = "Dante's age at his death in 1321",
        originalTitle = "Age at death",
        author = "Dante Alighieri",
        year = 1321,
        summary = "Dante was born in 1265 and died at Ravenna in 1321, so a short program finds his age at death as 56 years.",
        details = "Dante died on 14 September 1321 at Ravenna, and his birth is usually dated to May or June 1265, which gives an age of 56 years at his death. The example uses the years only.",
        instructions = """
            1. Save the example as dante_age.py.
            2. Run it: python3 dante_age.py
        """,
        example = """
            BIRTH = 1265   # Dante's birth year, usually given as 1265
            DEATH = 1321   # Dante's death at Ravenna, in September 1321
            print("born", BIRTH)
            print("died", DEATH)
            print("age at death", DEATH - BIRTH)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.EXAMPLE,
        section = "Numerals",
        title = "The gematria of the name Levi",
        originalTitle = "Gematria",
        author = "Hebrew scribes",
        year = 1321,
        summary = "Hebrew letters carry numerical values, and a short program adds the letters of the name Levi to give its gematria value of 46.",
        details = "The name Levi is written with lamed, vav and yod, which carry the values 30, 6 and 10. The example prints each letter with its value, so the total can be checked by hand, and the name is that of Levi ben Gerson.",
        instructions = """
            1. Save the example as gematria.py.
            2. Run it: python3 gematria.py
        """,
        example = """
            LETTERS = [("lamed", 30), ("vav", 6), ("yod", 10)]   # the three letters of Levi
            total = 0
            for name, value in LETTERS:
                print(name, value)
                total += value
            print("total", total)
        """,
    ),

)
