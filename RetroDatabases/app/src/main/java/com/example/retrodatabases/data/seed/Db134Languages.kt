package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 700 to 710: Liu Zhiji's Shitong, a treatise on how history should be written, completed in 710. */
val db134Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 134,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Liu Zhiji's Shitong, a treatise on how history should be written, completed in 710",
        originalTitle = "Shitong",
        author = "Liu Zhiji",
        year = 710,
        summary = "Liu Zhiji's Shitong, a critical study of the sources, methods and rules of Chinese historical writing, was completed in 710.",
        details = "It is usually counted as the earliest Chinese treatise of historiographical criticism, and its original 52 chapters fall into 39 inner and 13 outer parts, of which three inner chapters are lost. Liu Zhiji, who lived from 661 to 721, served as a scholar-official in the capital's academies, and accounts date the composition to about 708 to 710.",
        rare = true,
    ),

)
