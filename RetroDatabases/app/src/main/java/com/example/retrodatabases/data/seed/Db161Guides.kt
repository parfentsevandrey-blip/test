package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 434: how Vincent of Lerins's three tests sort a belief, worked out as a short program. */
val db161Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.GUIDE,
        section = "Doctrine",
        title = "How Vincent of Lerins's three tests sort a belief",
        originalTitle = "Commonitorium",
        author = "Vincent of Lerins",
        year = 434,
        summary = "Vincent of Lerins accepted a belief only if it was held everywhere, always and by all, and a short program applies these three tests to illustrative beliefs.",
        details = "The three tests come from the Commonitorium of 434, and the example runs them on four beliefs whose flags are invented for the test. The output says nothing about any real doctrine, and it shows how the rule keeps a belief only when it passes all three tests.",
        instructions = """
            1. Save the example as tests.py.
            2. Run it: python3 tests.py
        """,
        example = """
            # Vincent's three tests: held everywhere, always and by all. The flags are illustrative.
            beliefs = [
                ("teaching kept in every church", True, True, True),
                ("custom of one province", False, True, True),
                ("opinion of a single teacher", False, False, False),
                ("doctrine that only most accept", True, True, False),
            ]
            for name, everywhere, always, by_all in beliefs:
                verdict = "kept" if everywhere and always and by_all else "rejected"
                print(f"{name}: {verdict}")
            kept = sum(1 for _, everywhere, always, by_all in beliefs if everywhere and always and by_all)
            print("kept:", kept, "of", len(beliefs))
        """,
    ),

)
