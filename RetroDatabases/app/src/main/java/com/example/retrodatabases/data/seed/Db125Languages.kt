package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 790 to 800: the Shoku Nihongi of 797 and the first Arabic version of Euclid's Elements, about 800. */
val db125Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 125,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Shoku Nihongi, the second of Japan's national histories, completed in 797 and covering the years 697 to 791",
        originalTitle = "Shoku Nihongi",
        author = "Sugano no Mamichi and others",
        year = 797,
        summary = "The Shoku Nihongi, the second of Japan's six national histories, was completed in 797 and records the years 697 to 791 in Classical Chinese.",
        details = "It continues the Nihon Shoki of 720 in the same Chinese style, and Sugano no Mamichi is named as the leading compiler in the usual accounts. The year 797 is the usual date of its completion, and the span from 697 to 791 is the period its entries cover.",
    ),

    entry(
        databaseId = 125,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The first Arabic version of Euclid's Elements, attributed to al-Hajjaj ibn Yusuf under Harun al-Rashid, about 800",
        originalTitle = "Stoicheia",
        author = "Al-Hajjaj ibn Yusuf ibn Matar (attributed)",
        year = 800,
        summary = "The first Arabic version of Euclid's Elements, a Greek geometry textbook, is attributed to al-Hajjaj ibn Yusuf and dated to Harun al-Rashid's reign, about 800.",
        details = "Medieval writers name Harun al-Rashid or his vizier Yahya ibn Khalid al-Barmaki as the patron, and the sources tie the work to Harun's reign, from 786 to 809, rather than to one year. Al-Hajjaj later prepared a second version or revision for al-Ma'mun in the 820s, and copies that appear to trace back to Thabit ibn Qurra's revision of Ishaq ibn Hunayn's translation still contain material traceable to al-Hajjaj.",
        rare = true,
    ),

)
