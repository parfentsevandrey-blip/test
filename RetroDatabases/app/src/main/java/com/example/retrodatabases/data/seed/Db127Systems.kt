package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 770 to 780: the end of the Lombard kingdom in 774 and the Herstal capitulary of 779. */
val db127Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Charlemagne takes Pavia and ends the Lombard kingdom of Italy in 774",
        originalTitle = "Regnum Langobardorum",
        author = "Charlemagne and Desiderius",
        year = 774,
        summary = "Charlemagne took Pavia in 774 after a long siege, ended the Lombard kingdom of Italy, and took the title king of the Lombards.",
        details = "The Lombards had entered Italy in 568, and the duchies of Spoleto and Benevento outlived the kingdom. Accounts date the surrender of King Desiderius to June 774.",
    ),

    entry(
        databaseId = 127,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Herstal capitulary of March 779, Charlemagne's measures on church and state from a meeting at Herstal",
        originalTitle = "Capitulare Haristallense",
        author = "Charlemagne",
        year = 779,
        summary = "The Herstal capitulary, a set of measures on church and state agreed at a meeting of magnates and clergy at Herstal in March 779, is one of Charlemagne's early written laws.",
        details = "Herstal was a royal residence, and the meeting followed setbacks that included a failed campaign in Spain and a Saxon rising that threatened Cologne. The text survives in a manuscript at St Gallen, and a second capitulary from the same assembly is dated by some scholars to 779 and by others to Regensburg in 792 or 793.",
        rare = true,
    ),

)
