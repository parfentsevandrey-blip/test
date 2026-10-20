package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 110 to 120: the Yiwen zhi, the book catalogue in Ban Gu's Hanshu, in the year its completion is given as 111. */
val db193Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.TOOL,
        section = "Books",
        title = "The Yiwen zhi, the book catalogue in Ban Gu's Hanshu",
        originalTitle = "Yiwen zhi",
        author = "Ban Gu",
        year = 111,
        summary = "The Yiwen zhi, the treatise on arts and letters in the Book of Han, lists the imperial library's books in six parts.",
        details = "The treatise draws on Liu Xin's Seven Epitomes, a catalogue whose collation the court began in 26 BCE. Ban Gu died in 92 with the Han history unfinished, and 111 is the completion date that one summary gives for the Book of Han; accounts differ on how much Ban Zhao and Ma Xu added to its tables and treatises.",
        rare = true,
    ),

)
