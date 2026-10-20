package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 510 to 520: the Salic law of the Franks, the Pactus Legis Salicae, traditionally dated to the years of Clovis's last reign. */
val db153Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Salic law of the Franks, the Pactus Legis Salicae of 65 titles, traditionally dated 507 to 511 under Clovis",
        originalTitle = "Pactus Legis Salicae",
        author = "Clovis, with later Frankish kings",
        year = 511,
        summary = "The Pactus Legis Salicae, a Latin law code of 65 titles for the Salian Franks, is traditionally dated to about 507 to 511 under Clovis.",
        details = "Its core text is traditionally linked to Clovis's last years, and later kings revised it, with a Carolingian revision under Charlemagne about three centuries on. The traditional dating is disputed: one view puts the main drafting before 481 or 482, another before 507, and others see origins in the mid-fourth century or regard most of the surviving text as much later. The year 511 used here is the end of the traditional range.",
    ),

)
