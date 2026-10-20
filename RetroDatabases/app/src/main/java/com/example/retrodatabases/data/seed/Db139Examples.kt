package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One example of 650 to 660: the Liber Iudiciorum of the Visigoths, issued in 654, with the years to its revisions counted in code. */
val db139Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.EXAMPLE,
        section = "Visigothic",
        title = "The Liber Iudiciorum of 654 and its revisions of 681 and 693, with the years between them counted in code",
        originalTitle = "Liber Iudiciorum",
        author = "Recceswinth",
        year = 654,
        summary = "The Visigothic law code, the Liber Iudiciorum, was issued in 654 under King Recceswinth, and a short program counts the years to its revisions of 681 and 693.",
        details = "The code is dated to 654 under Recceswinth, and reference works give revisions in 681 and 693, although accounts differ on the later revision, since one account mentions only the 681 update. The program counts the years from the issue to each revision as they are commonly dated.",
        instructions = """
            1. Save the example as liber_revisions.py.
            2. Run it: python3 liber_revisions.py
        """,
        example = """
            ISSUE = 654
            REVISIONS = [681, 693]
            print("revisions counted:", len(REVISIONS))
            print("years from the issue to each revision:", [year - ISSUE for year in REVISIONS])
            print("years from the first revision to the second:", REVISIONS[1] - REVISIONS[0])
        """,
    ),

)
