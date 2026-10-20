package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 30 to 40: the shipan, a Han diviner's board of two turning plates, placed at 36 because the sources do not date it. */
val db201Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.DEVICE,
        section = "Divination",
        title = "The Han diviners' shipan, a turning heaven plate on an earth plate, undated in the sources and placed at 36",
        originalTitle = "shipan",
        author = "Han diviners",
        year = 36,
        summary = "The shipan was a Han diviner's instrument of two plates, a round heaven plate turning on a square earth plate, used to choose lucky days and directions.",
        details = "The sources do not date any particular board; 36, the death of Gongsun Shu in Shu, is the year of the anchor and not of the making. The board marks the twelve earthly branches and the points of the compass, so it belongs with the reckoning devices of the empire.",
        rare = true,
    ),

)
