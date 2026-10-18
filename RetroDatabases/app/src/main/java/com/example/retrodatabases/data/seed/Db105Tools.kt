package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A tool of 992: the Chunhua Ge Tie, a set of model calligraphy compiled in the Song for students to copy. */
val db105Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.TOOL,
        section = "Calligraphy",
        title = "The Chunhua Ge Tie, a set of model calligraphy compiled for Emperor Taizong in 992",
        originalTitle = "Chunhua Ge Tie",
        author = "Emperor Taizong of Song and Wang Zhu",
        year = 992,
        summary = "The Chunhua Ge Tie is a set of model calligraphy from the Han to the Tang periods, compiled by order of Emperor Taizong of Song in 992 for students to copy.",
        details = "Wang Zhu is credited with the original compilation and the tracing copy, made on imperial instruction. Rubbings of the 992 engraving survive, but the sources do not say whether the carvings were in stone or in wood, and the surviving versions come from later engravings, several of them incomplete.",
        rare = true,
    ),

)
