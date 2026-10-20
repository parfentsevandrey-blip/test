package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 510 to 520: Zu Chongzhi's Daming calendar as the Liang court approved it in 510, and Sengyou's catalogue of Buddhist scriptures of 515. */
val db153Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 153,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "Zu Chongzhi's Daming calendar, first presented in 462 and approved by the Liang court in 510 after his son's campaign",
        originalTitle = "Daming li",
        author = "Zu Chongzhi and his son Zu Geng",
        year = 510,
        summary = "Zu Chongzhi's Daming calendar, first presented in 462, was approved for official use by the Liang court in 510, after his son Zu Geng had pressed for it from 504.",
        details = "The presentation is dated to about 462 under the Liu Song, although some accounts give 465, so the starting year is disputed. The year 510 is the first official use of the calendar, and one account gives the name Jiazi yuanli for it as the Liang adopted it, while accounts differ on its name. The calendar stayed in use until 589.",
        rare = true,
    ),

    entry(
        databaseId = 153,
        kind = Kind.TOOL,
        section = "Chinese",
        title = "Sengyou's Chu sanzang jiji, a catalogue of the Buddhist scriptures translated into Chinese, completed in 515",
        originalTitle = "Chu sanzang jiji",
        author = "Sengyou (445-518)",
        year = 515,
        summary = "Sengyou compiled the Chu sanzang jiji, a catalogue of the Buddhist scriptures translated into Chinese, with notes on their translators, in 515.",
        details = "Sengyou was a monk at Jiankang, the capital of the Liang, and the catalogue is among the earliest surviving Chinese bibliographies of Buddhist scriptures. It is usually dated to 515, the year of its completion, and it names the translators of the texts it lists.",
        rare = true,
    ),

)
