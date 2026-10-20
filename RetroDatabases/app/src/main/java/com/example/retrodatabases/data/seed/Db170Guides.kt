package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 340: how analysis and synthesis solve a problem, as Pappus describes them in his Collection of about 340. */
val db170Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.GUIDE,
        section = "Method",
        title = "How analysis and synthesis solve a problem, as Pappus sets them out",
        originalTitle = "Analysis and synthesis",
        author = "Pappus of Alexandria and the geometers before him",
        year = 340,
        summary = "Pappus describes two ways of solving a problem: analysis works backward from the sought result to what is given, and synthesis runs the same steps forward again.",
        details = "Pappus sets out the two methods in his Collection of about 340, and accounts differ on how much of the method came from earlier geometers such as Euclid. The example uses an illustrative chain of claims, with letters chosen for the purpose rather than Pappus's own propositions.",
        instructions = """
            1. Save the example as analysis.py.
            2. Run it: python3 analysis.py
        """,
        example = """
            NEEDS = {"G": "F", "F": "E", "E": "B"}   # to prove each claim, it is enough to prove the one it names
            GIVEN = {"B", "C"}                       # claims already known
            path = []
            claim = "G"                              # the sought claim
            while claim not in GIVEN:
                path.append(claim)
                claim = NEEDS[claim]
            path.append(claim)
            print("analysis:", " -> ".join(path))
            print("synthesis:", " -> ".join(reversed(path)))
            print("steps of analysis:", len(path) - 1)
        """,
        rare = true,
    ),

)
