package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 280 to 290: Du Yu's linked trip-hammers, undated in the sources and placed at 280 by the conquest of Wu. */
val db176Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 176,
        kind = Kind.DEVICE,
        section = "Water power",
        title = "Du Yu's linked trip-hammers, credited to a Jin general of the third century",
        originalTitle = "lian zhi dui",
        author = "Du Yu, as later writers credit him",
        year = 280,
        summary = "Linked trip-hammers, a water-driven stamping mill that worked several hammers at once, are credited to the Jin general Du Yu by later writers.",
        details = "The sources do not date the hammers, and the credit to Du Yu is disputed, because Han burial models already show hammers worked by a vertical wheel. The year 280 is the year of the anchor, the Jin conquest of Wu, and not the year of the device.",
        rare = true,
    ),

)
