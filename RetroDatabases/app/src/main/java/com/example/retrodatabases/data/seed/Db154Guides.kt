package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 500 to 510: how a ratio is named by its species, as Nicomachus sets them out, worked out by a short program. */
val db154Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.GUIDE,
        section = "Greek",
        title = "How a ratio is named by its species, multiple, superparticular or superpartient, as Nicomachus names them",
        originalTitle = "Introductio arithmetica",
        author = "Nicomachus of Gerasa, with Boethius",
        year = 500,
        summary = "A ratio of two whole numbers falls into one of Nicomachus's species, and a short program names six ratios in that way.",
        details = "Nicomachus of Gerasa set out the species of ratio in his Introduction to Arithmetic, and Boethius carried the same names into Latin. The rule divides the larger number by the smaller and reads the quotient and the remainder, so 8:3 has quotient 2 and remainder 2 and is a multiple superpartient ratio.",
        rare = true,
        instructions = """
            1. Save the example as ratio_species.py.
            2. Run it: python3 ratio_species.py
        """,
        example = """
            def species(a, b):
                # Nicomachus's species for the ratio a:b, with a at least b
                quotient, remainder = divmod(a, b)
                if remainder == 0:
                    return "multiplex"
                if quotient == 1:
                    return "superparticularis" if remainder == 1 else "superpartiens"
                return "multiplex superparticularis" if remainder == 1 else "multiplex superpartiens"

            for a, b in [(3, 2), (4, 3), (5, 3), (6, 3), (7, 2), (8, 3)]:
                print(f"{a}:{b} is {species(a, b)}")
        """,
    ),

)
