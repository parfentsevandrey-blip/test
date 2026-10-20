package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 480 to 490: Zhang Qiujian's Mathematical Classic, an arithmetic handbook whose date is given as a window ending in 485. */
val db156Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 156,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Zhang Qiujian's Mathematical Classic, an arithmetic handbook of problems written about 466 to 485",
        originalTitle = "Zhang Qiujian suanjing",
        author = "Zhang Qiujian",
        year = 485,
        summary = "The Mathematical Classic of Zhang Qiujian is a handbook of arithmetic problems, and its closing problem is a puzzle of a hundred fowls bought with a hundred coins.",
        details = "Its date is given as a window from about 466 to 485, and 485 is used here as the end of that range, while some sources put the book in the late fifth or early sixth century. The closing puzzle has several whole-number answers, with cocks, hens and chicks counted together.",
        rare = true,
    ),

)
