package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 340: one Greek Bible, Codex Sinaiticus, copied in the fourth century and dated here to about 340. */
val db170Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Codex Sinaiticus, a Greek Bible on parchment dated about 340",
        originalTitle = "Codex Sinaiticus",
        author = "Greek scribes of the fourth century",
        year = 340,
        summary = "Codex Sinaiticus is one of the oldest nearly complete Greek Bibles, copied on parchment in the fourth century and found at Saint Catherine's monastery on Mount Sinai in 1844.",
        details = "Palaeographers place the copying in the fourth century, most often between about 330 and about 360, and 340 is a round middle date within that spread. The leaves were found at Saint Catherine's monastery in 1844 and are now divided between libraries in several countries.",
    ),

)
