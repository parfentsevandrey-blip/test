package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 0 to 10: one placed device, the warp-weighted loom of the Roman provinces, placed at 4 for the death of Gaius Caesar. */
val db204Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 204,
        kind = Kind.DEVICE,
        section = "Textiles",
        title = "The warp-weighted loom of the Roman provinces, undated in the sources and placed at 4",
        originalTitle = "Warp-weighted loom",
        author = "Weavers of the Roman provinces",
        year = 4,
        summary = "The warp-weighted loom hangs its threads from a beam and holds them taut with clay weights, and the record is placed at 4, the year Gaius Caesar died, not the year of the loom.",
        details = "Clay loom weights are common finds on Roman provincial sites, but the sources do not date this loom or its first use. The anchor is the death of Gaius Caesar, the adopted heir of Augustus, in 4, so 4 is the year of the anchor and not of the making.",
        rare = true,
    ),

)
