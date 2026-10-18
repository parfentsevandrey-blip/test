package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1190: the chapters of Maimonides's Guide added up by part, and seven dinars set against ten dirhams in weight. */
val db85Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.EXAMPLE,
        section = "Works",
        title = "The chapters of Maimonides's Guide, counted in its three parts",
        originalTitle = "Dalalat al-ha'irin",
        author = "Moses Maimonides",
        year = 1190,
        summary = "The Guide for the Perplexed has seventy-six chapters in its first part, forty-eight in its second and fifty-four in its third, and a short program adds them up.",
        details = "The three parts are counted separately and make 178 chapters in all, with the introduction set apart from the numbered parts. The counts are the usual ones for the standard editions and translations of the Guide.",
        instructions = """
            1. Save the example as guide_chapters.py.
            2. Run it: python3 guide_chapters.py
        """,
        example = """
            PARTS = {"first part": 76, "second part": 48, "third part": 54}
            for name, chapters in PARTS.items():
                print(name, chapters)
            print("chapters in all:", sum(PARTS.values()))
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "Seven gold dinars and ten silver dirhams of equal weight",
        originalTitle = "Dinar and dirham",
        author = "Islamic weight standards",
        year = 1190,
        summary = "Under the weight standard of Islamic law seven gold dinars weigh the same as ten silver dirhams, and a short program checks the match in milligrams.",
        details = "The weights are the standard figures set by Islamic jurists: a dinar of 4.25 grams, and a dirham of seven tenths of that, which is 2.975 grams. The program works in whole milligrams so that the two sums can be compared exactly.",
        instructions = """
            1. Save the example as dinar_dirham.py.
            2. Run it: python3 dinar_dirham.py
        """,
        example = """
            DINAR_MG = 4250
            DIRHAM_MG = 2975
            gold = 7 * DINAR_MG
            silver = 10 * DIRHAM_MG
            print("seven dinars:", gold, "mg")
            print("ten dirhams:", silver, "mg")
            print("equal:", gold == silver)
        """,
    ),

)
