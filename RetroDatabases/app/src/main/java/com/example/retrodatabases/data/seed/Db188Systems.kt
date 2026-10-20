package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 160 to 170: the Antonine plague, an epidemic that spread across the Roman world from about 165. */
val db188Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 188,
        kind = Kind.SYSTEM,
        section = "Epidemics",
        title = "The Antonine plague, an epidemic that spread across the Roman world from about 165",
        originalTitle = "Antonine plague",
        author = "No single author",
        year = 165,
        summary = "The Antonine plague spread across the Roman world from about 165 and killed great numbers over the following years.",
        details = "Accounts link its arrival to the army of Lucius Verus returning from the eastern war, and Galen describes its effects in the cities he knew. The first year is usually given as 165, and the death toll survives only as rough figures.",
    ),

)
