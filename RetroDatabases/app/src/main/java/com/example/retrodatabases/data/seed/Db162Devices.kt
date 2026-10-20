package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 420 to 430: the groma, a Roman surveyor's cross, which the sources do not date and which is placed at 420, the year the Liu Song was founded. */
val db162Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 162,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "The groma, a Roman surveyor's cross of plumb lines, undated in the sources and placed at 420, the year the Liu Song was founded",
        originalTitle = "groma",
        author = "Roman land surveyors",
        year = 420,
        summary = "The groma was a surveyor's cross with plumb lines hanging from its arms, used to set out right angles on the ground, and this record is placed at 420 because the sources do not date the instrument.",
        details = "The sources do not date this device, so 420 is the year of the anchor, the founding of the Liu Song dynasty, and not the year of the making. The instrument is described in Roman surveying writings, and no particular groma is tied to a date.",
        rare = true,
    ),

)
