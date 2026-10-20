package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One project of 500 to 510: the spans of four dynasties of the 500s, counted and ranked by a short program. */
val db154Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.PROJECT,
        section = "Chinese",
        title = "How long four dynasties of the 500s lasted: Liang, Chen, Northern Qi and Northern Zhou, counted in code",
        originalTitle = "Liang shu, Chen shu, Bei Qi shu, Bei Zhou shu",
        author = "Official histories of the four dynasties",
        year = 502,
        summary = "A short program takes the founding and ending years of four dynasties of the 500s and prints how long each one lasted, with the total and the longest.",
        details = "The dates are the usual founding and ending years, taken from the official histories of each dynasty, and the program subtracts the start from the end. The Liang, founded in 502, is the longest of the four, and the record takes its year from that founding.",
        instructions = """
            1. Save the example as dynasty_spans.py.
            2. Run it: python3 dynasty_spans.py
        """,
        example = """
            DYNASTIES = [
                ("Liang", 502, 557),
                ("Chen", 557, 589),
                ("Northern Qi", 550, 577),
                ("Northern Zhou", 557, 581),
            ]

            spans = [(name, end - start) for name, start, end in DYNASTIES]
            for name, start, end in DYNASTIES:
                print(f"{name} {start}-{end}: {end - start} years")
            print("total:", sum(years for _, years in spans), "years")
            longest = max(spans, key=lambda item: item[1])
            print("longest:", longest[0], longest[1], "years")
        """,
    ),

)
