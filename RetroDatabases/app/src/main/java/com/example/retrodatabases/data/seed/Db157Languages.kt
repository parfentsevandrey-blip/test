package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 470 to 480: a Latin treatise on the soul by Claudianus Mamertus and the Georgian Martyrdom of Shushanik. */
val db157Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Claudianus Mamertus's On the State of the Soul, a Latin treatise against a bodily soul",
        originalTitle = "De statu animae",
        author = "Claudianus Mamertus",
        year = 470,
        summary = "Claudianus Mamertus, a cleric of Vienne, wrote his three-book On the State of the Soul about 470 to argue that the soul has no body.",
        details = "The treatise answers an anonymous tract that treated the soul as bodily, which Claudianus took to be the work of Faustus of Riez. Sources date it about 470, and one gives 467 to 472; the author's brother Mamertus headed the see of Vienne.",
        rare = true,
    ),

    entry(
        databaseId = 157,
        kind = Kind.LANGUAGE,
        section = "Georgian",
        title = "The Martyrdom of Shushanik, an early Georgian prose account of a Christian woman's death",
        originalTitle = "Shushanikis tsameba",
        author = "Jacob of Tsurtavi",
        year = 476,
        summary = "The Martyrdom of Shushanik tells how a Christian woman of Iberia died under pressure to give up her faith, and it is among the oldest surviving works of Georgian literature.",
        details = "One account places the text between 476 and 482, and another extends the window to 483, while the death it describes is given as 475 or 476. Its author was her priest and witnessed her death, and the name Jacob of Tsurtavi is the one usually given for him.",
        rare = true,
    ),

)
