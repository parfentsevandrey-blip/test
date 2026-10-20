package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 360 to 370: the Shichishito, a seven-branched sword whose inscription is read as dated to 369. */
val db168Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.DEVICE,
        section = "Metalwork",
        title = "The Shichishito, a seven-branched sword whose inscription is read as 369",
        originalTitle = "Shichishito",
        author = "Unknown smiths",
        year = 369,
        summary = "The Shichishito is an iron sword with seven branches and gold-inlaid writing, kept at Isonokami Shrine in Japan, whose inscription is read as dated to 369.",
        details = "The inscription gives the fourth year of an era read as Taiwa, which most readings put at 369, but the month and some words are worn and the readings differ. Some calendar checks do not fit 369, so the year is a reading of a damaged text rather than a certainty.",
        rare = true,
    ),

)
