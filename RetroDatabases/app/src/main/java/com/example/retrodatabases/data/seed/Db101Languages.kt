package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1030 to 1036: a Tangut writing system, al-Biruni's study of India and an Old Javanese court poem. */
val db101Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.LANGUAGE,
        section = "Writing",
        title = "The Tangut script, a writing system issued in 1036 for the Western Xia state",
        originalTitle = "Tangut script",
        author = "Li Yuanhao, ruler of Western Xia",
        year = 1036,
        summary = "The Tangut script is a writing system of about 6,000 characters that was issued in 1036 for the Tangut state of Western Xia.",
        details = "The characters were made to write the Tangut language, and their shapes echo Chinese characters without being Chinese. The Mongols ended the state in 1227, and the script faded from use after that.",
        rare = true,
    ),

    entry(
        databaseId = 101,
        kind = Kind.LANGUAGE,
        section = "Arabic science",
        title = "al-Biruni's Kitab al-Hind, an Arabic study of Indian religion, science and customs",
        originalTitle = "Kitab tahqiq ma li-l-Hind",
        author = "al-Biruni",
        year = 1030,
        summary = "Al-Biruni's Kitab al-Hind is an Arabic study of Indian religion, science and customs, written about 1030 after his years in northern India.",
        details = "The author sets Indian doctrines and astronomy beside Greek and Persian ideas. The completion date is not fixed, so about 1030 is the usual estimate.",
        rare = true,
    ),

    entry(
        databaseId = 101,
        kind = Kind.LANGUAGE,
        section = "Old Javanese",
        title = "The Arjunawiwaha, an Old Javanese kakawin composed at the court of Airlangga",
        originalTitle = "Arjunawiwaha",
        author = "Mpu Kanwa",
        year = 1030,
        summary = "The Arjunawiwaha is a long Old Javanese poem in classical metres, composed about 1030 by Mpu Kanwa at the court of King Airlangga of East Java.",
        details = "The poem is built around Arjuna's penance on a mountain, and its closing stanza names Kanwa as the poet. Airlangga's rule in East Java is dated 1019 to 1042, so the date of about 1030 is an approximation.",
        rare = true,
    ),

)
