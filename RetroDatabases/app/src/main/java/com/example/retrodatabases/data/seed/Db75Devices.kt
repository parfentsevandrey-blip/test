package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1291 and 1295: a Chinese cotton gin brought to Songjiang about 1295 and the glass furnaces moved to Murano by Venetian order in 1291. */
val db75Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "Huang Daopo's cotton gin, brought to Songjiang about 1295",
        originalTitle = "Jiaoche",
        author = "Huang Daopo",
        year = 1295,
        summary = "Huang Daopo is said to have brought a cotton gin to Songjiang about 1295, a device that pulled seeds out of raw cotton more easily than hand picking.",
        details = "The gin is called jiaoche in the sources and is commonly translated as a roller gin. Accounts differ on whether she came home from Hainan or was native to Songjiang, and the date of her return rests on sources written well after the event.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.DEVICE,
        section = "Glass",
        title = "The glass furnaces of Murano, moved from Venice by order of 1291",
        originalTitle = "Fornace di Murano",
        author = "The Venetian council",
        year = 1291,
        summary = "A Venetian order of 1291 moved the glass furnaces out of the city to the island of Murano, to lower the risk of fire in the crowded city.",
        details = "The move is dated to 1291 in most accounts, and it fixed glassmaking on the island for centuries. Accounts differ on the exact wording of the order and on whether every furnace moved at once.",
        rare = true,
    ),

)
