package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1080 to 1090: a right-triangle step for the chord of a bow, from one modern reading of Shen Kuo's arc passage. */
val db96Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 96,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How a bow's chord follows from its diameter and sagitta by a right triangle",
        originalTitle = "Mengxi bitan",
        author = "Shen Kuo",
        year = 1088,
        summary = "A bow's chord follows from its diameter and sagitta by a right triangle, a step that one modern reading places in Shen Kuo's Dream Pool Essays.",
        details = "Half the diameter minus the sagitta gives the distance from the centre to the chord, and the Pythagorean theorem then gives half the chord. The arc passage of the book is partly unclear, so the link to Shen Kuo rests on one reading of it.",
        rare = true,
        instructions = """
            1. Save the example as bow_chord.py.
            2. Run it: python3 bow_chord.py
        """,
        example = """
            import math

            def chord(diameter, sagitta):
                half = diameter / 2
                leg = half - sagitta                          # from the centre to the chord
                return 2 * math.sqrt(half * half - leg * leg)

            for diameter, sagitta in [(10, 1), (10, 2), (10, 5), (26, 1)]:
                print(f"diameter {diameter}, sagitta {sagitta}: chord {chord(diameter, sagitta):g}")
        """,
    ),

)
