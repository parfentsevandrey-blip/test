package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A project of 924: the reign lengths of three English kings of the early tenth century, summed and ranked by a short program. */
val db112Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.PROJECT,
        section = "England",
        title = "Reign lengths of Edward the Elder, Athelstan and Edmund I of England, summed and ranked",
        originalTitle = "Reign lengths of Edward the Elder, Athelstan and Edmund I of England, summed and ranked",
        author = "Edward the Elder, Athelstan and Edmund I",
        year = 924,
        summary = "A short program sums the reign lengths of three English kings, 47 years in all, and ranks them from the longest to the shortest.",
        details = "Edward the Elder reigned from 899 to 924, Athelstan from 924 to 939 and Edmund I from 939 to 946, by the usual year-level dates. The year 924 is the start of Athelstan's reign, and the program counts whole years only.",
        instructions = """
            1. Save the example as english_reigns.py.
            2. Run it: python3 english_reigns.py
        """,
        example = """
            reigns = [
                ("Edward the Elder", 899, 924),
                ("Athelstan", 924, 939),
                ("Edmund I", 939, 946),
            ]

            lengths = sorted(((end - start, name) for name, start, end in reigns), reverse=True)
            print("rulers:", len(reigns))
            print("total years:", sum(length for length, _ in lengths))
            print("longest:", lengths[0][1], lengths[0][0], "years")
            print("shortest:", lengths[-1][1], lengths[-1][0], "years")
        """,
    ),

)
