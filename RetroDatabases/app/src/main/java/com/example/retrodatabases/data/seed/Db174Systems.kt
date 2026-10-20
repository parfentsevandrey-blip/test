package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 300 to 310: the Edict on Maximum Prices, the first edict of the Great Persecution, the abdication of Diocletian, the acclamation at York and the Carnuntum conference. */
val db174Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.SYSTEM,
        section = "Economy",
        title = "Diocletian's Edict on Maximum Prices, a ceiling on the prices and wages of the empire",
        originalTitle = "Edictum de pretiis rerum venalium",
        author = "Diocletian and his colleagues",
        year = 301,
        summary = "The Edict on Maximum Prices, issued in 301 by the four rulers of the Tetrarchy, set ceilings on the prices of goods and the pay of workers across the empire.",
        details = "Copies survive as inscriptions in several cities of the eastern provinces, and the best-known copy was found at Aphrodisias in Caria. The edict names Diocletian, Maximian, Constantius and Galerius as its issuers, and it threatened punishment for sellers who broke the ceilings.",
    ),

    entry(
        databaseId = 174,
        kind = Kind.SYSTEM,
        section = "Persecution",
        title = "The first edict of the Great Persecution, ordering churches destroyed and sacred books burned",
        originalTitle = "Edictum",
        author = "Diocletian and Galerius",
        year = 303,
        summary = "The first edict of the Great Persecution, issued at Nicomedia on 23 February 303, ordered the destruction of churches and the burning of sacred scriptures.",
        details = "It was the first of a series of edicts against the Christians that followed in 303 and 304. Eusebius and Lactantius both describe it, and the demand that books be given up led to the practice called traditio.",
    ),

    entry(
        databaseId = 174,
        kind = Kind.SYSTEM,
        section = "Succession",
        title = "The abdication of Diocletian and Maximian, which made Galerius and Constantius Augusti",
        originalTitle = "Abdication of Diocletian and Maximian",
        author = "Diocletian and Maximian",
        year = 305,
        summary = "On 1 May 305 Diocletian and Maximian gave up the throne, and Galerius and Constantius took the title of Augustus in their place.",
        details = "Diocletian resigned at Nicomedia and Maximian at Milan, and Severus and Maximinus Daia were made Caesars under the new Augusti. The date 1 May 305 is the one given for the end of Diocletian's reign.",
    ),

    entry(
        databaseId = 174,
        kind = Kind.SYSTEM,
        section = "Succession",
        title = "Constantine's acclamation as Augustus at York after his father's death",
        originalTitle = "Acclamation of Constantine at York",
        author = "The army at York",
        year = 306,
        summary = "On 25 July 306, after his father Constantius died at York, the army there acclaimed Constantine as Augustus.",
        details = "Galerius recognised him only as Caesar, and the wider dispute over the title lasted for years. The date 25 July 306 is the one given for both the death of Constantius and the acclamation.",
    ),

    entry(
        databaseId = 174,
        kind = Kind.SYSTEM,
        section = "Succession",
        title = "The conference at Carnuntum, which named Licinius Augustus for the west",
        originalTitle = "Carnuntum conference",
        author = "Galerius, with Diocletian and Maximian",
        year = 308,
        summary = "At a conference at Carnuntum in November 308, Galerius and his colleagues named Licinius Augustus to hold the western title.",
        details = "The meeting is dated 11 November 308. Galerius called it to settle the succession after the death of Severus and the revolt of Maxentius at Rome, and the choice of Licinius did not end the rivalry in the west.",
        rare = true,
    ),

)
