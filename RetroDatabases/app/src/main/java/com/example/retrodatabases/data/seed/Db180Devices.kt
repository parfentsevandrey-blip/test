package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 244: a clay jar found near Baghdad in 1936, placed at 244 by the anchor of Shapur I's victory at Misiche. */
val db180Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.DEVICE,
        section = "Iraq",
        title = "The Baghdad battery, a clay jar with a copper cylinder and an iron rod",
        originalTitle = "Baghdad battery",
        author = "Unknown makers",
        year = 244,
        summary = "A clay jar with a copper cylinder and an iron rod, found near Baghdad in 1936, whose use is disputed; the year here is an anchor, not a date of the jar.",
        details = "The sources do not date this jar, and 244 is the year of the anchor, Shapur I's victory over the Romans at Misiche, used only to place the record. Accounts differ on whether the jar belongs to the Parthian period, which ended in 224, or to the Sasanian period that followed it, and its reading as an electric cell is disputed.",
        rare = true,
    ),

)
