package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 379: the rule that every monk takes the surname Shi, written out as code and placed at the year Dao'an was moved to Chang'an. */
val db167Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 167,
        kind = Kind.GUIDE,
        section = "Naming",
        title = "How every monk takes the surname Shi, written as a naming rule in code",
        originalTitle = "Shi",
        author = "Dao'an",
        year = 379,
        summary = "The rule that every Buddhist monk takes the surname Shi is credited to Dao'an, and a short program applies it to a list of given names.",
        details = "The sources do not give a date for this rule; 379 is the year Dao'an was taken from Xiangyang to Chang'an when Former Qin forces seized the city, the anchor placed here and not the date of the rule. The example applies the rule to three given names, sorts the results and prints them, so the output is fixed.",
        instructions = """
            1. Save the example as shi_names.py.
            2. Run it: python3 shi_names.py
        """,
        example = """
            def shi_name(given):
                # The surname Shi is given to every monk, whatever his given name.
                return "Shi " + given

            names = ["Sengzhao", "Daoan", "Huiyuan"]
            for name in sorted(shi_name(given) for given in names):
                print(name)
            print("monks named:", len(names))
        """,
        rare = true,
    ),

)
