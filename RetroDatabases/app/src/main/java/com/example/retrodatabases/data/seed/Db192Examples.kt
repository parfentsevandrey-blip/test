package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 120 to 130: three accounts of the Sixth Legion's move to Britain, counted and spanned by a short program. */
val db192Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 192,
        kind = Kind.EXAMPLE,
        section = "Legions",
        title = "The Sixth Legion's move to Britain, dated 119 to 122 by different accounts, sorted in code",
        originalTitle = "Legio VI Victrix",
        author = "The Roman army of Britain",
        year = 122,
        summary = "Accounts give different years from 119 to 122 for the Sixth Legion's move to Britain, and a short program sorts the dates and finds the span between them.",
        details = "Accounts differ, so the record is dated to 122, the year of Hadrian's visit to Britain and the latest of the three dates. The example prints the number of dates, the earliest, the latest and the span, so the spread of the accounts can be checked by hand.",
        instructions = """
            1. Save the example as legion_dates.py.
            2. Run it: python3 legion_dates.py
        """,
        example = """
            moves = [119, 121, 122]   # years given for the move, as accounts differ
            print("dates given:", len(moves))
            print("earliest:", min(moves))
            print("latest:", max(moves))
            print("span in years:", max(moves) - min(moves))
        """,
        rare = true,
    ),

)
