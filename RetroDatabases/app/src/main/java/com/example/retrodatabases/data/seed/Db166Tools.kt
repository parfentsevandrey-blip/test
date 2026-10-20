package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 380 to 390: Jerome's Latin Onomasticon, a finding list of the place names of the Bible. */
val db166Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.TOOL,
        section = "Geography",
        title = "Jerome's Latin Onomasticon, a finding list of the places named in the Bible",
        originalTitle = "Onomasticon",
        author = "Jerome, after Eusebius of Caesarea",
        year = 390,
        summary = "Jerome's Latin version of Eusebius's Onomasticon, a list of the places named in the Bible, was made about 390 as a reference for readers of scripture.",
        details = "Eusebius compiled the Greek Onomasticon in the early fourth century, arranging the biblical place names in alphabetical order with notes on their sites. Jerome translated it into Latin and corrected several of its identifications, and the usual date for his version is about 390. Its identifications of biblical sites are still debated in places.",
        rare = true,
    ),

)
