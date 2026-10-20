package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 300 to 310: Porphyry's Greek attack on the faith, a Greek defence of Origen written in prison, and a Latin panegyric from Trier. */
val db174Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 174,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Porphyry's Against the Christians, a fifteen-book Greek attack on the faith",
        originalTitle = "Kata Christianon",
        author = "Porphyry of Tyre",
        year = 300,
        summary = "Porphyry's Against the Christians, in fifteen Greek books, criticised the scriptures and the faith, and survives only in fragments quoted by its opponents.",
        details = "Its date is disputed, with proposals from about 270 to about 305, and 300 is used here. Eusebius of Caesarea and Apollinaris of Laodicea answered it, and in 448 an imperial decree ordered its books burned. What survives is the text that opponents quoted, so the fifteen books are known mainly from fragments.",
        rare = true,
    ),

    entry(
        databaseId = 174,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "The Apology for Origen, written in prison at Caesarea by Pamphilus and Eusebius",
        originalTitle = "Apologia pro Origene",
        author = "Pamphilus of Caesarea and Eusebius of Caesarea",
        year = 307,
        summary = "Pamphilus and Eusebius wrote an Apology for Origen while Pamphilus was held in prison at Caesarea, a defence of Origen's writings.",
        details = "Pamphilus was imprisoned in 307 and put to death in 309, so the work was written in those years, and 307 is the year used here. Rufinus made a Latin version of part of it around 400, and much of the work is known through that version.",
        rare = true,
    ),

    entry(
        databaseId = 174,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Latin panegyric of 310 in praise of Constantine, delivered at Trier",
        originalTitle = "Panegyricus Latinus",
        author = "An unnamed orator at Trier",
        year = 310,
        summary = "A Latin panegyric delivered at Trier in 310 praised Constantine and told of a vision in which the god Apollo appeared to him.",
        details = "The speech was given at Trier, Constantine's court city in Gaul, and it belongs to the collection of Latin panegyrics that orators gave to emperors. The orator's report of the vision is the most striking detail of the speech.",
        rare = true,
    ),

)
