package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 640 to 650: how a split vote among Umar's six electors of 644 is settled, worked out by a short program. */
val db140Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.GUIDE,
        section = "Arabic",
        title = "How a split vote among Umar's six electors of 644 is settled, worked out by a short program",
        originalTitle = "al-shura",
        author = "Umar ibn al-Khattab",
        year = 644,
        summary = "A split vote among six electors is settled by counting the ballots and, in a tie, by the choice of the arbiter, Abd al-Rahman ibn Awf.",
        details = "The tie rule is the one most often reported for Umar's council, and the two ballot sets in the example are illustrations, not the recorded votes. Accounts differ on the exact wording of Umar's instruction, so the rule is given here in its most common form.",
        instructions = """
            1. Save the example as umar_council.py.
            2. Run it: python3 umar_council.py
        """,
        example = """
            ARBITER = "Abd al-Rahman"

            SAMPLE_A = [
                ("Ali", "Ali"),
                ("Uthman", "Uthman"),
                ("Talha", "Ali"),
                ("Zubayr", "Uthman"),
                ("Sa'd", "Ali"),
                ("Abd al-Rahman", "Uthman"),
            ]
            SAMPLE_B = [
                ("Ali", "Uthman"),
                ("Uthman", "Uthman"),
                ("Talha", "Uthman"),
                ("Zubayr", "Ali"),
                ("Sa'd", "Uthman"),
                ("Abd al-Rahman", "Ali"),
            ]

            def settle(ballots):
                counts = {}
                for voter, choice in ballots:
                    counts[choice] = counts.get(choice, 0) + 1
                top = max(counts.values())
                leaders = [name for name, n in counts.items() if n == top]
                if len(leaders) == 1:
                    return leaders[0], "majority of " + str(top)
                return dict(ballots)[ARBITER], "tie settled by the arbiter"

            for label, ballots in (("sample A", SAMPLE_A), ("sample B", SAMPLE_B)):
                winner, how = settle(ballots)
                print(label + ":", winner, "via", how)
        """,
    ),

)
