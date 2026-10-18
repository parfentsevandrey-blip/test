package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1107: the Concordat of London and its settlement of the investiture dispute in England. */
val db94Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.PRACTICE,
        section = "Investiture",
        title = "The Concordat of London, Henry I's settlement of the investiture dispute",
        originalTitle = "Concordat of London",
        author = "Henry I and Anselm of Canterbury",
        year = 1107,
        summary = "The Concordat of London of 1107 ended the English quarrel over investiture: the king gave up investing bishops with ring and staff, while bishops did homage to him for their lands.",
        details = "Anselm and Henry I reached the settlement in 1107 after years of dispute and exile, and it kept royal homage while giving up the spiritual investiture. The terms are known from letters and later reports of the agreement.",
    ),

)
