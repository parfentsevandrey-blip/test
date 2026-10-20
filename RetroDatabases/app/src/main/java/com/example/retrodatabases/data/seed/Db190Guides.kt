package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 140 to 150: breath counting from one to ten and back, as early Chinese meditation texts teach it, dated to An Shigao's Luoyang years. */
val db190Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.GUIDE,
        section = "Meditation",
        title = "How breath counting runs from one to ten and starts again in early Chinese meditation",
        originalTitle = "Anban shouyi jing",
        author = "Traditionally An Shigao",
        year = 148,
        summary = "Breath counting runs from one to ten and then starts again, a practice that early Chinese meditation texts teach, and a short program prints the count for chosen breaths.",
        details = "The text traditionally credited to An Shigao, the Anban shouyi jing, teaches counting the breath, and that attribution is traditional rather than proven. The year 148 is the one accounts give for An Shigao's move to Luoyang, and the text itself carries no firm date.",
        rare = true,
        instructions = """
            1. Save the example as breath_count.py.
            2. Run it: python3 breath_count.py
        """,
        example = """
            def count_for(breath):
                # The count runs from one to ten and then starts again at one.
                return (breath - 1) % 10 + 1

            for breath in [9, 10, 11, 20, 21, 30]:
                print("breath", breath, "counts", count_for(breath))
        """,
    ),

)
