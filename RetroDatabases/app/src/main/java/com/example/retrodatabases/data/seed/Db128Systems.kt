package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 760 to 770: the Round City of Baghdad, begun in 762, and the Frankish synod of Gentilly of 767. */
val db128Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 128,
        kind = Kind.SYSTEM,
        section = "Abbasid",
        title = "The Round City of Baghdad, the circular capital that al-Mansur began building in 762",
        originalTitle = "Madinat al-Salam",
        author = "al-Mansur",
        year = 762,
        summary = "al-Mansur began Baghdad in 762 as a round walled capital with four gates named for the cities and provinces they faced.",
        details = "Building began in 762, and the first brick is dated to 30 July of that year. Accounts differ on when the work ended, giving 766, 767 or 768. The gates faced Kufa to the southwest, Basra to the southeast, Sham to the northwest and Khorasan to the northeast.",
    ),

    entry(
        databaseId = 128,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Synod of Gentilly, a Frankish council of Christmas Day 767 that debated the filioque with Byzantine envoys",
        originalTitle = "Synod of Gentilly",
        author = "Pippin the Short and the Frankish bishops",
        year = 767,
        summary = "The Frankish synod at Gentilly, held on Christmas Day 767 with Pippin present, took up the filioque clause with envoys of the Byzantine emperor Constantine V.",
        details = "Papal legates, envoys of Constantine V and Greek bishops joined the Frankish bishops and Pippin at Gentilly, near Paris. The discussion turned to the filioque, the clause of the Nicene Creed that the Byzantine envoys found contentious. The official acts have not survived, and it is unclear who first raised the question.",
        rare = true,
    ),

)
