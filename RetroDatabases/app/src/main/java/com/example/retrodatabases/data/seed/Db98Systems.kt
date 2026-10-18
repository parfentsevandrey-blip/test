package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1065 to 1066: a college of Baghdad endowed by a vizier, and the Norman kingdom begun at Westminster. */
val db98Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The Nizamiyya of Baghdad, a college for Shafi'i law founded by Nizam al-Mulk",
        originalTitle = "Madrasat al-Nizamiyya",
        author = "Nizam al-Mulk, vizier of the Seljuq sultans",
        year = 1065,
        summary = "The Nizamiyya of Baghdad was a college endowed by the Seljuq vizier Nizam al-Mulk for teachers and students of Shafi'i law, dated to 1065 in some accounts and to its opening in 1067 in others.",
        details = "The college was supported by an endowment and became the model for a wider network of Nizamiyya colleges in the Seljuq lands. An earlier Nizamiyya at Nishapur is dated by one study to 1057 or 1058, so the Baghdad college may not have been the first of its kind.",
        rare = true,
    ),

    entry(
        databaseId = 98,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Norman kingdom of England, begun at William's coronation on Christmas Day 1066",
        originalTitle = "Regnum Anglorum",
        author = "William, duke of Normandy, later William I of England",
        year = 1066,
        summary = "William of Normandy was crowned king of England at Westminster on Christmas Day 1066, and the Norman monarchy that followed placed new lords over most of the English land.",
        details = "The crowning came after William's victory at Hastings on 14 October 1066, where the English king Harold Godwineson was killed. Norman lords took over most of the land in the years that followed, and the English kingdom continued under a new ruling class.",
    ),

)
