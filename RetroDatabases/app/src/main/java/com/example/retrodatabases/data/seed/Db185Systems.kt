package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 190 to 200: Septimius Severus's campaign against the Parthians, begun in 197. */
val db185Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 185,
        kind = Kind.SYSTEM,
        section = "Parthia",
        title = "Septimius Severus's campaign against the Parthians, begun in 197",
        originalTitle = "Bellum Parthicum",
        author = "Septimius Severus",
        year = 197,
        summary = "Septimius Severus turned from his victory over Clodius Albinus to attack the Parthian kingdom in 197, and his forces took Seleucia and Babylon and looted Ctesiphon in the winter of 197 to 198.",
        details = "The war followed the battle of Lugdunum on 19 February 197, in which Albinus was defeated and killed, and the Roman advance down the Euphrates came in the winter that followed. Accounts date the looting of Ctesiphon to late 197 or to January 198, and they differ on whether the failed siege of Hatra fell in 198, 199 or 200. The year 197 is the start used in this record.",
        rare = true,
    ),

)
