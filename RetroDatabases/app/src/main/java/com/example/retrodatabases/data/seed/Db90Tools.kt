package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1140 to 1150: al-Khwarizmi's algebra in Latin, Hermann's treatise on the nature of things, William's dialogue on natural philosophy, Bhaskara's arithmetic and an alchemical handbook. */
val db90Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Robert of Chester's Latin algebra of al-Khwarizmi",
        originalTitle = "Liber algebre et almucabola",
        author = "Robert of Chester",
        year = 1145,
        summary = "Robert of Chester finished a Latin translation of al-Khwarizmi's algebra in 1145, a handbook that taught the solving of equations by restoring and balancing.",
        details = "The translation was made in Spain, probably at Segovia, and it introduced the word algebra into Latin. The year 1145 is the date usually given for the translation, about six decades before Fibonacci's Liber abbaci of 1202.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.TOOL,
        section = "Natural philosophy",
        title = "William of Conches's Dragmaticon, a dialogue on natural philosophy",
        originalTitle = "Dragmaticon philosophiae",
        author = "William of Conches",
        year = 1145,
        summary = "William of Conches wrote the Dragmaticon, a Latin dialogue on natural philosophy, in the mid-1140s as a fuller treatment than his earlier Philosophia.",
        details = "The work is usually dated between 1144 and 1149, and 1145 is given here as a middle year, since the exact date within that span is not settled. It takes the form of a dialogue in which the author answers the questions of a noble listener.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.TOOL,
        section = "Natural philosophy",
        title = "Hermann of Carinthia's De essentiis, a Latin treatise on the nature of things",
        originalTitle = "De essentiis",
        author = "Hermann of Carinthia",
        year = 1143,
        summary = "Hermann of Carinthia completed De essentiis in 1143 at Beziers, a Latin treatise in which he set out his own views on the nature of things.",
        details = "It is the work in which Hermann sets out his own views rather than translating from Arabic. The place of composition and the date are given in a modern sale catalogue, and the work is not otherwise well known.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Bhaskara's Lilavati, arithmetic taught in verse",
        originalTitle = "Lilavati",
        author = "Bhaskara II",
        year = 1150,
        summary = "Bhaskara II wrote the Lilavati about 1150 as the first part of his Siddhanta Shiromani, an Indian arithmetic in verse with problems for the student.",
        details = "The book covers the rules of counting, fractions, the rule of three and the sums of series, and it was named after his daughter in later tradition. The date 1150 is the one usually given for the whole Siddhanta Shiromani, and the Lilavati is taken to be part of the same work.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.TOOL,
        section = "Alchemy",
        title = "Robert of Chester's Latin book of the composition of alchemy",
        originalTitle = "Liber de compositione alchemiae",
        author = "Robert of Chester",
        year = 1144,
        summary = "Robert of Chester finished his Latin translation of an Arabic alchemical book on 11 February 1144, which is reputed to be the earliest Latin text of its kind.",
        details = "The book was attributed in the Latin to the Umayyad prince Khalid ibn Yazid, but that attribution is false, and the dialogue presents the Byzantine monk Morienus as his teacher. Doubts remain over whether Robert wrote the preface, while the 1144 date for the translation is generally accepted.",
        rare = true,
    ),

)
