package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 480 to 490: how a household's equal-field grant of land is counted in mu under the rule of 485. */
val db156Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.GUIDE,
        section = "Land",
        title = "How a household's equal-field grant of land is worked out in mu, by the rule of the Northern Wei",
        originalTitle = "Juntian",
        author = "The Northern Wei court",
        year = 485,
        summary = "Under the equal-field rule of 485, a man aged fifteen or more received 40 mu and a woman received 20 mu, and a short program adds up the grant for one household.",
        details = "The decree is dated to the ninth year of the Taihe era, and a plot went back to the state when its holder died. The example counts a household of four people, with a boy of nine too young for land, and prints the grant for each member and the total.",
        rare = true,
        instructions = """
            1. Save the example as equal_field.py.
            2. Run it: python3 equal_field.py
        """,
        example = """
            MEN_MU = 40          # men aged fifteen and up
            WOMEN_MU = 20        # women
            ADULT_AGE = 15

            household = [("man", 40), ("man", 15), ("woman", 30), ("man", 9)]
            total = 0
            for sex, age in household:
                if sex == "man" and age >= ADULT_AGE:
                    mu = MEN_MU
                elif sex == "woman":
                    mu = WOMEN_MU
                else:
                    mu = 0
                total += mu
                print(sex, age, "receives", mu, "mu")
            print("total grant:", total, "mu")
        """,
    ),

)
