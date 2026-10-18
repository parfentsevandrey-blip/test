package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One device of 1120 to 1130: al-Khazini's hydrostatic balance, which weighs metals in air and water. */
val db92Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 92,
        kind = Kind.DEVICE,
        section = "Balances",
        title = "Al-Khazini's hydrostatic balance in the Book of the Balance of Wisdom, 1121",
        originalTitle = "Mizan al-hikma",
        author = "Abd al-Rahman al-Khazini",
        year = 1121,
        summary = "Al-Khazini's Book of the Balance of Wisdom describes a hydrostatic balance with several scale pans, used to weigh precious metals and test their purity.",
        details = "The work defines specific weight and surveys several balances before giving the author's own instrument, which has five scale pans on a multi-wire suspension with a pointer. The year is given as 1121 in some sources and as 1121 or 1122 in others, so the record uses 1121 as a working date.",
        rare = true,
    ),

)
