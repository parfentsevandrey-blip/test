package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1100: how canon law counts degrees of kinship, by adding the generations up to a common ancestor. */
val db94Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.GUIDE,
        section = "Marriage",
        title = "How canon law counts degrees of kinship from a common ancestor",
        originalTitle = "Canonical method of counting degrees",
        author = "Canon lawyers of the Latin Church",
        year = 1100,
        summary = "The canonical method counts a degree of kinship by adding the generations from each person up to their common ancestor, and a short program gives the degree for five pairs of relatives.",
        details = "Under this method siblings are in the second degree, an uncle and niece in the third and first cousins in the fourth. Before the Fourth Lateran Council of 1215, which reduced the forbidden degrees from seven to four, the church counted these degrees to the seventh.",
        instructions = """
            1. Save the example as kinship.py.
            2. Run it: python3 kinship.py
        """,
        example = """
            # generations from each person up to the common ancestor
            PAIRS = [
                ("parent and child", 0, 1),
                ("siblings", 1, 1),
                ("uncle and niece", 1, 2),
                ("first cousins", 2, 2),
                ("second cousins", 3, 3),
            ]
            for name, up_a, up_b in PAIRS:
                print(name, "degree", up_a + up_b)
        """,
    ),

)
