package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1100: how a laisse of an Old French epic is tested for its assonance, the one vowel that closes its lines. */
val db95Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.GUIDE,
        section = "Verse",
        title = "How a laisse of an Old French epic is tested for its assonance",
        originalTitle = "Laisse assonancee",
        author = "Anonymous poets of the chansons de geste",
        year = 1100,
        summary = "A laisse is a run of lines closed by one vowel sound, and a short program tests two made-up laisses to see whether every line ends on the same vowel.",
        details = "Epics of about 1100, such as the Chanson de Roland, group their lines in laisses in this way. The example uses made-up line endings to show the test, not lines of the poem.",
        rare = true,
        instructions = """
            1. Save the example as laisse.py.
            2. Run it: python3 laisse.py
        """,
        example = """
            # each laisse lists the final vowel of every line; it holds when all the vowels match
            LAISSES = {
                "first laisse": ["a", "a", "a", "a", "a"],
                "second laisse": ["e", "e", "i", "e"],
            }
            for name, endings in LAISSES.items():
                vowel = endings[0]
                breaks = [i + 1 for i, ending in enumerate(endings) if ending != vowel]
                if not breaks:
                    print(name, len(endings), "lines, assonance on", vowel)
                else:
                    print(name, len(endings), "lines, broken at line", breaks[0], "by", endings[breaks[0] - 1])
        """,
    ),

)
