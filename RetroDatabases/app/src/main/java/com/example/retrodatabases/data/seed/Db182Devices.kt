package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 220 to 230: a repeating crossbow of older design, placed at 228 for Zhuge Liang's first northern campaign. */
val db182Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 182,
        kind = Kind.DEVICE,
        section = "Arms",
        title = "The repeating crossbow that later histories credit Zhuge Liang with improving",
        originalTitle = "Zhuge nu",
        author = "Zhuge Liang, as credited",
        year = 228,
        summary = "The repeating crossbow is an older device that later histories credit Zhuge Liang with improving, and this record is placed at 228, the year of his first northern campaign.",
        details = "The sources do not date the device or its improvement, and its basic design may be older than Zhuge Liang, so 228 is the year of the anchor and not the year of the making. The anchor is Zhuge Liang's first northern campaign against Wei in 228, which the record uses only to place the device in his time.",
        rare = true,
    ),

)
