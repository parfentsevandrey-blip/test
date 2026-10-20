package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 274: a Roman bronze dodecahedron, a hollow twelve-faced object of unknown use, placed at the year of Aurelian's festival. */
val db177Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.DEVICE,
        section = "Objects",
        title = "The Roman bronze dodecahedron, a hollow twelve-faced object of unknown use",
        originalTitle = "Roman dodecahedron",
        author = "Unknown makers in the Roman provinces",
        year = 274,
        summary = "The Roman bronze dodecahedron is a hollow object with twelve pierced faces whose use is unknown, and it is placed here at 274, the year of Aurelian's festival.",
        details = "The sources do not date this device precisely; 274 is the year of the anchor, Aurelian's festival, and not the year of the making. Estimates of its date cluster in the second to fourth centuries, most finds come from former Gallo-Roman territory, and more than fifty uses have been proposed, from surveying to dice, without agreement.",
        rare = true,
    ),

)
