package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 220 to 230: the years between Africanus's chronology of 221 and the Bakhshali date of 224. */
val db182Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Africanus's chronology of 221 to the Bakhshali date of 224",
        originalTitle = "Years between two dated items",
        author = "No single author",
        year = 221,
        summary = "Africanus's chronology is dated to 221 and the Bakhshali leaf's radiocarbon range begins at 224, so one line of arithmetic gives three years between them.",
        details = "Both dates are approximate: 221 is the close of Africanus's chronology, and 224 is the lower end of the radiocarbon range for one Bakhshali leaf. The difference of three years is therefore a difference between two approximate dates, and the example prints it as a plain subtraction.",
        instructions = """
            1. Save the example as africanus_bakhshali.py.
            2. Run it: python3 africanus_bakhshali.py
        """,
        example = """
            AFRICANUS_YEAR = 221
            BAKHSHALI_YEAR = 224
            print("years from Africanus's chronology to the Bakhshali date:", BAKHSHALI_YEAR - AFRICANUS_YEAR)
        """,
    ),

)
