package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 690 to 700: the Quinisext Council of 692, the Japanese capital of Fujiwara-kyo from 694, Wihtred's laws of Kent in 695 and the Umayyad gold dinar of 696. */
val db135Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Quinisext Council of 692, which issued canons for the Byzantine church and is also called the Council in Trullo",
        originalTitle = "Concilium Quinisextum",
        author = "Justinian II and the bishops of Constantinople",
        year = 692,
        summary = "The Quinisext Council, which met at Constantinople in 691 and 692, issued 102 canons on church discipline and accepted the 85 Apostolic canons.",
        details = "It was meant to complete the work of the fifth and sixth ecumenical councils, which is why it is called Quinisext, and Rome did not accept all of its canons. Accounts date the council to 691 or 692, and this record uses 692 as the year of its canons.",
    ),

    entry(
        databaseId = 135,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "Fujiwara-kyo, the Japanese capital from 694 to 710, laid out on a Chinese-style plan",
        originalTitle = "Fujiwara-kyo",
        author = "Empress Jito and her court",
        year = 694,
        summary = "Fujiwara-kyo was the Japanese capital from 694 to 710, when the court under Empress Jito moved there from Asuka.",
        details = "The city was laid out on a grid modelled on Chinese capitals, and the court left it in 710 for Nara. Accounts give 694 as the year of the move, and this record uses that year for the start of the capital.",
        rare = true,
    ),

    entry(
        databaseId = 135,
        kind = Kind.SYSTEM,
        section = "English",
        title = "The laws of Wihtred of Kent, a code issued in 695 at a council held at Baccanceld",
        originalTitle = "Wihtred's laws",
        author = "King Wihtred of Kent",
        year = 695,
        summary = "The laws of Wihtred of Kent were issued in 695 at a council, and they survive in Old English as one of the early English law codes.",
        details = "The council met at Baccanceld in Kent, and its clauses deal with church matters and with fines for offences. Accounts give 695 as the year of issue, and this record uses that year for the code.",
        rare = true,
    ),

    entry(
        databaseId = 135,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "The Umayyad gold dinar with Arabic inscriptions, begun under Abd al-Malik in 696 or 697 and free of figures",
        originalTitle = "dinar",
        author = "Abd al-Malik ibn Marwan",
        year = 696,
        summary = "Abd al-Malik replaced the Byzantine-style gold coinage of his realm with dinars that bear only Arabic inscriptions, begun in 696 or 697.",
        details = "The early Arabic dinars carry Arabic religious texts in place of the old figures and Greek legends, and accounts date the first of them to 696 or 697. The dinar with its Arabic text later became the standard gold coin of the Islamic lands.",
    ),

)
