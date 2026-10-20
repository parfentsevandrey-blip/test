package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 690 to 700: the Zetian characters of Empress Wu, the Irish Law of the Innocents of 697 and the Quranic inscriptions of the Dome of the Rock. */
val db135Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 135,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Zetian characters, the new written forms that Empress Wu ordered for her Zhou dynasty, introduced about 690",
        originalTitle = "Wu Zhou xinzi",
        author = "Empress Wu Zetian",
        year = 690,
        summary = "Empress Wu's Zhou dynasty issued a set of new written characters around 690, and the best known of them is zhao, the name character made from the sun and moon in the sky.",
        details = "The new forms are usually given as twelve, though some accounts count more, and the official Zong Qinke is credited in one account with proposing them in 689. Their formal rollout came around the turn of 690 and continued until at least 705, and most of the forms lapsed after Empress Wu's reign while the name character survived.",
        rare = true,
    ),

    entry(
        databaseId = 135,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "Cain Adomnain, the Irish Law of the Innocents, issued at the Synod of Birr in 697 to spare women, clerics and children from war",
        originalTitle = "Cain Adomnain",
        author = "Adomnan of Iona",
        year = 697,
        summary = "Cain Adomnain, the Irish Law of the Innocents, was issued at a synod at Birr in 697 to spare women, clerics, children and the unarmed from the fighting.",
        details = "Adomnan, abbot of Iona, and his relative, the high king Loingsech mac Oengusso, are believed to have organised the Synod of Birr, and a surviving roster names 91 guarantors, among them kings and churchmen. Irish annals label the law Lex Innocentium, and its text survives in Old Irish.",
    ),

    entry(
        databaseId = 135,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The Quranic inscriptions of the Dome of the Rock at Jerusalem, put up under Abd al-Malik in 691 or 692",
        originalTitle = "Qubbat al-Sakhra",
        author = "Abd al-Malik ibn Marwan",
        year = 691,
        summary = "The Dome of the Rock in Jerusalem carries bands of Quranic verses in Arabic, put up under Abd al-Malik in 691 or 692.",
        details = "Accounts give 691 or 692 as the years of building, and the inscriptions around the dome are among the earliest dated Quranic inscriptions known. The text is carried by the building itself rather than copied in a manuscript.",
    ),

)
