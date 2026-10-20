package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 530 to 540: the common life of monks at Monte Cassino under one abbot, placed at 530 for the founding of the house. */
val db151Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 151,
        kind = Kind.PRACTICE,
        section = "Italian",
        title = "The common life of monks at Monte Cassino under one abbot, with shared prayer and work, placed at 530 for the founding of the house",
        originalTitle = "cenobium",
        author = "Benedict of Nursia and his monks",
        year = 530,
        summary = "Benedict's monks at Monte Cassino lived together under one abbot, sharing their prayer, reading and work in one house.",
        details = "The sources do not date this practice; 530 is the year of the anchor, the founding of Monte Cassino, which accounts date to about 529 or 530, and not the year the practice began. The common life is described in Gregory the Great's Dialogues, written about 593, so the practice rests on a later account rather than on a dated charter.",
        rare = true,
    ),

)
