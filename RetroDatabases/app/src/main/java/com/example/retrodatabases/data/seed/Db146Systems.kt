package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 580 to 590: the Kaihuang code of the Sui, promulgated in 583, and the Third Council of Toledo of 589. */
val db146Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 146,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "The Kaihuang code of the Sui, a law code of twelve sections and 500 provisions, promulgated in 583 by Emperor Wen",
        originalTitle = "Kaihuang lu",
        author = "Emperor Wen of Sui and his compilers",
        year = 583,
        summary = "The Kaihuang code replaced the harsher statutes of the Northern Zhou with a milder code of twelve sections and 500 provisions, and later codes built on it.",
        details = "The reform began in 581, the first year of the Kaihuang era, and accounts differ between 582 and 583 for the code's promulgation, so 583 is the year used here. Later dynastic codes inherited much of its penalty scheme.",
        rare = true,
    ),

    entry(
        databaseId = 146,
        kind = Kind.SYSTEM,
        section = "Visigothic",
        title = "The Third Council of Toledo of 589, which confirmed the Visigothic conversion to the Catholic faith and added the filioque to the creed",
        originalTitle = "Concilium Toletanum III",
        author = "King Reccared and the bishops of his kingdom",
        year = 589,
        summary = "The council that met at Toledo in 589 gathered the bishops of the Visigothic kingdom after King Reccared's conversion, and its acts set out the faith and canons of the kingdom.",
        details = "Reccared had turned from the Arian faith to the Catholic faith in 587, and the council of 589 confirmed that change and added the filioque, the phrase on the procession of the Holy Spirit, to the creed. Its acts include canons for the church and a declaration by the king.",
        rare = true,
    ),

)
