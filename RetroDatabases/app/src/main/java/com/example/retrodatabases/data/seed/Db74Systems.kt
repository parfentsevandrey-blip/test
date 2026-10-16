package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1300 to 1310: an assembly of the three estates in France, a studium at Rome, a studium at Perugia, the Teutonic Order's seat in Prussia and the papal court at Avignon. */
val db74Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.SYSTEM,
        section = "France",
        title = "The assembly of the three estates that Philip IV called in 1302",
        originalTitle = "Etats generaux",
        author = "Philip IV of France",
        year = 1302,
        summary = "Philip IV called representatives of the clergy, the nobles and the towns to Paris in 1302, an assembly that later writers name the first Estates-General.",
        details = "The assembly met at Notre-Dame in April 1302 and backed the king against Pope Boniface VIII. The name Estates-General came later, and historians differ on how far the 1302 meeting set a pattern for later assemblies.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.SYSTEM,
        section = "Rome",
        title = "The Studium Urbis at Rome, set up by Boniface VIII in 1303",
        originalTitle = "Studium Urbis",
        author = "Pope Boniface VIII",
        year = 1303,
        summary = "Pope Boniface VIII set up a studium at Rome in 1303, the school that later became the University of Rome, known as La Sapienza.",
        details = "The date 1303 marks the papal foundation, and some accounts also mention teaching in Rome before it. The later history of the studium led to the university that still bears the name La Sapienza.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.SYSTEM,
        section = "Umbria",
        title = "The studium of Perugia, made a studium generale in 1308 by Clement V",
        originalTitle = "Studium generale di Perugia",
        author = "Pope Clement V",
        year = 1308,
        summary = "A papal bull of Clement V gave the studium of Perugia the status of a studium generale in 1308, so that its degrees were recognised beyond the city.",
        details = "The date usually given is 8 September 1308, and the groundwork was laid in 1306 with a statute for the school. Later privileges followed in 1318 from John XXII, in 1321 and in 1355 from an imperial document.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.SYSTEM,
        section = "Prussia",
        title = "The Teutonic Order moves its seat to Marienburg in 1309",
        originalTitle = "Marienburg",
        author = "Siegfried von Feuchtwangen, Grand Master",
        year = 1309,
        summary = "In 1309 the Teutonic Order moved its seat to Marienburg, the castle in Prussia that became the centre of its rule there.",
        details = "The move is dated to 1309 under Grand Master Siegfried von Feuchtwangen, and accounts differ on the earlier seat: some name Acre, others a Venetian stage under an earlier Grand Master. Marienburg stayed the chief seat of the Order in Prussia for much of the next century and a half.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.SYSTEM,
        section = "Provence",
        title = "The papal court settles at Avignon in 1309",
        originalTitle = "Curia Avenionensis",
        author = "Pope Clement V",
        year = 1309,
        summary = "Clement V settled the papal court at Avignon in 1309, the start of a stay of the popes there that lasted until 1377.",
        details = "Historians call the years of the Avignon popes the Avignon papacy, and the court stayed there while its popes were French. The return to Rome came under Gregory XI in 1377.",
    ),

)
