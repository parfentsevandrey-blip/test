package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1273: the counterweight trebuchets, called huihui pao, that were used against the walls of Xiangyang. */
val db77Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.DEVICE,
        section = "Siege engines",
        title = "The counterweight trebuchets that helped take Xiangyang in 1273",
        originalTitle = "Huihui pao",
        author = "Engineers sent by the Ilkhan Abaqa",
        year = 1273,
        summary = "Counterweight trebuchets, called huihui pao, were built by engineers sent from Persia and were used against the walls of Xiangyang in 1273 by the forces of Kublai Khan.",
        details = "Accounts name the engineer Ismail, who was sent by the Ilkhan Abaqa, and they date the siege from about 1267 or 1268 to early 1273. A few accounts trace a similar machine to the Jin in 1232, so accounts differ on whether the counterweight design was new at Xiangyang.",
        rare = true,
    ),

)
