package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1050: the pennies in the English pound and mark, counted by a short program. */
val db99Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.EXAMPLE,
        section = "Money",
        title = "How many pennies made the English pound under Edward the Confessor",
        originalTitle = "Pound of account",
        author = "No single author",
        year = 1050,
        summary = "In Edward the Confessor's England the silver penny was the regular coin, and a pound of account held 240 pennies while a mark held 160, a count that a short program checks.",
        details = "The pound was reckoned as twenty shillings of twelve pennies each, and the mark as two-thirds of a pound, a system far older than the decade. The year given is the reign of Edward the Confessor, from 1042 to 1066, so it marks the period of the rule rather than its first use, and the example prints the counts with integer arithmetic.",
        rare = true,
        instructions = """
            1. Save the example as pennies.py.
            2. Run it: python3 pennies.py
        """,
        example = """
            PENCE_PER_SHILLING = 12
            SHILLINGS_PER_POUND = 20
            PENCE_PER_POUND = PENCE_PER_SHILLING * SHILLINGS_PER_POUND
            PENCE_PER_MARK = PENCE_PER_POUND * 2 // 3

            print("pence in a pound:", PENCE_PER_POUND)
            print("pence in a mark:", PENCE_PER_MARK)
            for pence in [600, 1200]:
                print(pence, "pence make", pence // PENCE_PER_POUND, "pounds and", pence % PENCE_PER_POUND, "pence")
        """,
    ),

)
