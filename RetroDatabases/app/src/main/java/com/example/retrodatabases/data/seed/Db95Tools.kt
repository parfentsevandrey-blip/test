package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1094: Su Song's illustrated book on the armillary sphere and the celestial globe of his clock tower. */
val db95Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Su Song's New Design of the Armillary Sphere and Celestial Globe",
        originalTitle = "Xin Yixiang fa yao",
        author = "Su Song",
        year = 1094,
        summary = "Su Song's illustrated book Xin Yixiang fa yao describes the armillary sphere and the celestial globe of his clock tower, with drawings of their parts and their working.",
        details = "Accounts differ on the year: some date the book to 1094 and others to 1092, the year the tower was completed. The book is the main surviving description of the instruments of the tower.",
        rare = true,
    ),

)
