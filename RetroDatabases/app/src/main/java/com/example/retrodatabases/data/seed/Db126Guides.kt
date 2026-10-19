package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 780 to 790: how al-Khalil's Kitab al-'Ayn orders its letters by articulation, from ayn at the throat to mim at the lips, written as a short program. */
val db126Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.GUIDE,
        section = "Arabic",
        title = "How al-Khalil's Kitab al-'Ayn sorts letter strings by articulation, from ayn at the throat to mim at the lips, checked by a program",
        originalTitle = "Kitab al-'Ayn",
        author = "Al-Khalil ibn Ahmad al-Farahidi (ascribed)",
        year = 786,
        summary = "The Kitab al-'Ayn orders its entries by articulation, from the throat letter ayn to the lip letter mim, and a short program sorts six letter strings by that order.",
        details = "The program keeps five letters, ayn, kaf, ta, ba and mim, in an articulation order that starts at ayn and ends at mim, as the book does for its full alphabet. The six strings are illustrative and not a list taken from the book, so the example shows the sorting method only.",
        rare = true,
        instructions = """
            1. Save the example as kitab_order.py.
            2. Run it: python3 kitab_order.py
        """,
        example = """
            ORDER = {"'": 0, "k": 1, "t": 2, "b": 3, "m": 4}
            ROOTS = ["mkb", "ktb", "'mt", "tkm", "bmk", "'kb"]
            ranked = sorted(ROOTS, key=lambda root: [ORDER[letter] for letter in root])
            print("roots sorted:", " ".join(ranked))
            print("first:", ranked[0])
            print("last:", ranked[-1])
            print("count:", len(ranked))
        """,
    ),

)
