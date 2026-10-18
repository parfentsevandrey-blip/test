package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 980 to 990: a Song encyclopedia, a Persian geography, a Chinese dictionary, a Japanese book of Buddhist tales and an Arabic geography. */
val db106Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 106,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "The Taiping Yulan, a Song encyclopedia of 1000 juan in 55 sections",
        originalTitle = "Taiping yulan",
        author = "Li Fang and the Song editors",
        year = 983,
        summary = "The Taiping Yulan, an encyclopedia of 1000 juan compiled for Emperor Taizong of the Song, was completed in 983.",
        details = "The compilation is dated 977 to 983, though one account gives 984 as the year of completion. Its material is arranged in 55 sections by subject, and it quotes a great many earlier books, some of which survive mainly through its quotations.",
    ),

    entry(
        databaseId = 106,
        kind = Kind.LANGUAGE,
        section = "Persia",
        title = "The Hudud al-'Alam, an anonymous geography of the regions of the world in Persian",
        originalTitle = "Hudud al-'Alam",
        author = "Anonymous",
        year = 982,
        summary = "The Hudud al-'Alam, The Regions of the World, is an anonymous geography written in New Persian in 982 or 983, one of the earliest of its kind in that language.",
        details = "The text is dated to 372 AH, which falls in 982 or 983 CE, and the author is not named in the surviving copies. It is among the first geographies written in Persian rather than Arabic.",
        rare = true,
    ),

    entry(
        databaseId = 106,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "Xu Xuan's revised Shuowen jiezi, the dictionary of characters under 540 radicals",
        originalTitle = "Shuowen jiezi",
        author = "Xu Shen and Xu Xuan",
        year = 986,
        summary = "Xu Xuan revised the Shuowen jiezi of Xu Shen, a dictionary of characters arranged under 540 radicals, and presented the revised text to the Song court in 986.",
        details = "Xu Shen's original dates to about 100 CE, and its arrangement of characters by radical shaped later dictionaries. Xu Xuan's revision added a fanqie spelling for many characters, and it is the edition that most later readers use.",
        rare = true,
    ),

    entry(
        databaseId = 106,
        kind = Kind.LANGUAGE,
        section = "Japan",
        title = "The Sanbo-e, a Japanese Buddhist book of tales by Minamoto no Tamenori",
        originalTitle = "Sanbo-e",
        author = "Minamoto no Tamenori",
        year = 984,
        summary = "Minamoto no Tamenori completed the Sanbo-e in 984, a Buddhist book of stories and teachings from Heian Japan.",
        details = "Its title means a picture of the Three Treasures, the Buddha, the Dharma and the Sangha. It is one of the early Japanese collections of Buddhist tales, written in the Heian period.",
        rare = true,
    ),

    entry(
        databaseId = 106,
        kind = Kind.LANGUAGE,
        section = "Islamic lands",
        title = "Al-Muqaddasi's Best Divisions, a geography of the Islamic lands written in Arabic",
        originalTitle = "Ahsan al-taqasim fi ma'rifat al-aqalim",
        author = "Al-Muqaddasi",
        year = 985,
        summary = "Al-Muqaddasi finished The Best Divisions for Knowledge of the Regions in 985, a geography of the Islamic provinces based on his own travels.",
        details = "The book describes provinces, cities, roads and markets across the Islamic lands, and its author draws on his own journeys. The title in Arabic means the best divisions for knowledge of the regions.",
        rare = true,
    ),

)
