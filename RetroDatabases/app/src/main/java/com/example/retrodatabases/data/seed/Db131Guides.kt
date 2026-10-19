package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 730 to 740: the Tang land statute of 737 that gives an adult man 100 mu, worked out in code as 80 allotted and 20 hereditary mu. */
val db131Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.GUIDE,
        section = "Land",
        title = "How the Tang land statute of 737 splits an adult man's 100 mu into 80 allotted and 20 hereditary mu, in code",
        originalTitle = "Kaiyuan ershiwu nian ling, juntian",
        author = "Tang government",
        year = 737,
        summary = "The Tang statute of the twenty-fifth year of Kaiyuan, 737, gave an adult man 100 mu of equal-field land, 80 mu to be allotted and 20 mu hereditary, and a short program works out the grant for a household.",
        details = "The allotted part went back to the state when its holder died, while the hereditary part could pass to his heirs. The total of 100 mu for an adult man was already the grant in the Wude statute of 624, and the program prints the grant for a household of three adult men.",
        rare = true,
        instructions = """
            1. Save the example as equal_field.py.
            2. Run it: python3 equal_field.py
        """,
        example = """
            ADULT_MAN_MU = 100                          # the equal-field grant for an adult man, in mu
            HEREDITARY_MU = 20                          # the part kept as hereditary land
            ALLOTTED_MU = ADULT_MAN_MU - HEREDITARY_MU  # the part returned to the state at death

            def household_grant(adult_men):
                return {
                    "allotted": ALLOTTED_MU * adult_men,
                    "hereditary": HEREDITARY_MU * adult_men,
                    "total": ADULT_MAN_MU * adult_men,
                }

            grant = household_grant(3)
            print("allotted mu for three adult men:", grant["allotted"])
            print("hereditary mu for three adult men:", grant["hereditary"])
            print("total mu for three adult men:", grant["total"])
            print("hereditary share of the grant:", HEREDITARY_MU / ADULT_MAN_MU)
        """,
    ),

)
