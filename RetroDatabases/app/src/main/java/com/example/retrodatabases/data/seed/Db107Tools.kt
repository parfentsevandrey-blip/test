package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 973 and 977: the Song revised materia medica of 973, and Ibn Hawqal's geography with maps in its revised form of 977. */
val db107Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 107,
        kind = Kind.TOOL,
        section = "Song China",
        title = "The Kaibao bencao, the revised materia medica that Emperor Taizu of Song ordered in 973",
        originalTitle = "Kaibao bencao",
        author = "Liu Han, Ma Zhi and colleagues",
        year = 973,
        summary = "Emperor Taizu of Song ordered a revised materia medica in 973, and the Kaibao bencao became a central pharmaceutical text of the early Song.",
        details = "The imperial physician Liu Han received the order, and Ma Zhi with eight colleagues is credited with collating the earlier Tang and Shu materia medica texts. Accounts do not settle who led the work.",
        rare = true,
    ),

    entry(
        databaseId = 107,
        kind = Kind.TOOL,
        section = "Islamic lands",
        title = "Ibn Hawqal's Surat al-Ard, a geography of the Islamic lands with regional maps, in its revised form of 977",
        originalTitle = "Kitab Surat al-Ard",
        author = "Ibn Hawqal, a merchant and traveller",
        year = 977,
        summary = "Ibn Hawqal, a merchant and traveller, completed the revised form of his geography, Surat al-Ard, in 977, with maps of the regions it describes.",
        details = "The book describes roads, trade and peoples across the lands from Spain to Central Asia, and it reworks the earlier geography of al-Istakhri under the same name. The revised text is dated 977.",
        rare = true,
    ),

)
