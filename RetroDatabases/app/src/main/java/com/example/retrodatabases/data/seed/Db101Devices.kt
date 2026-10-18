package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1030: al-Biruni's astrolabe as he described it in a treatise without a date, placed at the start of Mas'ud's reign. */
val db101Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The astrolabe as al-Biruni described its making and use, in a treatise the sources do not date",
        originalTitle = "Treatise on the astrolabe",
        author = "al-Biruni",
        year = 1030,
        summary = "Al-Biruni's treatise on the astrolabe is placed in 1030, a year the sources do not give for it.",
        details = "The treatise has no date in the sources, so the year 1030 is a placement at the start of Mas'ud's reign, the Ghaznavid sultan to whom al-Biruni dedicated his Canon; it is not a dated making. Al-Biruni had lived at Ghazni under Mahmud from 1017, so the placement falls within his Ghaznavid years.",
        rare = true,
    ),

)
