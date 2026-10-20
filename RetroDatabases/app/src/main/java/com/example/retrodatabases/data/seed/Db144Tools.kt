package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 600 to 610: the calendar books that the Paekche monk Kanroku brought to the Japanese court in 602. */
val db144Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.TOOL,
        section = "Japanese",
        title = "The calendar books that the Paekche monk Kanroku brought to the Japanese court in 602",
        originalTitle = "rekihon",
        author = "Kanroku (Gwalleuk), Paekche monk",
        year = 602,
        summary = "Kanroku, a monk from the Korean kingdom of Paekche, presented the Japanese court in 602 with calendar books and texts on astronomy, geography and divination.",
        details = "The Nihon Shoki records the gift in the tenth year of Empress Suiko, which is 602, and it presents Kanroku as an authority on astronomy and numerology who taught pupils. Some accounts date the first use of a calendar in Japan to 604, and scholars differ on which calendar the books contained, and one proposal, which is only a presumption, names the Chinese Yuanjia (Genka) calendar.",
        rare = true,
    ),

)
