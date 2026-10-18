package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1070 to 1079: the instruments of the Song Bureau of Astronomy under Shen Kuo, undated and placed by the year his supervision begins. */
val db97Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Shen Kuo's armillary sphere, gnomon and clepsydra of the Bureau of Astronomy, from 1072",
        originalTitle = "Shen Kuo's astronomical instruments",
        author = "Shen Kuo",
        year = 1072,
        summary = "The armillary sphere, gnomon and clepsydra of the Song Bureau of Astronomy, which Shen Kuo supervised from 1072.",
        details = "The sources do not date these instruments, so the year is a placement: 1072 is when Shen Kuo took charge of the bureau, the earliest date the sources give for his work on them. The year is the date of the office, not of any surviving object.",
        rare = true,
    ),

)
