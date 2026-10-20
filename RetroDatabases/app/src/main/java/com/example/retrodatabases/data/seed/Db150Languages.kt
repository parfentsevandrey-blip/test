package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 540 to 550: the Rule of Saint Benedict, written about 540, and Jia Sixie's Qimin yaoshu, a Chinese farming handbook completed about 544. */
val db150Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 150,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Rule of Saint Benedict, a short Latin rule for monks with a prologue and 73 chapters, written about 540",
        originalTitle = "Regula Benedicti",
        author = "Benedict of Nursia",
        year = 540,
        summary = "The Rule of Saint Benedict is a short Latin rule for a monastic community that sets out its prayer, reading, work and daily order in a prologue and 73 chapters.",
        details = "Benedict of Nursia wrote it for a community he led, most likely at Monte Cassino, and its date is uncertain: accounts place it between about 530 and 540, and 540 is the year used here. Its chapters cover the abbot, the Divine Office, obedience, silence and manual labour, and it became the standard rule of western monasteries in the Carolingian period.",
    ),

    entry(
        databaseId = 150,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Jia Sixie's Qimin yaoshu, a Chinese farming handbook in ten volumes and 92 chapters, completed about 544",
        originalTitle = "Qimin yaoshu",
        author = "Jia Sixie",
        year = 544,
        summary = "The Qimin yaoshu is a Chinese handbook of farming, orchards, silk, livestock and food processing, completed about 544 in ten volumes and 92 chapters.",
        details = "The sources give 544 under the Eastern Wei as the completion date, while some accounts give a span of 533 to 544, so the year is disputed. Its methods are set out with measures and reasons, and it is one of the oldest complete agricultural treatises of China.",
        rare = true,
    ),

)
