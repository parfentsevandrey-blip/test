package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 690 to 700: how the degree of kinship is counted by the Roman civil method, one step up and one step down, worked out by a program. */
val db135Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.GUIDE,
        section = "Byzantine",
        title = "How the degree of kinship is counted by the Roman civil method, one step up and one step down, worked out by a program",
        originalTitle = "gradus cognationis",
        author = "Roman jurists and the Byzantine courts",
        year = 692,
        summary = "The Roman civil count of kinship adds the generations from each of two people up to their common ancestor, so siblings are in the second degree and first cousins in the fourth.",
        details = "The Roman civil count, used in the law that the Byzantine courts followed in the 690s, adds one degree for each generation up to the common ancestor and one for each generation down. The year 692, that of the Quinisext Council, is only the anchor of this placed record and not the date of the method.",
        rare = true,
        instructions = """
            1. Save the example as kinship_degrees.py.
            2. Run it: python3 kinship_degrees.py
        """,
        example = """
            def roman_degree(up_a, up_b):
                # one degree for each generation up from each person to the common ancestor
                return up_a + up_b

            pairs = [
                ("siblings", 1, 1),
                ("uncle and nephew", 1, 2),
                ("first cousins", 2, 2),
                ("second cousins", 3, 3),
            ]
            for name, up_a, up_b in pairs:
                print(name, "degree", roman_degree(up_a, up_b))
        """,
    ),

)
