package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 850 to 860: al-Mutawakkil's decree of 850 on the dress of non-Muslim subjects, and the Pseudo-Isidorian Decretals of about 850. */
val db119Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.SYSTEM,
        section = "Abbasid",
        title = "The decree of 850 that set distinctive dress for non-Muslim subjects under al-Mutawakkil",
        originalTitle = "Decree of al-Mutawakkil of 850",
        author = "Al-Mutawakkil",
        year = 850,
        summary = "In 850 the Abbasid caliph al-Mutawakkil ordered non-Muslim subjects to wear distinctive dress, in the years when he was dropping the inquisition begun by al-Ma'mun.",
        details = "The decree is recorded in al-Tabari's chronicle under 235 AH, which is 850, and it required dhimmis to wear distinctive belts and conical caps and to fit their saddles with wooden stirrups. Accounts differ on the colour of the headgear, which some translations give as honey-coloured and others as yellow.",
        rare = true,
    ),

    entry(
        databaseId = 119,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Pseudo-Isidorian Decretals, a forged collection of church law with papal letters",
        originalTitle = "Decretales Pseudo-Isidorianae",
        author = "An anonymous Frankish compiler, writing under the name of Isidore",
        year = 850,
        summary = "The Pseudo-Isidorian Decretals, a forged collection of church law with papal letters and council canons, was put together in the Frankish kingdom about 850.",
        details = "The compiler wrote under the name of Isidore of Seville, the Spanish bishop who died in the seventh century, and the papal letters in the collection are not genuine. Most accounts date the making of the collection to the years around 850, and it was later exposed as a forgery.",
    ),

)
