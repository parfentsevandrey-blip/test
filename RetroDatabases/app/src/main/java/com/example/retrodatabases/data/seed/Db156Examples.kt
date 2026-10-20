package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 480 to 490: square bu of three fields turned into mu, placed at 485 for the equal-field decree that measured land in mu. */
val db156Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.EXAMPLE,
        section = "Fields",
        title = "The square bu of three fields in mu, by the land rule of the Han and later periods",
        originalTitle = "Mu and bu",
        author = "No single author",
        year = 485,
        summary = "A field measured in square bu converts to mu at 240 square bu to the mu, and a short program turns three fields into mu with the square bu left over.",
        details = "The sources do not date this unit rule to the 480s; the rule is older, and 485 is the year of the anchor, the Northern Wei equal-field decree that measured its grants in mu, not the year of the rule. The fields are whole-number rectangles, so the count is exact.",
        rare = true,
        instructions = """
            1. Save the example as mu_bu.py.
            2. Run it: python3 mu_bu.py
        """,
        example = """
            BU_PER_MU = 240   # square bu in one mu, by the land rule
            fields = [("120 by 80 bu", 120, 80), ("100 by 100 bu", 100, 100), ("90 by 60 bu", 90, 60)]
            for name, width, length in fields:
                square = width * length
                mu, rest = divmod(square, BU_PER_MU)
                print(name, "is", square, "square bu, or", mu, "mu and", rest, "square bu")
        """,
    ),

)
