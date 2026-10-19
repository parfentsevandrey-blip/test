package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 740 to 750: the foot-operated bellows and furnaces of the Great Buddha casting, placed at the decree of 743. */
val db130Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 130,
        kind = Kind.DEVICE,
        section = "Japanese",
        title = "The foot-operated bellows and furnaces that melted the bronze of the Great Buddha at Nara",
        originalTitle = "Tatara",
        author = "Unknown Nara metalworkers",
        year = 743,
        summary = "The bronze of the Great Buddha at Nara was melted in furnaces worked by foot-operated bellows, a bellows called tatara.",
        details = "The sources do not date this bellows and furnace; 743 is the year of the decree that began the Great Buddha project, not the year of the making. Accounts differ on when the casting began, so the record is placed at the decree's year as an anchor.",
        rare = true,
    ),

)
