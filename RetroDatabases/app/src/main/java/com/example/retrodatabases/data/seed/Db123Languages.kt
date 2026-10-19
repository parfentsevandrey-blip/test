package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 810 to 820: the Yuanhe xingzuan of 812, Theophanes's Chronographia of 813, al-Shafi'i's Risala of 814 and the Shinsen Shojiroku of 815. */
val db123Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 123,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "Yuanhe xingzuan, a Tang book of Chinese surnames and their clans",
        originalTitle = "Yuanhe xingzuan",
        author = "Lin Bao",
        year = 812,
        summary = "The Yuanhe xingzuan, a Tang register of Chinese surnames and the clans that carried them, was completed in 812 by Lin Bao.",
        details = "The register was prepared at the command of Emperor Xianzong, and its material came largely from private family genealogies, so its historical claims call for caution. The year 812 is the seventh year of the Yuanhe era, which ran from 806 to 820, and a copy of the work survives in the Siku Quanshu, the eighteenth-century imperial library collection.",
        rare = true,
    ),

    entry(
        databaseId = 123,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Shinsen Shojiroku, the Japanese register of the families of the capital and their origins",
        originalTitle = "Shinsen Shojiroku",
        author = "Court compilers under Emperor Saga",
        year = 815,
        summary = "The Shinsen Shojiroku, a register of families living in and around the capital, was completed in 815 and sorts them by their claimed descent.",
        details = "The families are divided into three groups: those descended from the gods, those descended from the emperors, and those descended from foreign immigrants. The register dates from the Konin era, which ran from 810 to 824.",
        rare = true,
    ),

    entry(
        databaseId = 123,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Theophanes's Chronographia, a Greek chronicle of the years 284 to 813",
        originalTitle = "Chronographia",
        author = "Theophanes the Confessor",
        year = 813,
        summary = "Theophanes's Chronographia, a Greek chronicle from the accession of Diocletian in 284 to the fall of Michael I Rhangabe in 813, was written about 810 to 815.",
        details = "The chronicle takes up where the unfinished chronicle of George Syncellus stops, and it counts its years from the creation of the world, as Byzantine chronographers did. Accounts date the composition to about 810 to 815 and differ by about a year on the end of the work, so 813, the year its narrative ends, is the date used here; scholars also debate how much of the chronological tables was Theophanes's own work.",
    ),

    entry(
        databaseId = 123,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "Al-Shafi'i's Risala, a treatise on the sources and method of Islamic law",
        originalTitle = "Kitab al-Risala",
        author = "Muhammad ibn Idris al-Shafi'i",
        year = 814,
        summary = "Al-Shafi'i's Risala, a treatise on the sources of Islamic law and the method of reasoning from them, was first written in Baghdad about 814.",
        details = "The book was reworked later in Egypt, where al-Shafi'i died in 820, and the year 814 is approximate for the Baghdad version. Scholars do not agree on whether al-Shafi'i founded the field of legal theory, since rival accounts name Abu Hanifa, al-Shaybani or Abu Yusuf as the founder.",
    ),

)
