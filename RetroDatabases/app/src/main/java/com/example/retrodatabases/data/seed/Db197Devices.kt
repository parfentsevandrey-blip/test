package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 70 to 80: the libella, a Roman level, placed at 79 because the sources do not date it. */
val db197Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.DEVICE,
        section = "Surveying",
        title = "The libella, a Roman level with a plumb line, undated in the sources and placed at 79",
        originalTitle = "Libella",
        author = "Roman builders and surveyors",
        year = 79,
        summary = "The libella is a Roman level, a frame with a plumb line hung from its crossbar, and this record is placed at 79 because the sources do not date the device.",
        details = "The sources do not date this device; 79 is the year of the anchor, the eruption of Vesuvius, not of the making. A plumb line that hangs straight from the crossbar shows that the base is level, a simpler check than the long water channel of the chorobates.",
        rare = true,
    ),

)
