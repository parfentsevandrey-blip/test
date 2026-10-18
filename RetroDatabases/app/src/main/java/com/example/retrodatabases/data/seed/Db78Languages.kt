package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1260 to 1270: a square alphabet for the Mongol court, a French encyclopedia, a Latin book of saints' lives and a Latin defence of the faith. */
val db78Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.LANGUAGE,
        section = "Mongolian",
        title = "The Phags-pa script, a square alphabet made for the Mongol empire",
        originalTitle = "Phags-pa'i yig",
        author = "Drogon Chogyal Phagpa for the Yuan court",
        year = 1269,
        summary = "The Phags-pa script, an alphabet built on Tibetan letters, was made for the Mongol court and was ordered for official documents by an edict of 1269.",
        details = "Accounts give 1260 as the year the commission began and 1269 as the year the script was finished and the edict made it the script of official documents, alongside local scripts where suitable. The alphabet was meant to remain the state's script after Kublai took the Chinese throne in 1271, but its uptake was limited, and most official records stayed in Chinese characters or in the Uyghur script.",
        rare = true,
    ),

    entry(
        databaseId = 78,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Brunetto Latini's Book of the Treasure, an encyclopedia in French",
        originalTitle = "Li Livres dou tresor",
        author = "Brunetto Latini",
        year = 1265,
        summary = "Brunetto Latini wrote his Book of the Treasure in French during his exile, a summary of learning on history, nature, ethics and government.",
        details = "The exile followed the Guelph defeat at Montaperti in 1260, and most accounts date the writing to the years of that exile, roughly 1260 to 1266. He chose French, which was widely read at the royal courts of France, rather than Italian or Latin, and the book was later copied and translated into other languages.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Jacobus de Voragine's Golden Legend, a Latin book of saints' lives",
        originalTitle = "Legenda aurea",
        author = "Jacobus de Voragine",
        year = 1260,
        summary = "Jacobus de Voragine compiled the Legenda aurea, a Latin collection of saints' lives arranged by the church year, and it became one of the most widely copied books of the Middle Ages.",
        details = "Jacobus was a Dominican who later became archbishop of Genoa, and most accounts give about 1260 as the date of the first compilation. The author kept revising the collection for decades, so accounts differ on which version a given copy follows.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Thomas Aquinas's Summa against the Gentiles, a Latin defence of the faith",
        originalTitle = "Summa contra gentiles",
        author = "Thomas Aquinas",
        year = 1265,
        summary = "Thomas Aquinas wrote his Summa against the Gentiles in Italy in the early and middle 1260s, a Latin defence of the Christian faith that argues from reason as well as from revelation.",
        details = "Aquinas wrote the work while he was attached to the papal court and the Dominican houses of Italy, and the composition is usually dated to 1259 to 1265. The four books were meant as a manual for those who argued with unbelievers.",
    ),

)
