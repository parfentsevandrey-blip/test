package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 520 to 530: Boethius's Consolation of Philosophy, written in prison about 524, and Li Daoyuan's Commentary on the Water Classic, dated before his death in 527. */
val db152Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 152,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Boethius's Consolation of Philosophy, a Latin work of prose and verse written in prison while its author awaited execution",
        originalTitle = "De consolatione philosophiae",
        author = "Boethius",
        year = 524,
        summary = "The Consolation of Philosophy, written by the Roman statesman Boethius in prison, alternates prose and verse in five books of dialogue with Lady Philosophy.",
        details = "Boethius wrote it in prison near Pavia in 523 and 524, while he awaited the execution that the Ostrogothic king Theoderic ordered. He was executed in 524 or 525, and accounts differ on the year. The book was widely copied and translated in the Middle Ages.",
    ),

    entry(
        databaseId = 152,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Li Daoyuan's Commentary on the Water Classic, a gazetteer of rivers, waterways and the places along them",
        originalTitle = "Shuijing zhu",
        author = "Li Daoyuan",
        year = 527,
        summary = "Li Daoyuan's Commentary on the Water Classic expands an older book of rivers into a gazetteer of waterways, towns, roads and local history.",
        details = "Its date is not firmly fixed, and this record uses 527, the year Li Daoyuan died, as the end of the early sixth-century range usually given for it. The expanded edition is said to fill forty scrolls and to describe about 1,250 waterways. The older base text, the Water Classic, is traditionally linked to Sang Qin, and scholars date that base text in different ways.",
        rare = true,
    ),

)
