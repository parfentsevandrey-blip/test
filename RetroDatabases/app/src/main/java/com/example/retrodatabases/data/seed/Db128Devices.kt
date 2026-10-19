package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 760 to 770: a prayer wheel, placed at 763 because no source dates the first wheel. */
val db128Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.DEVICE,
        section = "Tibetan",
        title = "The Tibetan prayer wheel, a cylinder of written prayers turned by hand, with no firm date for its first use",
        originalTitle = "mani khorlo",
        author = "Tibetan makers, unknown",
        year = 763,
        summary = "A prayer wheel is a hand-turned cylinder of written prayers that repeats its text on every turn.",
        details = "The sources do not date this device; 763 is the year of the anchor, the Tibetan seizure of Chang'an, and not the year of any wheel. No firm date for the first wheel is known, so the record keeps the device for its method of repeating a written prayer on each turn.",
        rare = true,
    ),

)
