package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 343 and 346: the Council of Serdica, which split between Western and Eastern bishops, and the second Persian attempt on Nisibis. */
val db170Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 170,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Serdica, where Western and Eastern bishops split over Athanasius",
        originalTitle = "Concilium Sardicense",
        author = "Ossius of Cordoba and the bishops who met at Serdica",
        year = 343,
        summary = "The council that met at Serdica, modern Sofia, split into a Western gathering that backed Athanasius and an Eastern one that withdrew to Philippopolis.",
        details = "Accounts give the council's date as 342 or 343, and some argue for 344, so 343 is used here. The emperors Constans and Constantius II are often named as convenors, though some accounts name Constans alone, and Ossius of Cordoba chaired the Western side, which recognized Athanasius and Marcellus as orthodox and upheld the Roman rulings of 341.",
        rare = true,
    ),

    entry(
        databaseId = 170,
        kind = Kind.SYSTEM,
        section = "Sieges",
        title = "The second Persian attempt on Nisibis in 346, which the city's walls turned back",
        originalTitle = "Nisibis",
        author = "The Sasanian army and the defenders of Nisibis",
        year = 346,
        summary = "In 346 a Sasanian army under the king Shapur II made a second attempt on the Roman frontier city of Nisibis, and the defenders held it, as they had done before.",
        details = "Few details of the 346 attempt are on record, and the usual accounts give neither its commanders nor its length. Accounts give the first attempt as 337 or 338, and the third siege, in 350, is a separate event, so the year 346 rests on the usual list of attempts.",
        rare = true,
    ),

)
