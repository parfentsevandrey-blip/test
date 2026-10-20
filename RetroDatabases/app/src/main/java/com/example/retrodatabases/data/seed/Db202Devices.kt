package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 20 to 30: the Roman bilanx, a two-pan balance, placed at 26 by the anchor of Pilate's office in Judaea. */
val db202Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 202,
        kind = Kind.DEVICE,
        section = "Weighing",
        title = "The bilanx, a Roman two-pan balance for weighing goods",
        originalTitle = "Bilanx",
        author = "Roman traders",
        year = 26,
        summary = "The bilanx was a Roman two-pan balance that weighed goods against standard weights in trade.",
        details = "The sources do not date this kind of balance, so 26 is the year of the anchor, the year Pontius Pilate took office in Judaea, and not the year any balance was made. The beam hangs from its centre, so the goods in one pan and the standard weights in the other are compared on equal arms.",
        rare = true,
    ),

)
