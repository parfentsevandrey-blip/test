package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 900 to 910, a placed record: the Tang fish tally, dated by the fall of the Tang in 907 rather than by any tally. */
val db114Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.DEVICE,
        section = "China",
        title = "The Tang fish tally, a split metal fish that proved an official's identity at a gate",
        originalTitle = "Yufu",
        author = "The Tang court",
        year = 907,
        summary = "The Tang court gave its officials fish-shaped metal tallies, cut in two, so that one half could be matched against the other at a gate.",
        details = "This is a placed record: the fish tally is a device of the Tang court, and its year 907 is the year the dynasty fell, used as the anchor for placement rather than the date of any particular tally. Officials carried one half, and the matching of the two halves let the guard check the bearer's right to pass.",
        rare = true,
    ),

)
