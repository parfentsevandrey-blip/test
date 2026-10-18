package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1020 to 1030: the Sichuan paper notes in fixed values from 1024 and the burning of heretics at Orleans in 1022. */
val db102Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.PRACTICE,
        section = "Money",
        title = "Sichuan paper notes in fixed denominations from one to ten guan",
        originalTitle = "Sichuan paper notes in fixed denominations from one to ten guan",
        author = "Sichuan note issuers and the Song state",
        year = 1024,
        summary = "From 1024 the Sichuan paper notes called jiaozi were issued in fixed denominations from one guan to ten guan.",
        details = "Private traders in Sichuan had begun issuing these notes in place of metal money before the state took over. Accounts give 1024 for the first notes of fixed value, one year after the date given for the Jiaozi office.",
        rare = true,
    ),

    entry(
        databaseId = 102,
        kind = Kind.PRACTICE,
        section = "Heresy",
        title = "The burning of heretics at Orleans in 1022",
        originalTitle = "The burning of heretics at Orleans in 1022",
        author = "King Robert II of France",
        year = 1022,
        summary = "In 1022 a group of heretics at Orleans was condemned and burned, one of the earliest recorded burnings for heresy in medieval Western Europe.",
        details = "The case is known from the chronicle of Ademar of Chabannes and a few other accounts, and the details of the trial are not fully clear. Historians count it among the first such burnings in the Latin West.",
        rare = true,
    ),

)
