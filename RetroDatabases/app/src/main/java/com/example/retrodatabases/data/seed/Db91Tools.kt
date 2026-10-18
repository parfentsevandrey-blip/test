package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1134: Li Qingzhao's catalogue of bronze and stone inscriptions, completed after her husband's death. */
val db91Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 91,
        kind = Kind.TOOL,
        section = "Inscriptions",
        title = "Li Qingzhao's Jinshi lu, a catalogue of bronze and stone inscriptions",
        originalTitle = "Jinshi lu",
        author = "Zhao Mingcheng and Li Qingzhao",
        year = 1134,
        summary = "The Jinshi lu catalogues bronze and stone inscriptions with notes on their texts, dates and sources, and Li Qingzhao's postscript is usually dated 1134.",
        details = "Zhao Mingcheng began the collection and died in 1129 before the catalogue was finished, so Li Qingzhao completed it after his death. Accounts differ on how much of the final work was his and how much was added by his widow, so the 1134 date marks the completion rather than the start.",
        rare = true,
    ),

)
