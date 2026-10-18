package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 992 to 1000: a Byzantine trade bull for Venice, Olaf Tryggvason's rule in Norway, Otto III's coronation and the Althing's Christian ruling. */
val db105Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "A Byzantine golden bull of 992 lowers the customs duties on Venetian traders",
        originalTitle = "Chrysobull of 992",
        author = "Basil II of Byzantium",
        year = 992,
        summary = "In 992 a Byzantine golden bull granted Venetian traders lower customs duties in the empire.",
        details = "The bull is usually dated to 992 and issued in the name of Basil II, and it is an early document in the series of Venetian trade privileges in the empire. Histories of Venice describe its terms in different ways, so the record gives only the general sense.",
        rare = true,
    ),

    entry(
        databaseId = 105,
        kind = Kind.SYSTEM,
        section = "Norway",
        title = "Olaf Tryggvason becomes king of Norway in 995 and presses Christian baptism on his people",
        originalTitle = "Olaf Tryggvason becomes king of Norway in 995 and presses Christian baptism on his people",
        author = "Olaf Tryggvason",
        year = 995,
        summary = "Olaf Tryggvason became king of Norway in 995, and the Norse sagas describe him forcing Christian baptism on the Norwegians.",
        details = "He came to power after the death of Earl Haakon in 995, and his campaign to convert Norway is known mainly from sagas written long after his death at the sea battle of Svolder in about 1000. Accounts differ on how far baptism was forced and how far it was won by persuasion.",
    ),

    entry(
        databaseId = 105,
        kind = Kind.SYSTEM,
        section = "Germany and Rome",
        title = "Otto III is crowned emperor at Rome in 996 by his cousin Pope Gregory V",
        originalTitle = "Otto III is crowned emperor at Rome in 996 by his cousin Pope Gregory V",
        author = "Otto III and Pope Gregory V",
        year = 996,
        summary = "Otto III, the German king, is crowned emperor at Rome in 996 by his cousin Pope Gregory V, the first German pope.",
        details = "Gregory V was elected pope in 996, and he was the first pope from Germany. The record gives 996 for the coronation, the year usually given in histories of the Ottonian empire.",
    ),

    entry(
        databaseId = 105,
        kind = Kind.SYSTEM,
        section = "Iceland",
        title = "Iceland's Althing adopts Christianity on the ruling of the lawspeaker Thorgeir",
        originalTitle = "Iceland's Althing adopts Christianity on the ruling of the lawspeaker Thorgeir",
        author = "Thorgeir Ljosvetningagodi, lawspeaker of Iceland",
        year = 1000,
        summary = "The Althing, Iceland's assembly, adopts Christianity as the law of the land on the ruling of the lawspeaker Thorgeir, in 999 or 1000.",
        details = "Accounts differ between 999 and 1000 for the decision, and the sources give different versions of the terms the ruling set for the older religion. The record uses 1000, the later of the two dates.",
    ),

)
