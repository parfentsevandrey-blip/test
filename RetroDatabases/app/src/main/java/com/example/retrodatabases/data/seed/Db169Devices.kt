package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 350: one Roman pin lock, placed at 350, the year of its anchor. */
val db169Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.DEVICE,
        section = "Locks",
        title = "The Roman pin lock, a bronze lock that a key raised by pins",
        originalTitle = "Sera and clavis",
        author = "Roman makers of locks",
        year = 350,
        summary = "Roman bronze locks were worked by pins that a key lifted to the right height, so that only the right key opened them.",
        details = "The sources do not date this device, so 350 is the year of the anchor, the usurpation of Magnentius in the west, not of the making. Roman locks of this kind survive as bronze objects, which is why the record is placed and is among the weakest in the database.",
        rare = true,
    ),

)
