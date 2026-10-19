package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 710 to 720: the rules of the regulated eight-line Tang poem, checked by a short program for line length and for rhyme on the even lines. */
val db133Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How a regulated eight-line Tang poem is tested for line length and for rhyme on its even lines",
        originalTitle = "Lushi, the regulated verse",
        author = "Shen Quanqi and Song Zhiwen (credited)",
        year = 710,
        summary = "A regulated Tang poem has eight lines of five or seven characters that rhyme on the even lines, and a short program checks both rules.",
        details = "Shen Quanqi (656 to 714) and Song Zhiwen (about 660 to 712) are credited with fixing the form, and accounts differ on when its rules were settled, from the late seventh century to the Jinglong era of 707 to 710, so 710 is the year used here. The labels in the example stand for line lengths and rhyme groups, not for real lines of verse, and the program shows that a rhyme broken on line six is caught.",
        rare = true,
        instructions = """
            1. Save the example as lushi_check.py.
            2. Run it: python3 lushi_check.py
        """,
        example = """
            LINES = 8

            def regulated(poem):
                lengths = {length for length, rhyme in poem}
                rhymes = {rhyme for number, (length, rhyme) in enumerate(poem, start=1) if number % 2 == 0}
                return len(poem) == LINES and len(lengths) == 1 and lengths <= {5, 7} and len(rhymes) == 1

            FIVE = [(5, "B"), (5, "A"), (5, "C"), (5, "A"), (5, "D"), (5, "A"), (5, "E"), (5, "A")]
            SEVEN = [(7, "B"), (7, "A"), (7, "C"), (7, "A"), (7, "D"), (7, "A"), (7, "E"), (7, "A")]
            BROKEN = [(5, "B"), (5, "A"), (5, "C"), (5, "A"), (5, "D"), (5, "F"), (5, "E"), (5, "A")]
            MIXED = [(5, "B"), (7, "A"), (5, "C"), (5, "A"), (5, "D"), (5, "A"), (5, "E"), (5, "A")]
            SHORT = [(5, "B"), (5, "A"), (5, "C"), (5, "A"), (5, "D"), (5, "A"), (5, "E")]

            print("five-character poem regulated:", regulated(FIVE))
            print("seven-character poem regulated:", regulated(SEVEN))
            print("rhyme broken on line six:", regulated(BROKEN))
            print("mixed line lengths:", regulated(MIXED))
            print("seven lines only:", regulated(SHORT))
        """,
    ),

)
