package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Texts and languages of 500 to 510: Boethius's arithmetic text, Priscian's grammar, the Philoxenian Syriac New Testament and Liu Xie's handbook of literary craft. */
val db154Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 154,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Boethius's De institutione arithmetica, a two-book Latin text that reworked Nicomachus for Latin readers",
        originalTitle = "De institutione arithmetica",
        author = "Boethius",
        year = 500,
        summary = "Boethius's two-book Latin arithmetic reworked the Introduction to Arithmetic of Nicomachus for Latin readers, and medieval schools used it as a textbook.",
        details = "Scholars date Boethius's mathematical works as a group to about 500 to 506, and this record uses 500, the start of that range. The treatise follows Nicomachus of Gerasa closely, trimming some material and expanding harder passages.",
        rare = true,
    ),

    entry(
        databaseId = 154,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Priscian's Institutiones grammaticae in eighteen books, finished before 526 and read in schools for centuries",
        originalTitle = "Institutiones grammaticae",
        author = "Priscian of Caesarea",
        year = 500,
        summary = "Priscian's eighteen-book Latin grammar became the standard grammar of the medieval schools, and its last two books treat syntax.",
        details = "Priscian's floruit is put at about 500 and the work was finished before 526, so this record uses 500. The first sixteen books treat letters, syllables and the parts of speech, and the last two treat syntax.",
        rare = true,
    ),

    entry(
        databaseId = 154,
        kind = Kind.LANGUAGE,
        section = "Syriac",
        title = "The Philoxenian Syriac New Testament, a revision of the Peshitta made for Philoxenus of Mabbug about 508",
        originalTitle = "Philoxenian version",
        author = "Polycarp, for Philoxenus of Mabbug",
        year = 508,
        summary = "A Syriac revision of the New Testament made for the bishop Philoxenus of Mabbug reworked the older Peshitta text.",
        details = "Polycarp, a chorepiscopus, did the work for Philoxenus, and accounts differ on the date: one reference gives 507 or 508 and an older encyclopedia about 505. This record uses 508, and how much of the text survives is also disputed.",
        rare = true,
    ),

    entry(
        databaseId = 154,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Liu Xie's Wenxin diaolong, a fifty-chapter handbook of literary craft written about 501",
        originalTitle = "Wenxin diaolong",
        author = "Liu Xie",
        year = 501,
        summary = "Liu Xie's fifty-chapter survey of literary craft set out the principles of writing, the literary genres and the rules of style.",
        details = "The book is dated to about 501 to 502, and this record uses 501. It moves from first principles through the genres to criticism, and it is one of the earliest systematic works of Chinese literary criticism.",
    ),

)
