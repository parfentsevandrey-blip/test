package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 30 to 40: a Roman pumice stone for smoothing writing surfaces, placed at 37 because the sources do not date it. */
val db201Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.TOOL,
        section = "Writing",
        title = "The Roman pumice stone for smoothing writing surfaces, undated in the sources and placed at 37",
        originalTitle = "pumex",
        author = "Roman scribes",
        year = 37,
        summary = "The pumice stone was a light volcanic stone that Roman scribes rubbed over a writing surface to smooth it.",
        details = "The sources do not date any particular stone, so 37, the accession of Caligula, is the year of the anchor and not of the making. A smooth surface was needed for ink on papyrus and parchment, and the stone shows the care a scribe took before writing.",
        rare = true,
    ),

)
