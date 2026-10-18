package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1260 to 1270: Hulagu's observatory at Maragha, Simon de Montfort's parliament of 1265, the assize of bread and ale of 1266 and the Statute of Marlborough of 1267. */
val db78Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.SYSTEM,
        section = "Astronomy",
        title = "Hulagu's observatory at Maragha, begun in 1259 under Nasir al-Din al-Tusi",
        originalTitle = "Rasadkhana of Maragha",
        author = "Nasir al-Din al-Tusi and Hulagu Khan",
        year = 1262,
        summary = "Hulagu's observatory at Maragha, directed by Nasir al-Din al-Tusi, was begun in 1259 and was in use by 1262.",
        details = "Hulagu backed the project, which is usually dated to 1259, and the observatory became a centre of astronomical observation and of the work that led to the Ilkhanic tables. Accounts differ on when the building was finished, and one account gives a building phase of twelve years from the start, so a completion date after 1262 is possible.",
        rare = true,
    ),

    entry(
        databaseId = 78,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "Simon de Montfort's parliament of 1265, held in the name of the king",
        originalTitle = "Parliamentum",
        author = "Simon de Montfort, in the name of Henry III",
        year = 1265,
        summary = "Simon de Montfort summoned a parliament in the name of Henry III for January 1265, and its writs called knights from the shires and burgesses from the towns to Westminster.",
        details = "Its membership included knights from the shires and burgesses from the towns, and Montfort's rule ended with his defeat and death at Evesham in August 1265. Accounts differ on how far the assembly was a precedent for later parliaments.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Statute of Marlborough, an English law on land, lordship and courts",
        originalTitle = "Statutum de Merleberge",
        author = "Henry III and his magnates",
        year = 1267,
        summary = "The Statute of Marlborough, issued in 1267, reformed English law on land, lordship and the courts.",
        details = "It was issued in the reign of Henry III after the civil war of 1264 to 1267, and it dealt with the rights of lords over their tenants and with the courts in which those rights were tried. Its clauses were cited in later English law, and 1267 is the date given in most accounts.",
    ),

    entry(
        databaseId = 78,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Assize of Bread and Ale, set by Henry III in 1266",
        originalTitle = "Assisa panis et cervisie",
        author = "Henry III of England",
        year = 1266,
        summary = "The Assize of Bread and Ale of 1266 tied the weight of bread and the price of ale to the price of grain, so that bakers and brewers followed one scale across England.",
        details = "The assize tied the weight of a penny loaf to the price of wheat and the price of ale to the price of grain, and local officers checked the weights and measures. Most accounts give 1266 as the year of the assize.",
    ),

)
