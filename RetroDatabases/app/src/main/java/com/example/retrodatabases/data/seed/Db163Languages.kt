package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 410 to 418: Faxian's account of his journey to India and the Gwanggaeto stele of Goguryeo, both written in Chinese characters. */
val db163Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 163,
        kind = Kind.LANGUAGE,
        section = "Travel",
        title = "Faxian's Record of Buddhist Kingdoms, an account of his journey to India",
        originalTitle = "Foguo ji",
        author = "Faxian",
        year = 412,
        summary = "Faxian's Foguo ji tells of his journey to India for Buddhist texts and of his return by sea, and it was written after he reached China again in 412.",
        details = "Faxian left Chang'an in 399 and spent years in India and Sri Lanka before he sailed home, reaching the coast of Shandong in 412. He then wrote his account at Jiankang, the city that is now Nanjing. The year of its completion is uncertain, so this record uses 412, the year of his return, as the earliest date the book can have.",
    ),

    entry(
        databaseId = 163,
        kind = Kind.LANGUAGE,
        section = "Korean",
        title = "The Gwanggaeto stele, a Korean inscription in Chinese characters",
        originalTitle = "Gwanggaeto Taewang bi",
        author = "King Jangsu of Goguryeo",
        year = 414,
        summary = "The Gwanggaeto stele, set up in the old Goguryeo lands in 414, carries a long inscription in Chinese characters in honour of King Gwanggaeto.",
        details = "King Jangsu erected the stone in 414 to honour his father, and its text is written in Chinese characters. Accounts differ on how parts of the inscription should be read, and rubbings made in the 1880s by Japanese army officers are at the centre of a long dispute over whether the text was retouched.",
        rare = true,
    ),

)
