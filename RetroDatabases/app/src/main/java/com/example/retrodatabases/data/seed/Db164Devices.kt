package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 400 to 410: a water-driven stone saw at Hierapolis, placed at 402, the year Honorius moved the western court to Ravenna. */
val db164Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 164,
        kind = Kind.DEVICE,
        section = "Mechanics",
        title = "The water-driven stone saw of Hierapolis, a sawmill that cut marble blocks, undated in the sources and placed at 402",
        originalTitle = "Hierapolis sawmill",
        author = "Roman engineers of the eastern provinces",
        year = 402,
        summary = "A water-driven saw at Hierapolis in Asia Minor cut stone blocks with a wheel and a crank, an early mechanical device known from its remains.",
        details = "The sources do not date this saw, so 402 is the year of the anchor, the year Honorius moved the western court to Ravenna, and not the year of the making. The remains at the site are dated to the late Roman period, and the placed year marks that period rather than one object.",
        rare = true,
    ),

)
