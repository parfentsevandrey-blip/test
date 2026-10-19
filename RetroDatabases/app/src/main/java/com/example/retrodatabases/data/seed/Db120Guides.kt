package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 840 to 850, a placed record: the isnad test of a chain of narrators, placed at 850, the year the mihna ended. */
val db120Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.GUIDE,
        section = "Hadith",
        title = "How a chain of narrators is checked, pair by pair, against their lifespans",
        originalTitle = "Isnad",
        author = "Hadith critics",
        year = 850,
        summary = "A chain of narrators, the isnad, is checked link by link: a short program applies the plausibility test that the two narrators' lifespans must overlap for one to have heard the other.",
        details = "This is a placed record: the sources do not date the method, and 850 is the year of the anchor, the end of the mihna inquisition under al-Mutawakkil, which had put scholars of hadith under test. The narrators A to D and their dates are invented for the example, which prints the test for a chain that passes and for one that fails.",
        instructions = """
            1. Save the example as isnad.py.
            2. Run it: python3 isnad.py
        """,
        example = """
            NARRATORS = {
                "A": (760, 822),
                "B": (735, 795),
                "C": (700, 740),
                "D": (800, 860),
            }

            def could_have_heard(listener, teacher):
                birth_l, death_l = NARRATORS[listener]
                birth_t, death_t = NARRATORS[teacher]
                return max(birth_l, birth_t) <= min(death_l, death_t)

            CHAIN = ["A", "B", "C"]
            BROKEN = ["D", "C"]

            for chain in (CHAIN, BROKEN):
                for listener, teacher in zip(chain, chain[1:]):
                    print(listener, "from", teacher, "possible:", could_have_heard(listener, teacher))
        """,
        rare = true,
    ),

)
