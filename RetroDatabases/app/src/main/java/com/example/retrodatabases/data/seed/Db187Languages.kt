package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 170 to 177: Tatian's Diatessaron of about 170 and a Greek letter from the churches of Vienne and Lyon on the persecution dated to 177. */
val db187Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.LANGUAGE,
        section = "Gospels",
        title = "Tatian's Diatessaron, one harmonised text of the four gospels",
        originalTitle = "Diatessaron",
        author = "Tatian",
        year = 170,
        summary = "Tatian wove the four gospels into one continuous text, the Diatessaron, and the work was read in Syriac and other languages for centuries.",
        details = "Modern accounts date the composition from about 166 to about 180, so 170 is a round date within that range. Scholars still disagree on whether Tatian first wrote the harmony in Syriac or in Greek, and the Greek evidence is scant.",
        rare = true,
    ),

    entry(
        databaseId = 187,
        kind = Kind.LANGUAGE,
        section = "Letters",
        title = "The letter of the churches of Vienne and Lyon on the persecution under Marcus Aurelius",
        originalTitle = "Letter of the churches of Vienne and Lyon to the churches of Asia and Phrygia",
        author = "The Christian churches of Vienne and Lyon",
        year = 177,
        summary = "A Greek letter from the churches of Vienne and Lyon describes the deaths of Christians in the persecution dated to 177, and Eusebius preserved long extracts of it in his Church History.",
        details = "The date 177 is the conventional one, taken from Eusebius's framing, and his two chronologies disagree, since his Chronicon places the events in 166 and 167 while other references give 178. The letter reaches us only through the extracts that Eusebius kept.",
        rare = true,
    ),

)
