package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 650 to 660: the Ten Computational Canons, the mathematics textbooks that Li Chunfeng annotated and submitted in 656. */
val db139Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "The Ten Computational Canons, the mathematics textbooks annotated by Li Chunfeng and submitted to the throne in 656",
        originalTitle = "Suanjing shishu",
        author = "Li Chunfeng with Liang Shu and Wang Zhenru",
        year = 656,
        summary = "The ten mathematics texts of the Tang, annotated by Li Chunfeng with Liang Shu and Wang Zhenru, were submitted to the throne in 656 and taught at the imperial university.",
        details = "An astronomer's memorial found the older texts full of errors, and an imperial order followed, so the set brings together four earlier works, the Nine Chapters among them, with six more titles. Students at the Tang imperial university studied it, and one account dates the name Ten Classics to 1084.",
        rare = true,
    ),

)
