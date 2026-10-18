package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 950 to 960: al-Uqlidisi's arithmetic written at Damascus, and a Byzantine handbook of government. */
val db109Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 109,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Al-Uqlidisi's Book of Chapters on Indian Arithmetic, written at Damascus",
        originalTitle = "Kitab al-Fusul fi al-hisab al-hindi",
        author = "Abu'l-Hasan Ahmad ibn Ibrahim al-Uqlidisi",
        year = 952,
        summary = "Al-Uqlidisi wrote his Book of Chapters on Indian Arithmetic at Damascus in 952 or 953, and it is the oldest surviving Arabic work on the Hindu system of numerals.",
        details = "The book gives what is taken as the earliest direct treatment of decimal fractions, and it uses a bar over the units digit as the decimal marker. Most of what is known of the book rests on one manuscript held in Istanbul, and accounts date that copy to 1157 or to 1186.",
        rare = true,
    ),

    entry(
        databaseId = 109,
        kind = Kind.TOOL,
        section = "Byzantium",
        title = "De administrando imperio, a Greek handbook of government for an emperor's son",
        originalTitle = "De administrando imperio",
        author = "Constantine VII Porphyrogenitus",
        year = 952,
        summary = "Constantine VII wrote De administrando imperio, a Greek handbook on governing the empire and dealing with its neighbours, for his son Romanos, and it is usually dated from 948 to 952.",
        details = "The handbook gives advice on the peoples around the empire and beyond its borders, and it was written for the emperor's son rather than for the public. The year 952 is the end of the usual range and is used here as the year of the finished work.",
        rare = true,
    ),

)
