package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Language of 1050: the Elementarium, a Latin word book by the eleventh-century grammarian Papias. */
val db99Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 99,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Elementarium of Papias, a Latin word book of about 1050",
        originalTitle = "Elementarium doctrinae rudimentis",
        author = "Papias the grammarian",
        year = 1050,
        summary = "Papias, an eleventh-century grammarian, compiled the Elementarium, a Latin word book that gave the meanings and sources of hard words.",
        details = "Accounts differ on its date: some place an early version in 1045, while others put the finished work about the middle of the century, so the year here is that estimate. The author's dates are unknown, and he is often called Papias the Lombard, although where he came from is uncertain.",
        rare = true,
    ),

)
