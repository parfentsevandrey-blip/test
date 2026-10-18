package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1050: al-Mawardi's manual of the caliphate's offices, written in the mid-eleventh century. */
val db99Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.TOOL,
        section = "Government",
        title = "Al-Mawardi's Ordinances of Government, a manual of the caliphate's offices",
        originalTitle = "Al-ahkam al-sultaniyya",
        author = "Al-Mawardi of Baghdad",
        year = 1050,
        summary = "Al-Mawardi, a jurist of Baghdad who died in 1058, wrote a systematic manual on the offices, taxes and courts of the Islamic state and how a caliphate should be run.",
        details = "The book is usually placed in the mid-eleventh century, before his death in 1058, and its exact date of composition is not known. It goes through the offices one by one as a legal treatise, and later writers on government drew on it.",
        rare = true,
    ),

)
