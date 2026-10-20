package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 10 to 20: the rise in the Roman citizen roll from the census of 28 BC to the census of 14, in citizens and in percent. */
val db203Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.EXAMPLE,
        section = "Growth",
        title = "The rise in the Roman citizen roll from the census of 28 BC to the census of 14",
        originalTitle = "Census growth",
        author = "Roman censors",
        year = 14,
        summary = "The citizen roll rose by 874,000 between the census of 28 BC and the census of 14, a rise of 21.5 percent, and a short program computes both figures.",
        details = "The two counts are 4,063,000 for 28 BC and 4,937,000 for 14, both as chapter 8 of the Res Gestae gives them, and the example uses only those two. The percentage is the rise over the earlier count, printed to one decimal place, and the program prints both numbers so they can be checked by hand.",
        instructions = """
            1. Save the example as citizen_growth.py.
            2. Run it: python3 citizen_growth.py
        """,
        example = """
            before = 4063000     # citizens on the roll at the census of 28 BC
            after = 4937000      # citizens on the roll at the census of 14
            rise = after - before
            print("rise in citizens:", rise)
            print("rise in percent:", f"{rise * 100 / before:.1f}")
        """,
        rare = true,
    ),

)
