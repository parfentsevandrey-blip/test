package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 350 to 359: Wang Xizhi's preface to the Lanting poems, a Chinese prose text of 353. */
val db169Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 169,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Wang Xizhi's preface to the Lanting poems, Chinese prose on a gathering of 353",
        originalTitle = "Lanting ji xu",
        author = "Wang Xizhi",
        year = 353,
        summary = "Wang Xizhi's preface to the poems of the Lanting gathering, dated to the ninth year of the Yonghe era, which is 353, is one of the most famous pieces of Chinese calligraphy.",
        details = "The gathering met at the Orchid Pavilion near Shaoxing, and the preface describes the wine, the poems and the thoughts of the guests. The autograph is lost, and the best-known versions are later copies, so scholars dispute how closely their wording follows Wang's own text.",
    ),

)
