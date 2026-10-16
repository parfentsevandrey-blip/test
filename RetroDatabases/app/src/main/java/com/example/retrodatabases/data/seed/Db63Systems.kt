package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1410 to 1420: the Council of Constance, which ended the Western Schism and condemned Jan Hus. */
val db63Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Constance, 1414 to 1418",
        originalTitle = "Concilium Constantiense",
        author = "Pope John XXIII, at the urging of King Sigismund",
        year = 1414,
        summary = "The Council of Constance met from 1414 to 1418 to end the Western Schism of rival popes and to judge the teaching of Jan Hus.",
        details = "Hus was burned at the stake on 6 July 1415, and the council elected Martin V on 11 November 1417, which effectively closed the Western Schism. The council's span is given as 1414 to 1418, and this record uses 1414 for its opening year.",
    ),

)
