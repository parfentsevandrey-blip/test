package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 10 to 20: Augustus's census of 14, which counted the Roman citizens on the roll, with the counts that came before it. */
val db203Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 203,
        kind = Kind.SYSTEM,
        section = "Counting",
        title = "Augustus's census of 14, which counted 4,937,000 Roman citizens",
        originalTitle = "Census of 14",
        author = "Augustus, with Tiberius as co-censor",
        year = 14,
        summary = "The census of 14 logged 4,937,000 Roman citizens on the roll, the largest of the three counts that Augustus describes in chapter 8 of his Res Gestae.",
        details = "Chapter 8 gives the earlier counts of 28 BC, 4,063,000, and 8 BC, 4,233,000, and one translation prints the 8 BC figure as 4,230,000, so accounts differ slightly on that number. The total counts Roman citizens on the roll, not the whole population of the empire, and it was taken with Tiberius sharing the office.",
        rare = true,
    ),

)
