package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 660 to 670: Xuanzang's Great Prajnaparamita sutra in 600 fascicles, translated in the 660s. */
val db138Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Xuanzang's Great Prajnaparamita sutra in 600 fascicles, one of his last and longest translations, made in the 660s",
        originalTitle = "Da bore boluomiduo jing",
        author = "Xuanzang",
        year = 663,
        summary = "Xuanzang's Chinese version of the Mahaprajnaparamita sutra, in 600 fascicles, was among the last and longest of his translations.",
        details = "Secondary sources date the work to 660 to 663, and this record uses 663, the end of that span, as its year. Xuanzang, who lived from about 602 to 664, directed the work.",
        rare = true,
    ),

)
