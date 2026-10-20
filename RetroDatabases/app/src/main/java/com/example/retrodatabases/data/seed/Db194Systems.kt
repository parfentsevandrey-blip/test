package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 100 to 110: Trajan's bridge over the lower Danube, built from 103 to 105 by Apollodorus of Damascus. */
val db194Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.SYSTEM,
        section = "Danube",
        title = "Trajan's bridge over the Danube at Drobeta, built by Apollodorus of Damascus",
        originalTitle = "Trajan's bridge over the Danube",
        author = "Apollodorus of Damascus",
        year = 105,
        summary = "Trajan's bridge crossed the lower Danube on twenty masonry piers that carried wooden arches, a work planned by Apollodorus of Damascus.",
        details = "The work is usually given as running from 103 to 105, and the site lay near the Iron Gates, at the present Drobeta-Turnu Severin. Accounts differ on the start, since one account dates Trajan's order to 104, and one modern argument holds that construction began at Drobeta but the bridge was never built.",
        rare = true,
    ),

)
