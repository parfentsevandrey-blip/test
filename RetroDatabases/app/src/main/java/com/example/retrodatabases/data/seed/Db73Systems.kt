package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1310 to 1317: Venice's Council of Ten, a papal bull against the Templars, the Ordinances of 1311, the Yuan examinations and the Clementine decretals. */
val db73Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 73,
        kind = Kind.SYSTEM,
        section = "Venice",
        title = "The Council of Ten, set up in Venice after a plot of 1310",
        originalTitle = "Consiglio dei Dieci",
        author = "The Venetian Great Council",
        year = 1310,
        summary = "The Venetian Great Council set up a Council of Ten in 1310 to deal with a conspiracy against the state, and the body was renewed until it became a standing part of Venetian government.",
        details = "The council was created after the plot led by Bajamonte Tiepolo was put down, and its first members were chosen for a short term. Accounts differ on the date at which it became permanent, so no later date is given here.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.SYSTEM,
        section = "Military orders",
        title = "The papal bull that dissolved the Knights Templar in 1312",
        originalTitle = "Vox in excelso",
        author = "Pope Clement V",
        year = 1312,
        summary = "Pope Clement V dissolved the Order of the Knights Templar in 1312 by the bull Vox in excelso, a papal decree issued after years of pressure from the French crown.",
        details = "The order was ended by papal decree and not by a court verdict, and much of its property was handed to the Knights Hospitaller. The French crown kept its own share of the wealth that it had pressed to seize.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Ordinances of 1311, limits on the English king's government",
        originalTitle = "Ordinances of 1311",
        author = "The Lords Ordainers",
        year = 1311,
        summary = "The Lords Ordainers drew up the Ordinances of 1311, a set of rules that limited Edward II's household and government and called for regular meetings of the great lords.",
        details = "The Ordinances were a baronial answer to the king's favourites, and they were agreed at a parliament in 1311. Accounts differ on how far Edward II kept to them in the years that followed.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Yuan restoration of the civil examinations in 1313",
        originalTitle = "Yuan keju",
        author = "The Yuan court under Renzong",
        year = 1313,
        summary = "The Yuan court restored the civil examinations in 1313, so that candidates for office again answered on the Confucian classics, with the commentaries of Zhu Xi as the standard text.",
        details = "The examinations had been suspended after the Mongol conquest of the south, and the first candidates sat the restored examinations in 1315. The decree is usually dated 1313.",
    ),

    entry(
        databaseId = 73,
        kind = Kind.SYSTEM,
        section = "Canon law",
        title = "The Clementine decretals, the law book of 1317",
        originalTitle = "Constitutiones Clementinae",
        author = "Pope John XXII, publishing the decrees of Clement V",
        year = 1317,
        summary = "Pope John XXII published the Clementine decretals in 1317, a collection of the decrees of Clement V that was added to the older books of church law.",
        details = "The collection includes decrees from the Council of Vienne, which met in 1311 and 1312, and it takes its name from Clement V. Its place among the books of canon law made it a standing part of the law of the church.",
    ),

)
