package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 391: how Augustine's De musica counts the time of verse feet, written out as a short program. */
val db165Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How Augustine's De musica counts the time of a verse foot, a long syllable taking two units",
        originalTitle = "De musica",
        author = "Augustine of Hippo",
        year = 391,
        summary = "Augustine's De musica treats verse by its time, and a short program counts the units of a few feet by giving each long syllable two units and each short one unit.",
        details = "Augustine wrote the treatise in stages in the late 380s and early 390s, and its rhythm theory uses the old rule that a long syllable takes twice the time of a short one. The example applies that rule to four feet and one line, with each foot written as a string of L and S, so every figure can be checked by hand.",
        instructions = """
            1. Save the example as musica_time.py.
            2. Run it: python3 musica_time.py
        """,
        example = """
            FEET = {"dactyl": "LSS", "spondee": "LL", "iamb": "SL", "pyrrhic": "SS"}
            UNITS = {"L": 2, "S": 1}   # a long syllable takes two time units, a short one takes one

            def time_of(pattern):
                return sum(UNITS[syllable] for syllable in pattern)

            for name, pattern in FEET.items():
                print(name, pattern, time_of(pattern))
            line = "LSSLSSLL"
            print("line", line, "has", time_of(line), "time units")
        """,
        rare = true,
    ),

)
