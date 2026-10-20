package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 220 to 230: Zhao Shuang's diagram that proves the gougu rule with four triangles and a small square, checked by a program. */
val db182Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "How Zhao Shuang's diagram proves the gougu rule with rearranged squares",
        originalTitle = "Zhoubi suanjing zhu",
        author = "Zhao Shuang",
        year = 222,
        summary = "Zhao Shuang's diagram fills the square on the hypotenuse with four right triangles and a small square, and a short program checks the rearranged areas for three triples.",
        details = "Zhao Shuang's commentary belongs to the early third century, and this record uses 222, though some accounts give about 220. The Zhoubi suanjing it explains claims an older origin, but its extant text is dated no earlier than the Eastern Han, so the gougu rule itself is not dated here. The link between the diagram and Zhao Shuang is made in a sixth-century commentary.",
        instructions = """
            1. Save the example as zhao_gougu.py.
            2. Run it: python3 zhao_gougu.py
        """,
        example = """
            # Zhao Shuang's diagram: a square on the hypotenuse holds four right triangles
            # around a small square whose side is the difference of the two legs.
            TRIPLES = [(3, 4, 5), (5, 12, 13), (8, 15, 17)]
            for a, b, c in TRIPLES:
                outer = c * c
                triangles = 4 * (a * b // 2)
                inner = (b - a) ** 2
                holds = outer == triangles + inner == a * a + b * b
                print(f"legs {a} and {b}, hypotenuse {c}: {outer} = {triangles} + {inner}, holds: {holds}")
            print("triples checked:", len(TRIPLES))
        """,
        rare = true,
    ),

)
