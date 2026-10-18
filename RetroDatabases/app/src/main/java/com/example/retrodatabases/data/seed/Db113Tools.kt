package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 910 to 920: a Japanese glossary of the native names of Chinese medicinal drugs, usually dated 918. */
val db113Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 113,
        kind = Kind.TOOL,
        section = "Japan",
        title = "Honzo wamyo, a Japanese list of the native names of Chinese drugs",
        originalTitle = "Honzo wamyo",
        author = "Fukane no Sukehito",
        year = 918,
        summary = "Fukane no Sukehito set out the native Japanese names of Chinese medicinal drugs in a glossary usually dated to 918.",
        details = "Each entry pairs a Chinese drug name with the name the drug went by in Japan, so a reader could look up a remedy under either name. The usual date for the compilation is 918, and the usual attribution is to Fukane no Sukehito.",
        rare = true,
    ),

)
