package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 310 to 320: how the strength of a Roman legion is counted from its cohorts and centuries, using the textbook arrangement. */
val db173Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.GUIDE,
        section = "Army",
        title = "How the strength of a Roman legion is counted from its cohorts and centuries",
        originalTitle = "Legio",
        author = "Roman military writers",
        year = 310,
        summary = "The textbook Roman legion had ten cohorts of six centuries of eighty men, and a short program adds them up to its full strength.",
        details = "The textbook arrangement is older than the fourth century, and the sources do not date the legion's strength in the 310s, so 310 is only the placed year of this record. The first cohort was in practice double-sized, which the program ignores, so the total is the textbook figure of 4,800 men.",
        instructions = """
            1. Save the example as legion.py.
            2. Run it: python3 legion.py
        """,
        example = """
            COHORTS = 10
            CENTURIES_PER_COHORT = 6
            MEN_PER_CENTURY = 80
            centuries = COHORTS * CENTURIES_PER_COHORT
            men = centuries * MEN_PER_CENTURY
            print("centuries in the legion:", centuries)
            print("men in one cohort:", CENTURIES_PER_COHORT * MEN_PER_CENTURY)
            print("men in the legion:", men)
        """,
    ),

)
