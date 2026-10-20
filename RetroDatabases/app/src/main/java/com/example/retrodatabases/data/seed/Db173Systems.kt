package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 310 to 320: Galerius's edict, the Milvian Bridge, the Edict of Milan, the Council of Arles and the Eastern Jin. */
val db173Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.SYSTEM,
        section = "Edicts",
        title = "Galerius's edict of toleration, issued at Serdica, which ended the persecution in his part of the empire",
        originalTitle = "Edict of Serdica",
        author = "Galerius",
        year = 311,
        summary = "Galerius's edict, issued at Serdica in April 311, ended the persecution of Christians in his part of the empire and asked them to pray for the emperor.",
        details = "Galerius died later in the same year, and Eusebius gives the text of the edict in Greek. Historians differ on whether the edict granted full freedom of worship or only ended the persecution.",
        rare = true,
    ),

    entry(
        databaseId = 173,
        kind = Kind.SYSTEM,
        section = "Battles",
        title = "The battle of the Milvian Bridge, where Constantine defeated Maxentius outside Rome",
        originalTitle = "Pons Milvius",
        author = "Constantine and Maxentius",
        year = 312,
        summary = "Constantine defeated Maxentius at the Milvian Bridge outside Rome on 28 October 312, and the victory gave him control of Rome and the west.",
        details = "Maxentius drowned in the Tiber during the rout. Lactantius and Eusebius tell different stories of the signs that Constantine saw before the battle.",
    ),

    entry(
        databaseId = 173,
        kind = Kind.SYSTEM,
        section = "Edicts",
        title = "The Edict of Milan, a letter of Licinius on freedom of worship posted at Nicomedia in 313",
        originalTitle = "Edict of Milan",
        author = "Licinius and Constantine",
        year = 313,
        summary = "The Edict of Milan, a letter of Licinius on freedom of worship, was posted at Nicomedia on 13 June 313, and its text survives in Lactantius and Eusebius.",
        details = "The name Edict of Milan is a modern label, and historians differ on whether Constantine and Licinius agreed a joint edict at Milan. Accounts also differ on the date of the meeting, which some place in February 313 and others months later.",
    ),

    entry(
        databaseId = 173,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Arles, a church council of 314 with three British bishops among its signatories",
        originalTitle = "Synod of Arles",
        author = "Constantine and the western bishops",
        year = 314,
        summary = "The Council of Arles met in 314 under Constantine's direction to settle the Donatist dispute, and three British bishops signed its acts.",
        details = "The British bishops named are Eborius of York and Restitutus of London, and a third, Adelfius, whose see is disputed, since Lincoln and Colchester have both been proposed for him.",
        rare = true,
    ),

    entry(
        databaseId = 173,
        kind = Kind.SYSTEM,
        section = "Dynasties",
        title = "The Eastern Jin dynasty, founded at Jiankang after the Western Jin capital fell at Chang'an",
        originalTitle = "Dong Jin",
        author = "Sima Rui",
        year = 317,
        summary = "Sima Rui, a prince of the Jin house, set up the Eastern Jin dynasty at Jiankang in 317, after the Western Jin capital at Chang'an fell in 316.",
        details = "Accounts differ on the start date: one has Sima Rui assume the imperial title in 317, while others give his accession as April 318.",
    ),

)
