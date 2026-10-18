package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1090: the years from the taking of Alamut to its fall to the Mongols, counted and split in code. */
val db95Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.EXAMPLE,
        section = "Counting",
        title = "The years from the taking of Alamut to its fall, counted in code",
        originalTitle = "Years between two fixed dates",
        author = "Hasan-i Sabbah and the Mongols",
        year = 1090,
        summary = "A short program counts the 166 years from the taking of Alamut in 1090 to its fall to the Mongols in 1256, and splits the span into whole centuries and the years left over.",
        details = "Both dates are given as years only, as commonly stated: the fortress was taken in 1090 and fell in 1256. The example uses integer division and the remainder, so the split into centuries needs no calendar and no date library.",
        instructions = """
            1. Save the example as alamut_years.py.
            2. Run it: python3 alamut_years.py
        """,
        example = """
            taken = 1090   # Hasan-i Sabbah takes Alamut, as commonly given
            fell = 1256    # the Mongols take Alamut, as commonly given
            span = fell - taken
            print("years from Alamut's taking to its fall:", span)
            print("whole centuries:", span // 100, "and years left over:", span % 100)
        """,
    ),

)
