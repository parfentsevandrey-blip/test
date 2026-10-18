package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1231 to 1236: Sicilian, Japanese, church and English law codes, and a Baghdad college with four schools of law. */
val db81Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Frederick II's Liber Augustalis, the law code of the kingdom of Sicily",
        originalTitle = "Liber Augustalis",
        author = "Frederick II",
        year = 1231,
        summary = "Frederick II issued the Liber Augustalis at Melfi in 1231, a Latin law code for his kingdom of Sicily that set out the courts, the officials and the royal justice of the realm.",
        details = "The code is also called the Constitutions of Melfi, and its name recalls the Augustus, the imperial title Frederick claimed. It was promulgated in 1231, and its laws were meant to bind every court and official in the kingdom.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The Goseibai Shikimoku, the warrior code of the Kamakura shogunate",
        originalTitle = "Goseibai Shikimoku",
        author = "Hojo Yasutoki",
        year = 1232,
        summary = "Hojo Yasutoki issued the Goseibai Shikimoku in 1232, a short code of laws for the warrior government at Kamakura that was used for decades to judge disputes among samurai.",
        details = "The code is usually described as having 51 articles, and it was drawn up by a council of officials rather than by the court at Kyoto. It is also called the Joei code after the era name of 1232, and it set out rules on land holding and on how disputes were heard.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.SYSTEM,
        section = "Church law",
        title = "Gregory IX's Liber Extra, the papal decretal collection of 1234",
        originalTitle = "Liber Extra",
        author = "Raymond of Penyafort, for Pope Gregory IX",
        year = 1234,
        summary = "Pope Gregory IX issued the Liber Extra in 1234, a collection of papal decretals in five books that gave canon law one official text for the schools and courts.",
        details = "Raymond of Penyafort compiled the collection, which replaced the older decretal collections in the schools. It was sent to the universities of Bologna and Paris as the authorised text, and later papal law was gathered in further collections, the Clementines of 1317 among them.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Statute of Merton, a law on lords and common land in England",
        originalTitle = "Statutum de Merton",
        author = "Henry III and his parliament",
        year = 1236,
        summary = "The Statute of Merton, passed by Henry III's parliament at Merton Priory in Surrey, let lords enclose waste that tenants used in common, if enough pasture was left for those tenants.",
        details = "The statute is dated 1235 or 1236 depending on the reckoning, because it belongs to the twentieth regnal year of Henry III, which runs from late October 1235 to late October 1236. It also has clauses on widows' dower, wardship and debts.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.SYSTEM,
        section = "Iraq",
        title = "The Mustansiriya school in Baghdad, a college with four law schools",
        originalTitle = "Al-Mustansiriya",
        author = "Caliph al-Mustansir",
        year = 1233,
        summary = "The caliph al-Mustansir opened the Mustansiriya school in Baghdad in 1233, a college that taught the four Sunni schools of law in separate halls under one roof.",
        details = "Building began in 1227, and the school is usually dated to its opening in 1233. The four schools were Hanafi, Maliki, Shafii and Hanbali, and each had its own hall for teaching.",
        rare = true,
    ),

)
