package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 320 to 330: the Roman screw press for wine and cloth, undated in the sources and placed at 324, the year of Chrysopolis. */
val db172Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 172,
        kind = Kind.DEVICE,
        section = "Presses",
        title = "The Roman screw press for wine and cloth, a timber frame with a turned screw, undated in the sources and placed at 324",
        originalTitle = "Torcular",
        author = "Roman writers on farming and fulling",
        year = 324,
        summary = "The Roman screw press, a timber frame with a turned screw that squeezed grapes and cloth, is described by Roman writers and was used across the empire.",
        details = "The sources do not date the screw press in the fourth century, so 324 is the year of the anchor, the battle of Chrysopolis, and not the year of any press's making. Roman writers describe screw presses for wine and for pressing cloth, and the placed year marks the period rather than one object.",
        rare = true,
    ),

)
