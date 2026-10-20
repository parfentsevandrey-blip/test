package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 460 to 470: the western throne filled from Constantinople in 467, with the vacancy and the end of the reign set out. */
val db158Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 158,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "Anthemius, the western emperor chosen by Leo I of the East in 467",
        originalTitle = "Anthemius Augustus",
        author = "Leo I of the East and Ricimer",
        year = 467,
        summary = "Anthemius, chosen for the western throne by the eastern emperor Leo I, held it from 467 until Ricimer had him executed in 472.",
        details = "Anthemius's western reign is dated from 12 April 467 to 11 July 472. Accounts put the vacancy before his reign at eighteen months or about two years, and they differ on its start: one dates it to November 465, and another dates the death of Libius Severus to 15 August 465. Ricimer, who commanded the army, accepted him through a marriage tie to Anthemius's daughter, and Ricimer had him executed on 11 July 472.",
        rare = true,
    ),

)
