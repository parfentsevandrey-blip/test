package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 740 to 750: how a reclaimed field is written in cho and tan, with the area units of the Japanese land rules. */
val db130Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.GUIDE,
        section = "Land",
        title = "How a reclaimed field is written in cho and tan, the area units of the Japanese land rules of the 740s",
        originalTitle = "Cho and tan",
        author = "Japanese land officials",
        year = 743,
        summary = "A reclaimed field is written in cho and tan, with ten tan to the cho, and a short program turns a count of tan into cho, tan and square bu.",
        details = "The sources do not date this method to 743; the year is the anchor of the land law of that year, which dealt with reclaimed fields. The program uses the usual figure of 360 square bu to a tan, and it prints three fields as examples.",
        rare = true,
        instructions = """
            1. Save the example as tan_cho.py.
            2. Run it: python3 tan_cho.py
        """,
        example = """
            fields = [1234, 75, 9]
            for tan in fields:
                print(tan, "tan =", tan // 10, "cho", tan % 10, "tan;", tan * 360, "square bu")
        """,
    ),

)
