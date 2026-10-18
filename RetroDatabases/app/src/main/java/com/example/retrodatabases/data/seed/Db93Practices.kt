package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practice of 1110: the stress rhythm that closed sentences in papal letters. */
val db93Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.PRACTICE,
        section = "Writing",
        title = "The cursus, the stress rhythm that closed sentences in papal letters",
        originalTitle = "Cursus",
        author = "The papal chancery",
        year = 1110,
        summary = "Papal letters of the early twelfth century ended their sentences with set stress patterns called the cursus, which came in three named forms.",
        details = "The three forms were called planus, tardus and velox, and the scribe chose one by the stress of the last words of a sentence. Accounts differ on when the chancery first applied the rule with regularity, so 1110 is a round date within the period of its use.",
    ),

)
