package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 900 to 910: the Kokinshu of 905, the Nihon Sandai Jitsuroku of 901 written in Chinese, and Ibn al-Faqih's Arabic geography of about 903. */
val db114Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 114,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Kokinshu, the first imperial anthology of Japanese poems",
        originalTitle = "Kokin Wakashu",
        author = "Ki no Tsurayuki and three other compilers",
        year = 905,
        summary = "The Kokinshu, an anthology of Japanese poems compiled by order of Emperor Daigo, was completed in 905 and is the first of the imperial waka anthologies.",
        details = "Four compilers worked on it, Ki no Tsurayuki, Ki no Tomonori, Oshikochi no Mitsune and Mibu no Tadamine, and the anthology holds 1,111 poems in twenty books. Tsurayuki also wrote its preface in Japanese rather than Chinese, and the poems follow the thirty-one-sound pattern of the tanka form.",
    ),

    entry(
        databaseId = 114,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Nihon Sandai Jitsuroku, Japan's sixth official history, written in Chinese",
        originalTitle = "Nihon Sandai Jitsuroku",
        author = "Court historians of the Heian period",
        year = 901,
        summary = "The Nihon Sandai Jitsuroku, the sixth of Japan's national histories, was completed in 901 in classical Chinese and covers three reigns from 858 to 887.",
        details = "Its title means the veritable records of three reigns, and the emperors covered are Seiwa, Yozei and Koko. The work is usually dated to 901, runs to fifty volumes, and follows the five earlier national histories in its annalistic form.",
        rare = true,
    ),

    entry(
        databaseId = 114,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn al-Faqih's Kitab al-Buldan, an Arabic geography of cities, roads and lands",
        originalTitle = "Kitab al-Buldan",
        author = "Ibn al-Faqih al-Hamadhani",
        year = 903,
        summary = "Ibn al-Faqih's Book of Countries, an Arabic geography of the cities, roads and lands of the Islamic world, is dated about 903.",
        details = "The surviving text is an abridgment, so the exact year is approximate, and the date of about 903 is the one usually given. The book describes the cities and roads of the caliphate and the lands beyond its frontiers.",
        rare = true,
    ),

)
