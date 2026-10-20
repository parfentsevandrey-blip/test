package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 490 to 500: one placed record, a set of standard balance weights for gold coins at the Constantinople mint. */
val db155Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.DEVICE,
        section = "Weighing",
        title = "Standard balance weights of the Constantinople mint, placed at 498",
        originalTitle = "Exagium",
        author = "Officials of the Constantinople mint",
        year = 498,
        summary = "Balances with standard weights, called exagia, checked the gold coins of the Byzantine mint, and this placed record uses the bronze reform of 498 as its anchor.",
        details = "This record does not date a particular set of weights; 498 is the year of the anchor, the bronze reform, not the year of the making. A balance of this kind weighed a gold coin against a standard weight.",
        rare = true,
    ),

)
