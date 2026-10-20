package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 150 to 160: the Quartodeciman keeping of Easter on 14 Nisan, placed at 155 with the anchor of Polycarp's meeting in Rome. */
val db189Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 189,
        kind = Kind.PRACTICE,
        section = "Easter",
        title = "Keeping Easter on the fourteenth of Nisan, the Quartodeciman practice of Asia Minor",
        originalTitle = "Quartodeciman practice",
        author = "Churches of Asia Minor",
        year = 155,
        summary = "Some churches of Asia Minor kept Easter on the fourteenth day of the month of Nisan whatever the weekday, while the church at Rome kept it on a Sunday, and the difference was disputed.",
        details = "The sources do not date the start of this practice; 155 is the placed year, the date given by one account for the meeting in Rome of Polycarp of Smyrna and Anicetus, and not the date of the practice. Eusebius places Polycarp's death in the reign of Marcus Aurelius, so the date of the meeting is disputed, and the churches were allowed to keep their own custom.",
        rare = true,
    ),

)
