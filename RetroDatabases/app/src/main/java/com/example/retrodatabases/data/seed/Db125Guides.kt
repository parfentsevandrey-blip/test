package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 790 to 800: the root and pattern method for Arabic verbs in the manner of Sibawayh's grammar, placed at 796, the year usually given for his death. */
val db125Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.GUIDE,
        section = "Arabic",
        title = "How an Arabic verb is built from a three-letter root set into the pattern fa'ala, in the manner of Sibawayh's grammar",
        originalTitle = "Al-Kitab",
        author = "Sibawayh",
        year = 796,
        summary = "Sibawayh's Kitab builds Arabic verbs from a three-letter root set into a pattern, and this guide does the same in a short program.",
        details = "The Kitab is undated, so 796, the year usually given for Sibawayh's death, is the anchor here. One account puts his death between 793 and 796, and another dates a death in Basra to 777, a claim that is disputed; the example sets the root k-t-b into fa'ala to make kataba, and into two other patterns.",
        instructions = """
            1. Save the example as root_pattern.py.
            2. Run it: python3 root_pattern.py
        """,
        example = """
            ROOT = ["k", "t", "b"]  # the three root letters, first, middle and last
            PATTERNS = [
                ("Form I", "FaCaLa"),
                ("Form II", "FaCCaLa"),
                ("Form IV", "'aFCaLa"),
            ]
            for name, pattern in PATTERNS:
                word = pattern.replace("F", ROOT[0]).replace("C", ROOT[1]).replace("L", ROOT[2])
                print(name, word)
        """,
        rare = true,
    ),

)
