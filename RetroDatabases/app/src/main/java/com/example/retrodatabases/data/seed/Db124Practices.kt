package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 800 to 810: the Capitulare de villis, the oath of 802, the Carolingian minuscule and the Filioque sung at Aachen in 809. */
val db124Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.PRACTICE,
        section = "Frankish",
        title = "The Capitulare de villis, Charlemagne's rules for the stewards of royal estates, about 800",
        originalTitle = "Capitulare de villis",
        author = "Charlemagne's court",
        year = 800,
        summary = "The Capitulare de villis is a set of royal rules for the stewards of the crown estates, covering their duties, stock, crops and the goods to be sent to court, and is usually dated to about 800.",
        details = "It is one of Charlemagne's capitularies, the written instructions issued by the king, and it includes a list of plants to be grown on the estates. The date given is about 800, and accounts differ by a few years.",
        rare = true,
    ),

    entry(
        databaseId = 124,
        kind = Kind.PRACTICE,
        section = "Frankish",
        title = "The oath of loyalty of 802, sworn to Charlemagne by the free men of his realm",
        originalTitle = "Sacramentum",
        author = "Charlemagne",
        year = 802,
        summary = "In 802 Charlemagne required the free men of his realm to swear a personal oath of loyalty to him, sworn before his royal envoys.",
        details = "The oath bound men aged twelve and over, and it made loyalty to the king a duty of all free subjects rather than only of his personal followers. It was issued with the general instructions for the royal envoys of that year, and accounts differ on how fully it was carried out.",
        rare = true,
    ),

    entry(
        databaseId = 124,
        kind = Kind.PRACTICE,
        section = "Latin",
        title = "The Carolingian minuscule, a clear book hand that spread through Charlemagne's realm about 800",
        originalTitle = "Caroline minuscule",
        author = "Scribes of the Frankish scriptoria",
        year = 800,
        summary = "The Carolingian minuscule, a clear and regular book hand, spread through the Frankish scriptoria around 800 and became the model for later Latin writing.",
        details = "Its spread is usually placed in the late eighth and early ninth centuries, and accounts differ on the place where it was first developed. Its lower-case letter forms are the ancestors of those used in modern printing.",
    ),

    entry(
        databaseId = 124,
        kind = Kind.PRACTICE,
        section = "Frankish",
        title = "The Creed sung with the filioque at the Frankish court, confirmed by the council of Aachen in 809",
        originalTitle = "Filioque",
        author = "Charlemagne's council at Aachen",
        year = 809,
        summary = "A council at Aachen in 809 confirmed the Frankish custom of singing the Creed with the word filioque, which teaches that the Holy Spirit comes from the Father and the Son.",
        details = "Pope Leo III accepted the teaching but would not add the word to the Creed at Rome, and the question became part of the long dispute between the Latin and Greek churches. Accounts differ on the exact course of the council, and the year is usually given as 809.",
        rare = true,
    ),

)
