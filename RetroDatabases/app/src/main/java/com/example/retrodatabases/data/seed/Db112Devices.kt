package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 927: an astrolabe held at SOAS in London, inscribed with the date 315 AH, a candidate for the earliest dated astrolabe. */
val db112Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "The SOAS astrolabe inscribed 315 AH, a candidate for the earliest dated astrolabe",
        originalTitle = "Astrolabe inscribed 315 AH",
        author = "Nastulus (also given as Bastulus)",
        year = 927,
        summary = "An astrolabe held at SOAS in London is inscribed with the date 315 AH, which falls in 927 or 928 CE, and it is a candidate for the earliest dated astrolabe.",
        details = "Its maker is given as Nastulus or Bastulus, and its dating and authenticity have been questioned. The inscribed date falls in 927 or 928, and the year used here is the earlier of the two.",
        rare = true,
    ),

)
