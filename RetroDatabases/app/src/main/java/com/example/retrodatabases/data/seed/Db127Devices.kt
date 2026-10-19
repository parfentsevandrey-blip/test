package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 770 to 780: the first astrolabe made in Islam, credited to Muhammad al-Fazari and placed at 775 by the death of al-Mansur. */
val db127Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.DEVICE,
        section = "Abbasid",
        title = "The first astrolabe made in Islam, credited to Muhammad ibn Ibrahim al-Fazari",
        originalTitle = "usturlab",
        author = "Muhammad ibn Ibrahim al-Fazari",
        year = 775,
        summary = "Modern accounts credit Muhammad ibn Ibrahim al-Fazari with the earliest astrolabe made in the Muslim world, an instrument whose making the sources do not date.",
        details = "The sources do not date this astrolabe; 775 is the year of the anchor, not of the making. The anchor is the death of the caliph al-Mansur, under whose reign of 754 to 775 Muhammad's court work is placed.",
        rare = true,
    ),

)
