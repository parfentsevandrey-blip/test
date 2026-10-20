package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of the 140s: the Roman sitella, an urn for drawing lots, placed at 142 with the start of the Antonine Wall. */
val db190Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 190,
        kind = Kind.DEVICE,
        section = "Lots",
        title = "The Roman sitella, an urn for drawing the lots of the assemblies",
        originalTitle = "Sitella",
        author = "The Roman assemblies",
        year = 142,
        summary = "The sitella was a Roman urn used for drawing lots, and it is known from Roman texts and from coins.",
        details = "Roman texts name the sitella as the urn for drawing the lots that named the tribes or centuries, and modern writers often confuse it with the ballot box. Late Republican coins picture voters dropping tablets into urns. The sources do not date this urn; 142 is the year of the anchor, the start of the Antonine Wall, not of the making.",
        rare = true,
    ),

)
