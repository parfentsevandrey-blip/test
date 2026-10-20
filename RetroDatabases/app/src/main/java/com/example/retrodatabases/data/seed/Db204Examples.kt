package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 0 to 10: the gap between the stated people of the Han census of 2 and the rounded total of other accounts. */
val db204Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.EXAMPLE,
        section = "Census",
        title = "The gap between the two accounts of the Han census of 2, counted in code",
        originalTitle = "Gap between two totals",
        author = "Census clerks of the Han and later readers",
        year = 2,
        summary = "The stated Han census total of 59,594,978 people and the rounded total of 57,670,000 in other accounts differ by 1,924,978, and a short program counts that gap.",
        details = "The rounded total stands for the figure of about 57.67 million that other accounts give, so the example shows the size of the dispute and makes no new count of people. The program prints each figure so the result can be checked by hand.",
        instructions = """
            1. Save the example as census_gap.py.
            2. Run it: python3 census_gap.py
        """,
        example = """
            stated = 59594978      # people in the stated total of the census
            rounded = 57670000     # about 57.67 million, rounded for this count
            gap = stated - rounded
            print("stated people:", stated)
            print("other account, rounded:", rounded)
            print("gap:", gap)
        """,
        rare = true,
    ),

)
