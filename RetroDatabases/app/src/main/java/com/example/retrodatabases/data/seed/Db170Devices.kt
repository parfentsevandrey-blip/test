package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 340 to 350: the shaduf, a counterweighted lever that lifts water, placed at 340, the year Constantine II died. */
val db170Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.DEVICE,
        section = "Hydraulics",
        title = "The shaduf, a counterweighted lever that lifts water from a channel to a field, placed at 340 though far older",
        originalTitle = "Shaduf",
        author = "Farmers of Egypt and the Near East",
        year = 340,
        summary = "The shaduf is a long lever with a counterweight and a bucket that lifts water from a channel, a device of Egyptian and Near Eastern fields that is far older than this decade.",
        details = "The sources do not date this device in the fourth century, so 340 is the year of the anchor, the death of Constantine II, and not the year of the making. Egyptian art shows the lever in use long before the Roman period, and the placed year marks the decade rather than one object.",
        rare = true,
    ),

)
