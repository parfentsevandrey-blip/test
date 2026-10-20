package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 410 to 418: Martianus Capella's allegory of the marriage of Philology and Mercury, read as a school handbook. */
val db163Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.TOOL,
        section = "Learning",
        title = "Martianus Capella's Marriage of Philology and Mercury, a school handbook of learning",
        originalTitle = "De nuptiis Philologiae et Mercurii",
        author = "Martianus Capella",
        year = 410,
        summary = "Martianus Capella's Marriage of Philology and Mercury mixes prose and verse in an allegory of a wedding, and it became a school handbook in the Middle Ages.",
        details = "In Martianus's book the bride Philology receives personified branches of learning as wedding gifts and attendants, and medieval schools read it as a guide to them. Scholars date the work between about 410 and about 439, and this record uses 410, the earliest date proposed, because the year of first publication is not fixed.",
        rare = true,
    ),

)
