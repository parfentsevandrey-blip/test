package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 560 to 570: the Roman counting board, placed at 565, the year Justin II came to the throne. */
val db148Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 148,
        kind = Kind.DEVICE,
        section = "Roman",
        title = "The Roman counting board, a flat board with counters for sums",
        originalTitle = "abacus (counting board)",
        author = "Roman calculators",
        year = 565,
        summary = "A flat counting board with counters set in columns let a calculator add and subtract without writing numerals.",
        details = "Roman calculators set counters, called calculi, on a flat board in columns and read the sums from their places. This record is placed: the sources do not date a board of this decade, so 565, the year Justin II came to the throne, is only the anchor and not the date of any board.",
        rare = true,
    ),

)
