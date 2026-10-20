package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 440 to 450: one placed record, a portcullis, dated by the repair of the Theodosian walls and not by the device. */
val db160Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.DEVICE,
        section = "Fortification",
        title = "The portcullis, a grille of wood or iron that was lowered to close a gateway in a town wall",
        originalTitle = "Cataracta",
        author = "Builders of town walls",
        year = 447,
        summary = "A portcullis was a grille of wood or iron that closed a gateway when it was lowered, a device known from the fortified towns of the Roman world.",
        details = "The sources do not date this device; 447 is the year of the anchor, the repair of the Theodosian walls of Constantinople after an earthquake, and not the year of any gate-grille's making. The record is placed for that reason, since the portcullis is far older than this decade.",
        rare = true,
    ),

)
