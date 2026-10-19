package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 720 to 730: the Nihon Shoki of 720 and an anonymous Syriac world chronicle titled to the year 724. */
val db132Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 132,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Nihon Shoki, the first of Japan's six national histories, completed in 720",
        originalTitle = "Nihon Shoki",
        author = "Prince Toneri and others",
        year = 720,
        summary = "The Nihon Shoki, a Japanese chronicle written in Classical Chinese in thirty volumes, was completed in 720 and is the first of the six national histories.",
        details = "It covers the mythical past down to 697 and was compiled under Prince Toneri with other scholars. The date 720 is the usual one for its presentation to the court, and it set the pattern for the later national histories.",
    ),

    entry(
        databaseId = 132,
        kind = Kind.LANGUAGE,
        section = "Syriac",
        title = "The Chronicle to the year 724, an anonymous Syriac world history kept as British Library Add. 14643",
        originalTitle = "Chronicon anonymum ad annum 724",
        author = "Anonymous",
        year = 724,
        summary = "The Chronicle to the year 724 is an anonymous Syriac world history of the eighth century, named after the year in its title.",
        details = "The manuscript is British Library Add. 14643, and the text also circulates as the Book of the Caliphs. Scholars differ on what the year 724 marks, whether a later copy or a list of caliphs appended to the text rather than the main narrative, and accounts differ on how far that narrative runs.",
        rare = true,
    ),

)
