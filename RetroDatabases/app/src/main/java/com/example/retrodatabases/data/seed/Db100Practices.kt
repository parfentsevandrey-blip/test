package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1045: the sale of the papacy to Gregory VI, a case of simony that the reform councils later condemned. */
val db100Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.PRACTICE,
        section = "Church",
        title = "Simony, the sale of the papacy by Benedict IX to Gregory VI in 1045",
        originalTitle = "Simony, the sale of the papacy by Benedict IX to Gregory VI in 1045",
        author = "Benedict IX and John Gratian",
        year = 1045,
        summary = "Simony is the buying and selling of church office, and in 1045 Benedict IX sold the papacy to his godfather John Gratian, who took the name Gregory VI.",
        details = "The word comes from Simon Magus, who offered money for spiritual power in the Acts of the Apostles. The sale became one of the scandals behind the reform councils of the following decades, and the rival claimants it produced were dealt with at Sutri in 1046.",
    ),

)
