package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 0 to 10: a gap between two totals written as a percentage of the stated total, worked out for the Han census of 2. */
val db204Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.GUIDE,
        section = "Shares",
        title = "How a gap between two totals is written as a percentage of the stated total",
        originalTitle = "Percentage of a total",
        author = "Census clerks and accountants",
        year = 2,
        summary = "A gap between two totals is written as a percentage of the stated total by dividing the gap and multiplying by 100, and a short program does it for the Han census of 2.",
        details = "The method is undated, so the record sits at 2, the census year that the worked example uses, and not at the date of the method. The gap is the one between the stated people and the rounded total of other accounts, and the program rounds the share to two places and to one, so each step can be checked by hand.",
        instructions = """
            1. Save the example as share.py.
            2. Run it: python3 share.py
        """,
        example = """
            stated = 59594978      # people in the stated total of the census
            rounded = 57670000     # about 57.67 million, rounded for this count
            gap = stated - rounded
            share = 100 * gap / stated
            print("gap:", gap)
            print("share of the stated total, in percent:", round(share, 2))
            print("to one decimal place:", round(share, 1))
        """,
    ),

)
