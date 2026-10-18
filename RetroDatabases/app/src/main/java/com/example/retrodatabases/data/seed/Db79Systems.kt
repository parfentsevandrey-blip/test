package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1250 to 1260: a Paris college that gave its name to a university, the Oxford provisions, a Castilian law code, Kublai's paper money and a Franciscan rule. */
val db79Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 79,
        kind = Kind.SYSTEM,
        section = "University",
        title = "The Sorbonne, Robert de Sorbon's college for poor masters at Paris",
        originalTitle = "College de Sorbon",
        author = "Robert de Sorbon",
        year = 1257,
        summary = "Robert de Sorbon founded a college for poor masters of theology at Paris in 1257, and its name was later given to the university as a whole.",
        details = "The college gave lodging to poor masters who studied theology at Paris, and it took its name from its founder. The year 1257 is the usual date for the foundation, and the college grew into the Sorbonne of later centuries.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Provisions of Oxford, a baronial plan for government in England",
        originalTitle = "Provisiones de Oxonia",
        author = "The English barons under Simon de Montfort",
        year = 1258,
        summary = "The Provisions of Oxford were agreed between King Henry III and the barons in June 1258 and set up a council of fifteen to share the work of government.",
        details = "The council was to advise the king, and Parliament was to meet three times a year, which limited the king's power to act alone. The date of 11 June 1258 is usually given for the agreement, and the pope annulled the reforms in 1261 before the civil war of the 1260s.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.SYSTEM,
        section = "Castile",
        title = "Alfonso X's Fuero Real, a law code written in Castilian for towns",
        originalTitle = "Fuero Real",
        author = "Alfonso X of Castile",
        year = 1255,
        summary = "Alfonso X granted the Fuero Real in 1255 as a law code for towns in Castile, written in Castilian rather than in Latin.",
        details = "The code is divided into four books, and it was a model for the larger Siete Partidas that were drafted in the years after it. The year 1255 is the usual date of the grant, which was first made to a town in the kingdom.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.SYSTEM,
        section = "Mongol China",
        title = "Zhongtong chao, Kublai Khan's paper money of 1260",
        originalTitle = "Zhongtong yuanbao jiaochao",
        author = "The Mongol court of Kublai Khan",
        year = 1260,
        summary = "Kublai Khan issued paper money called the Zhongtong chao in 1260, with notes backed by silver held by the government.",
        details = "The notes take their name from the Zhongtong reign period, which ran from 1260 to 1264, and they were valued in strings of cash. The year 1260 is the usual date of the first issue, and the court later struggled to keep the notes at their value.",
    ),

    entry(
        databaseId = 79,
        kind = Kind.SYSTEM,
        section = "Religious orders",
        title = "The Franciscan constitutions of Narbonne, a rule for the friars",
        originalTitle = "Constitutiones Narbonenses",
        author = "The Franciscan general chapter at Narbonne",
        year = 1260,
        summary = "The Franciscan general chapter at Narbonne in 1260 issued constitutions that set out the rules of the friars' houses and their government.",
        details = "The constitutions were issued under the minister general Bonaventure, and the order used them for many years afterwards. The year 1260 is the usual date for the chapter, which met at the southern French city of Narbonne.",
        rare = true,
    ),

)
