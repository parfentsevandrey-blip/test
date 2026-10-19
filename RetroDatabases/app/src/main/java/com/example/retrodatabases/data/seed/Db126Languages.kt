package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 780 to 790: the Nestorian stele of Xi'an of 781, Malik's Muwatta, the Kitab al-'Ayn and the Harivamsa Purana of 783. */
val db126Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 126,
        kind = Kind.LANGUAGE,
        section = "Chinese and Syriac",
        title = "The Nestorian stele of Xi'an, a Chinese and Syriac monument to the Church of the East in Tang China, erected in 781",
        originalTitle = "Da Qin Jingjiao liuxing Zhongguo bei song",
        author = "Adam, known in Chinese as Jingjing, a priest of the Church of the East",
        year = 781,
        summary = "The Nestorian stele of Xi'an, a stone inscription in Chinese and Syriac, was erected in 781 and tells the story of the Church of the East in China from the arrival of Alopen in 635.",
        details = "The stele was rediscovered near Xi'an in 1625, and its account of the mission begins with Alopen, a Persian missionary whose arrival is placed in 635. Several accounts date the monument to 781, and the Syriac part is dated by the Greek era, the reckoning the Syriac churches used.",
    ),

    entry(
        databaseId = 126,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Malik ibn Anas's Muwatta, a collection of hadith and legal practice from Medina, first compiled about 780",
        originalTitle = "al-Muwatta",
        author = "Malik ibn Anas",
        year = 780,
        summary = "Malik ibn Anas's Muwatta, a collection of hadith and legal practice from Medina, is usually dated to about 780.",
        details = "Malik died in 795 and went on revising the book during his lifetime, so the date of its first form is uncertain. The text arranges its reports by legal topic, and later collections of hadith were often compared with it.",
    ),

    entry(
        databaseId = 126,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Kitab al-'Ayn, the first Arabic dictionary, ascribed to al-Khalil ibn Ahmad of Basra",
        originalTitle = "Kitab al-'Ayn",
        author = "Al-Khalil ibn Ahmad al-Farahidi (ascribed)",
        year = 786,
        summary = "Kitab al-'Ayn, the first Arabic dictionary, is ascribed to al-Khalil ibn Ahmad al-Farahidi, whose death is given as 786 in some accounts and as 791 in others.",
        details = "The book's entries run in order of articulation, from the throat letter ayn to the labial mim, and a pupil may have finished it after the author's death. The year 786 is therefore a placed year, the author's death, and not a proven date for the book's first form.",
        rare = true,
    ),

    entry(
        databaseId = 126,
        kind = Kind.LANGUAGE,
        section = "Sanskrit",
        title = "Jinasena's Harivamsa Purana, a Sanskrit Jain history centered on the Tirthankara Neminatha, completed in 783",
        originalTitle = "Harivamsa Purana",
        author = "Jinasena",
        year = 783,
        summary = "Jinasena's Harivamsa Purana, a Sanskrit Jain history centered on the Tirthankara Neminatha, was completed in 783, which its colophon gives as Saka 705.",
        details = "The poem has 66 cantos and about 12,000 verses, and its colophon names four rulers, Indrayudha, Srivallabha, Vatsaraja and Varaha. Its composition is said to have begun at Vardhamanapura and ended at Dostatika, and sources number the author as Jinasena I or Jinasena II.",
        rare = true,
    ),

)
