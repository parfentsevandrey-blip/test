package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 460 to 470: dating years by a reign-period name, here the Taishi era of Liu Song from 465 to 471. */
val db158Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.PRACTICE,
        section = "Dating",
        title = "Dating years by the Taishi reign-period name of Liu Song, from 465 to 471",
        originalTitle = "Taishi nian hao",
        author = "Emperor Ming of Liu Song",
        year = 465,
        summary = "Liu Song counted its years by the Taishi reign-period name of the emperor Ming, from 465 to 471, and the Taiyu name followed in 472.",
        details = "Dynastic tables run the Taishi era from 465 to 471, and the Taiyu era covers 472; the emperor who chose these names was Liu Yu, known as Emperor Ming. The emperor's reign is given from 9 January 466 in one account, so the first year of the era and his accession do not coincide. A Book of Song entry for September 466 falls in Taishi year two, which fits 465 as year one.",
        rare = true,
    ),

)
