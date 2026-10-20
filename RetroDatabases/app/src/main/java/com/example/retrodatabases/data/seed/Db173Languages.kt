package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 310 to 320: Lactantius's book on the persecutors, a Sogdian merchant's letter and Ge Hong's Baopuzi. */
val db173Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 173,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Lactantius's On the Deaths of the Persecutors, a Latin account of the persecuting emperors",
        originalTitle = "De mortibus persecutorum",
        author = "Lactantius",
        year = 315,
        summary = "Lactantius's On the Deaths of the Persecutors is a Latin book, written about 315, that tells how the emperors who persecuted the church came to a bad end.",
        details = "The book was written after the defeat and death of Maximinus Daia in 313, and it reads the fall of each persecutor as divine punishment. It also preserves the letter of Licinius that is usually called the Edict of Milan.",
        rare = true,
    ),

    entry(
        databaseId = 173,
        kind = Kind.LANGUAGE,
        section = "Sogdian",
        title = "Letter II of the Sogdian Ancient Letters, a merchant's letter that reports the sack of Luoyang",
        originalTitle = "Ancient Letter II",
        author = "Sogdian merchants",
        year = 313,
        summary = "Letter II of the Sogdian Ancient Letters is a merchant's letter in the Sogdian language, written about 313, that reports the sack of Luoyang as recent news.",
        details = "Aurel Stein found the letters in 1907 in a watchtower west of Dunhuang, and the find-spot does not show where Letter II was written. Accounts differ on its date, which runs from 312 to 314.",
        rare = true,
    ),

    entry(
        databaseId = 173,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Ge Hong's Baopuzi, a Chinese book on alchemy and long life, completed in 317",
        originalTitle = "Baopuzi",
        author = "Ge Hong",
        year = 317,
        summary = "Ge Hong completed the Baopuzi in 317, a Chinese book whose inner chapters set out alchemy and the search for long life.",
        details = "The title means the Master Who Embraces Simplicity, and the outer chapters turn from alchemy to government and conduct. The book is a main source for the alchemy of the Jin period.",
        rare = true,
    ),

)
