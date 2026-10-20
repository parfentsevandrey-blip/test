package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 540 to 550: the plough and harrow for dry fields that the Qimin yaoshu describes, placed at the year of the book. */
val db150Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.DEVICE,
        section = "Chinese",
        title = "The plough and harrow for dry fields in north China, as the Qimin yaoshu describes their use",
        originalTitle = "lei and ba",
        author = "Jia Sixie, describing the tools",
        year = 544,
        summary = "The Qimin yaoshu describes the plough and harrow that farmers used on dry fields in north China, two tools whose making the sources do not date.",
        details = "The sources do not date this device: 544 is the year of the Qimin yaoshu, which describes ploughing and harrowing, not the year the tools were made, and the tools are older than the book. The year 544 is placed at the book's date under the placement rule, so this is one of the weakest records of the database.",
        rare = true,
    ),

)
