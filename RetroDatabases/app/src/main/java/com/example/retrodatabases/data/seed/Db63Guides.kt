package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1410 to 1420: how many votes a two-thirds majority needed for the electors of Constance in 1417. */
val db63Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.GUIDE,
        section = "Votes",
        title = "How many votes a two-thirds majority needed at Constance in 1417",
        originalTitle = "Concilium Lateranense III",
        author = "Pope Alexander III and the Third Lateran Council",
        year = 1417,
        summary = "Two thirds of an electorate must be rounded up to a whole number of votes, and a short program counts the votes needed for the 52 or 53 electors that accounts give for Martin V's election.",
        details = "The two-thirds rule for papal elections was set by the Third Lateran Council of 1179, and Martin V was elected on 11 November 1417 by an electorate that accounts give as 52 or 53. One account counts 22 cardinals and so 52 electors, while another counts 23 cardinals and so 53, and the example applies the rule to both counts without reconstructing the ballots of 1417.",
        instructions = """
            1. Save the example as two_thirds.py.
            2. Run it: python3 two_thirds.py
        """,
        example = """
            import math

            def votes_needed(electors):
                # Two thirds of the electors, rounded up to a whole vote.
                return math.ceil(2 * electors / 3)

            for electors in (52, 53):
                print(f"electors {electors}: votes needed {votes_needed(electors)}")
        """,
    ),

)
