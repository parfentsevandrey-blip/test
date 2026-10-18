package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1010 to 1015: a Persian epic, a Latin chronicle, a Latin history of Normandy, an Old English sermon and a Vietnamese edict. */
val db103Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 103,
        kind = Kind.LANGUAGE,
        section = "Persian epic",
        title = "Ferdowsi's Shahnameh, the Persian Book of Kings, completed in 1010",
        originalTitle = "Shahnameh",
        author = "Ferdowsi of Tus",
        year = 1010,
        summary = "Ferdowsi's Shahnameh, a New Persian epic of about fifty thousand couplets, was completed in 1010 and presented to the Ghaznavid sultan Mahmud.",
        details = "The poem follows the kings of Iran from the mythical past to the Arab conquest of the Sasanian empire, in rhymed couplets of the epic metre. The date 1010 is the usual one, and the stories of how Mahmud rewarded the poet are told in several versions that disagree.",
    ),

    entry(
        databaseId = 103,
        kind = Kind.LANGUAGE,
        section = "Saxon history",
        title = "Thietmar of Merseburg's Chronicon, a Latin history of the Saxon and Ottonian kings",
        originalTitle = "Chronicon",
        author = "Thietmar of Merseburg",
        year = 1012,
        summary = "Thietmar of Merseburg wrote his Latin Chronicon in eight books on the Saxon and Ottonian kings between about 1012 and his death in 1018.",
        details = "Thietmar was bishop of Merseburg, and his chronicle gives close attention to Saxony and the German kingdom in the tenth and early eleventh centuries. The work is usually dated 1012 to 1018, and the record gives the year the writing began.",
        rare = true,
    ),

    entry(
        databaseId = 103,
        kind = Kind.LANGUAGE,
        section = "Norman history",
        title = "Dudo of Saint-Quentin's history of the first dukes of Normandy",
        originalTitle = "De moribus et actis primorum Normanniae ducum",
        author = "Dudo of Saint-Quentin",
        year = 1015,
        summary = "Dudo of Saint-Quentin's Latin history of the first dukes of Normandy runs from 852 to the death of Duke Richard I in 996 and was first circulated about 1015.",
        details = "Accounts give dates for the work from about 996 to about 1030, and 1015 is the year most often given for its first appearance. The work is linked to the dukes Richard I and Richard II, who are said to have asked for it, and it is known under several titles.",
        rare = true,
    ),

    entry(
        databaseId = 103,
        kind = Kind.LANGUAGE,
        section = "Old English",
        title = "Wulfstan's Sermo Lupi ad Anglos, a sermon to the English in the Danish wars",
        originalTitle = "Sermo Lupi ad Anglos",
        author = "Wulfstan, archbishop of York",
        year = 1014,
        summary = "Wulfstan's Old English sermon, the Sermo Lupi ad Anglos, rebukes the English for their sins while Danish armies overran the country and is usually dated to about 1014.",
        details = "The sermon belongs to the years of Sweyn Forkbeard's invasion in 1013 and 1014, and it is often read as a call to moral and public reform. The date of about 1014 is the usual one, but scholars debate the exact occasion of the preaching.",
        rare = true,
    ),

    entry(
        databaseId = 103,
        kind = Kind.LANGUAGE,
        section = "Vietnam",
        title = "Ly Thai To's edict moving the capital to Thang Long, 1010",
        originalTitle = "Chieu doi do",
        author = "Ly Cong Uan, Ly Thai To",
        year = 1010,
        summary = "The founder of the Ly dynasty, Ly Cong Uan, is credited with an edict of 1010 that moved the capital from Hoa Lu to Dai La, which was renamed Thang Long.",
        details = "The edict is written in classical Chinese and survives in later Vietnamese histories, written centuries after the event, so its wording reflects those compilers as well as the court. Thang Long is the old name of the city now called Hanoi.",
        rare = true,
    ),

)
