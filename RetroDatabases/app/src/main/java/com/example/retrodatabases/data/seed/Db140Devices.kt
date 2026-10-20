package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 640 to 650: the padded horse collar, a harness whose first use has no firm date, placed here at 650. */
val db140Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 140,
        kind = Kind.DEVICE,
        section = "Medieval",
        title = "The padded horse collar, a harness for draught horses whose first use has no firm date, placed at 650",
        originalTitle = "padded horse collar",
        author = "Unknown makers",
        year = 650,
        summary = "The padded horse collar, which moved a horse's pull from its throat to its shoulders, has no firmly dated first use and is placed here at 650.",
        details = "Its first use has no firm date, so the year 650 is a placed year and not a date from any source. The collar put the pull of the horse on its shoulders instead of its throat, which let the horse pull heavier loads.",
        rare = true,
    ),

)
