package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1020 to 1030: al-Biruni's Kitab al-Tafhim, an astrology handbook that also covers geometry and astronomy. */
val db102Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 102,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "al-Biruni's Kitab al-Tafhim, an astrology handbook in Arabic and Persian",
        originalTitle = "Kitab al-Tafhim li-awa'il sina'at al-tanjim",
        author = "Abu Rayhan al-Biruni",
        year = 1029,
        summary = "Al-Biruni's Kitab al-Tafhim is a handbook of astrology that also covers geometry, astronomy, astrolabe use, geography and chronology, and it is dated to Ghazna in 1029.",
        details = "The book was written in both Arabic and Persian, and arithmetic is among its subjects. One account suggests that it was drafted earlier, while al-Biruni was still at Jurjan before his move to Ghazna around 1017, and R. Ramsay Wright published an English translation in 1934.",
        rare = true,
    ),

)
