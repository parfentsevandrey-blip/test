package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1184 to 1188: a French Grail romance, an Old East Slavic lay, Irish and Persian books, a German romance of Aeneas and an Arabic travel account. */
val db86Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Chretien's Perceval, the French story of the Grail left unfinished",
        originalTitle = "Perceval, ou le Conte du Graal",
        author = "Chretien de Troyes",
        year = 1185,
        summary = "Chretien de Troyes wrote his Perceval in French verse during the 1180s, a romance of a young knight and the Grail that breaks off before its end.",
        details = "Scholars date the poem to about 1181 to 1190, and later writers added continuations that show how widely it was read. Accounts differ on why the poem stops, and 1185 is a midpoint of the dating range rather than a fixed date.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "Old East Slavic",
        title = "The Lay of Igor's Campaign, a poem of a princes' raid in 1185",
        originalTitle = "Slovo o polku Igoreve",
        author = "An unnamed poet of Rus'",
        year = 1185,
        summary = "The Lay of Igor's Campaign is an Old East Slavic poem about Prince Igor's failed raid of 1185 against the Polovtsy of the steppe.",
        details = "Most scholars date the poem to 1185 to 1187, though some place it later, and some scholars in the nineteenth century doubted its authenticity. The only known manuscript was destroyed in the fire of Moscow in 1812, so the text is known from the first printed edition of 1800 and from later copies.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "Gerald of Wales's Topography of Ireland, a Latin account of the island",
        originalTitle = "Topographia Hibernica",
        author = "Gerald of Wales",
        year = 1188,
        summary = "Gerald of Wales finished his Topography of Ireland in 1188, a Latin description of the island's land, peoples and marvels.",
        details = "Gerald divided the work into three distinctions and mixed natural description with stories that he presented as things he had seen or heard. Dates for its writing are given as 1185 to 1188, and 1188 is the year usually given for its completion.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Layla and Majnun, Nizami's verse tale of thwarted love",
        originalTitle = "Layla va Majnun",
        author = "Nizami Ganjavi",
        year = 1188,
        summary = "Nizami Ganjavi completed his Layla and Majnun in 1188, a Persian verse romance of two lovers whom their families keep apart.",
        details = "The poem is the third of Nizami's five long poems, known as the Khamsa, and it became a model for later poets in Persian and Turkish. The year 1188 is the completion date usually given, and the story follows a poet who loves a girl from a rival family.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Heinrich von Veldeke's Eneit, the first courtly romance in German",
        originalTitle = "Eneit",
        author = "Heinrich von Veldeke",
        year = 1185,
        summary = "Heinrich von Veldeke's Eneit, a German romance adapted from the French Roman d'Eneas, is often called the first courtly romance written in German.",
        details = "Most of the text was written by about 1175, when it was at the court of Cleves, and the finished poem is usually dated between 1184 and 1190. Scholars do not agree on the date of the French Eneas, which is placed anywhere from about 1150 to just before 1174.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Ibn Jubayr's Rihla, an Arabic travel account from Granada to Mecca",
        originalTitle = "Rihla",
        author = "Ibn Jubayr",
        year = 1184,
        summary = "Ibn Jubayr's Rihla is an Arabic travel account of a journey from Granada to Mecca and beyond, written in the mid-1180s.",
        details = "The journey is usually dated to 1183 to 1185, and the account of the roads and cities of the Islamic lands was put into writing soon afterwards. Accounts differ on the exact year of writing, so 1184 is a midpoint of the mid-1180s rather than a fixed date.",
        rare = true,
    ),

)
