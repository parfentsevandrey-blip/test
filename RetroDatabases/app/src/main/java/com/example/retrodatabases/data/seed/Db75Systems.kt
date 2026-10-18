package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1291 to 1298: the Ordinances of Justice of Florence, the Swiss league's charter, the Model Parliament of England, the Venetian closing of the Great Council and Boniface VIII's Liber Sextus. */
val db75Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.SYSTEM,
        section = "Florence",
        title = "The Ordinances of Justice of Florence, against the magnates",
        originalTitle = "Ordinamenti di giustizia",
        author = "Giano della Bella and the Florentine commune",
        year = 1293,
        summary = "The Ordinances of Justice, passed at Florence in January 1293, shut the noble magnate families out of the highest offices unless they joined a guild.",
        details = "The ordinances were enacted on 18 January 1293 in the modern reckoning, and they made a magnate family answerable for the crimes of its members. Historians differ on how far the rules were enforced in the years that followed.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.SYSTEM,
        section = "Alps",
        title = "The Swiss federal charter of 1291, an alliance of three Alpine valleys",
        originalTitle = "Bundesbrief",
        author = "The men of Uri, Schwyz and Unterwalden",
        year = 1291,
        summary = "The Swiss federal charter, dated early August 1291, is an alliance of the three forest communities of Uri, Schwyz and Unterwalden, and it became the founding text of the later Swiss league.",
        details = "The charter is dated to early August 1291 by tradition. Historians dispute how far it founded a new league and how far it confirmed an older agreement among the valleys.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Model Parliament of 1295, with knights and burgesses summoned",
        originalTitle = "Parliament of 1295",
        author = "Edward I of England",
        year = 1295,
        summary = "Edward I called a parliament in 1295 that summoned knights from the shires and burgesses from the towns, and it set a pattern for later parliaments.",
        details = "Historians call it the Model Parliament because it became a model for later assemblies, not because it was the first to include town members. Accounts differ on how representative it was, and its writs were issued in the autumn of 1295.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.SYSTEM,
        section = "Venice",
        title = "The closing of Venice's Great Council to new families, the serrata of 1297",
        originalTitle = "Serrata del Maggior Consiglio",
        author = "The Venetian Great Council",
        year = 1297,
        summary = "In 1297 Venice began to close its Great Council to new families, so that membership passed by descent, a change the city called the serrata.",
        details = "The closing was carried out over several decades, and accounts differ on how many families were admitted in the years after 1297. Historians link it to the rise of a closed patriciate that governed the city until 1797.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.SYSTEM,
        section = "Church law",
        title = "Boniface VIII's Liber Sextus, a sixth book of papal decretals",
        originalTitle = "Liber Sextus Decretalium",
        author = "Pope Boniface VIII",
        year = 1298,
        summary = "Pope Boniface VIII issued the Liber Sextus in 1298 as a sixth book added to the decretals of Gregory IX, so that papal law could be taught and cited in one collection.",
        details = "It was promulgated on 3 March 1298, and the book was itself followed by the Clementine decretals of 1317. Universities that taught canon law came to cite the sixth book alongside the earlier collections.",
    ),

)
