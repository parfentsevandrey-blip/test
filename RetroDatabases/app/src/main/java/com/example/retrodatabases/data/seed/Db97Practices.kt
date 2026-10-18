package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1074 and 1077: a papal synod's bans on simony and married priests, and a king's penance at Canossa. */
val db97Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 97,
        kind = Kind.PRACTICE,
        section = "Church reform",
        title = "Gregory VII's Lenten synod of 1074, which barred simony and married priests",
        originalTitle = "Gregory VII's Lenten synod of 1074, which barred simony and married priests",
        author = "Gregory VII",
        year = 1074,
        summary = "A Roman synod held in Lent 1074 under Gregory VII renewed the bans on simony and on married clergy, and it told the laity not to take services from such priests.",
        details = "The decrees named simony and clerical marriage together as faults of the clergy, and the same measures were repeated at later synods. They fed the wider dispute over who might appoint bishops and priests, which later became the investiture conflict.",
    ),

    entry(
        databaseId = 97,
        kind = Kind.PRACTICE,
        section = "Empire and papacy",
        title = "Henry IV's penance at Canossa in January 1077, a public submission to the pope",
        originalTitle = "Henry IV's penance at Canossa in January 1077, a public submission to the pope",
        author = "Henry IV and Gregory VII",
        year = 1077,
        summary = "In January 1077 the German king Henry IV stood as a penitent at Canossa until Pope Gregory VII lifted the excommunication laid on him.",
        details = "Henry waited in the snow outside the castle in penitential dress from about 25 January, and accounts differ on whether he stood there three days or four before the absolution on 28 January. The act was public and ritual, but it did not settle the wider dispute between king and papacy over church appointments.",
    ),

)
