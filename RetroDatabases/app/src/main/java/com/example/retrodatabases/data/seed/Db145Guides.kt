package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 590 to 600: how a teacher answers each hearer's fault with its contrary, as Gregory's Pastoral Rule advises, worked out by a short program. */
val db145Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 145,
        kind = Kind.GUIDE,
        section = "Church",
        title = "How Gregory's Pastoral Rule answers each hearer's fault with its contrary, worked out by a short program",
        originalTitle = "Regula pastoralis",
        author = "Gregory the Great",
        year = 591,
        summary = "Gregory's Pastoral Rule advises a teacher to answer each hearer's fault with its contrary, and a short program pairs stand-in faults with the answers that suit them.",
        details = "The rule of matching a hearer with the word that suits him, such as hope for the timid and fear for the bold, is set out in the book, so 591 is the year of the book that states it. The example uses stand-in faults and answers, not Gregory's own list, so it shows the method and nothing more.",
        rare = true,
        instructions = """
            1. Save the example as contrary_pairs.py.
            2. Run it: python3 contrary_pairs.py
        """,
        example = """
            # Stand-in faults and the answer given to each one; these are not Gregory's own list.
            PAIRS = [("timid", "hope"), ("bold", "fear"), ("proud", "humility"), ("idle", "urgency")]

            for fault, answer in PAIRS:
                print(fault, "hearer:", answer)
            print("pairs:", len(PAIRS))
        """,
    ),

)
