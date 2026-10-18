package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line of arithmetic each, for three pairs of dated events of 930 to 940: the Althing and the Nanjing capital, the Tosa Diary and the Huajianji, and the Wamyo Ruijusho and the Southern Tang. */
val db111Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.FLEX,
        section = "Iceland",
        title = "Years from the Althing's traditional start to the Liao southern capital",
        originalTitle = "Years from the Althing's traditional start to the Liao southern capital",
        author = "The chieftains of the Althing and Taizong of Liao",
        year = 938,
        summary = "A one-line calculation counts the years from the traditional founding of the Althing in 930 to the making of Nanjing, the Liao southern capital, in 938.",
        details = "The founding year of 930 is the traditional date of the Althing, and the upgrade of Youzhou to Nanjing in 938 is the common reference date. The calculation counts 8 years between them, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as althing_nanjing.py.
            2. Run it: python3 althing_nanjing.py
        """,
        example = """
            althing_start = 930     # traditional founding of the Althing
            nanjing = 938           # Youzhou made the Southern Capital Nanjing
            print("years from the Althing to the Nanjing capital:", nanjing - althing_start)
        """,
    ),

    entry(
        databaseId = 111,
        kind = Kind.FLEX,
        section = "Japan",
        title = "Years from the Tosa Diary to the Huajianji preface",
        originalTitle = "Years from the Tosa Diary to the Huajianji preface",
        author = "Ki no Tsurayuki and Zhao Chongzuo",
        year = 940,
        summary = "A one-line calculation counts the years from the Tosa Diary of about 935 to the Huajianji preface of 940.",
        details = "The Tosa Diary is dated about 935 and the Huajianji preface to 940, as commonly stated. The calculation counts 5 years between them, and the run tests the arithmetic rather than the dates themselves.",
        instructions = """
            1. Save the example as diary_lyrics.py.
            2. Run it: python3 diary_lyrics.py
        """,
        example = """
            tosa_diary = 935        # Ki no Tsurayuki's Tosa Diary, about 935
            huajianji = 940         # Zhao Chongzuo's preface, 940
            print("years from the Tosa Diary to the Huajianji preface:", huajianji - tosa_diary)
        """,
    ),

    entry(
        databaseId = 111,
        kind = Kind.FLEX,
        section = "China",
        title = "Years from the Wamyo Ruijusho to the founding of the Southern Tang",
        originalTitle = "Years from the Wamyo Ruijusho to the founding of the Southern Tang",
        author = "Minamoto no Shitago and Li Bian",
        year = 937,
        summary = "A one-line calculation counts the years from the start of the Wamyo Ruijusho in 934 to the founding of the Southern Tang in 937.",
        details = "The compilation of the Wamyo Ruijusho is dated 934 in the year used here, and the Southern Tang is founded in 937, both as commonly stated. The calculation counts 3 years between them, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as dictionary_tang.py.
            2. Run it: python3 dictionary_tang.py
        """,
        example = """
            wamyo_start = 934       # compilation of the Wamyo Ruijusho begun
            southern_tang = 937     # Li Bian founds the Southern Tang
            print("years from the Wamyo Ruijusho to the Southern Tang:", southern_tang - wamyo_start)
        """,
    ),

)
