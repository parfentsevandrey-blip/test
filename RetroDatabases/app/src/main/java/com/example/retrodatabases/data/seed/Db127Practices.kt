package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 770 to 780: Offa of Mercia's gold coin of about 774, a Latin-lettered copy of an Abbasid dinar of AH 157. */
val db127Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 127,
        kind = Kind.PRACTICE,
        section = "Mercian",
        title = "Offa of Mercia's gold coin of about 774, a Latin-lettered copy of an Abbasid dinar",
        originalTitle = "mancus",
        author = "Offa of Mercia",
        year = 774,
        summary = "A gold coin struck under Offa of Mercia about 774 copies an Abbasid dinar of AH 157, with its Latin lettering set among Arabic glyphs.",
        details = "Offa's surviving gold coinage is three pieces, and this one follows the Abbasid model dated AH 157, which is 773 to 774 CE. Accounts differ on whether the copy was struck in 774 or somewhat later, and the word mancus later meant a gold piece, a gold weight matching the dinar, and a money of account worth thirty silver pennies.",
        rare = true,
    ),

)
