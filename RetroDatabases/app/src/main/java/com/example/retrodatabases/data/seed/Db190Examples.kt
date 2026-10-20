package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 140 to 150: one sowing move on a twelve-pit mancala board, with its seeds counted by a short program. */
val db190Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.EXAMPLE,
        section = "Games",
        title = "One sowing move on a twelve-pit mancala board, computed in code",
        originalTitle = "Mancala",
        author = "No single author",
        year = 146,
        summary = "A twelve-pit mancala board starts with four seeds in each pit, and a sowing move drops the seeds of one pit into the pits that follow, which a short program computes.",
        details = "The sources do not date the sowing rule; 146 is the year of the anchor, the accession of Emperor Huan of Han, not of the making. The program sows the four seeds of the first pit into the next four pits and prints the board, and it adds the seeds so that the total of 48 can be checked by hand.",
        rare = true,
        instructions = """
            1. Save the example as mancala_sow.py.
            2. Run it: python3 mancala_sow.py
        """,
        example = """
            pits = [4] * 12
            start = 0
            seeds = pits[start]
            pits[start] = 0
            position = start
            for _ in range(seeds):
                position = (position + 1) % 12
                pits[position] += 1
            print("pits after one sowing:", pits)
            print("seeds on the board:", sum(pits))
        """,
    ),

)
