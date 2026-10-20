package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 353: the cup game of the Lanting gathering, where a cup that stopped before a guest called for a poem. */
val db169Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.PRACTICE,
        section = "Poetry",
        title = "The winding-stream cup game at the Lanting gathering, where guests wrote poems",
        originalTitle = "Liushang qushui",
        author = "Wang Xizhi and his guests",
        year = 353,
        summary = "At the Lanting gathering of 353 guests set wine cups afloat on a winding stream, and a guest whose cup stopped before him had to write a poem.",
        details = "The custom is described in Wang Xizhi's preface, which is the main source for the gathering. It was a party game of poetry and wine, not a rule of the state, and the cup stopping before a guest was its signal for the poem.",
        rare = true,
    ),

)
