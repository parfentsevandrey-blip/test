package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guide of 1119: how a sailor picks a guide by the weather, in Zhu Yu's account of sea travel. */
val db93Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.GUIDE,
        section = "Navigation",
        title = "How a sailor picks a guide by the weather, in Zhu Yu's account",
        originalTitle = "Pingzhou Ketan",
        author = "Zhu Yu",
        year = 1119,
        summary = "Zhu Yu's account of sea travel gives a rule for choosing the guide by the weather, and a short program applies that rule to four cases of sky.",
        details = "As the account is usually summarized, the sun guides a ship by day, the stars guide it by night, and in dark or overcast weather the magnetized needle is consulted. The example applies that rule to four cases and prints the guide chosen for each.",
        instructions = """
            1. Save the example as navigator.py.
            2. Run it: python3 navigator.py
        """,
        example = """
            def guide(daytime, overcast):
                if overcast:
                    return "magnetic needle"
                if daytime:
                    return "sun"
                return "stars"

            CASES = [
                ("clear day", True, False),
                ("overcast day", True, True),
                ("clear night", False, False),
                ("overcast night", False, True),
            ]
            for label, daytime, overcast in CASES:
                print(f"{label}: steer by the {guide(daytime, overcast)}")
        """,
    ),

)
