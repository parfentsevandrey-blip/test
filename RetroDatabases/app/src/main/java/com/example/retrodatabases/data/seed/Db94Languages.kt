package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1100 to 1110: a Latin crusade narrative, an Irish manuscript, a Latin tale collection, a Latin treatise, an English history and a corrected Latin Bible. */
val db94Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "Crusade",
        title = "The Gesta Francorum, a Latin account of the First Crusade",
        originalTitle = "Gesta Francorum et aliorum Hierosolimitanorum",
        author = "An anonymous knight of the crusading army",
        year = 1100,
        summary = "The Gesta Francorum is a Latin narrative of the First Crusade, written by an anonymous knight about 1100 to 1101 and reworked by later chroniclers.",
        details = "The narrative follows the march from Constantinople to Antioch and Jerusalem between 1096 and 1099, so its events come before its writing. Scholars date the first version to about 1100 to 1101, and accounts differ on the exact year because the text survives in several forms.",
    ),

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "The Book of the Dun Cow, an Irish manuscript from Clonmacnoise",
        originalTitle = "Lebor na hUidre",
        author = "Mael Muire mac Ceilechair and other scribes",
        year = 1100,
        summary = "The Book of the Dun Cow is one of the oldest surviving Irish manuscripts, a collection of sagas, genealogies and religious texts written in Irish about 1100.",
        details = "Its main text was written at Clonmacnoise before 1106, the year its chief scribe, Mael Muire mac Ceilechair, is said to have died, and later additions followed. Some accounts date the manuscript to the twelfth century, so about 1100 is an approximation within that span.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Petrus Alfonsi's Disciplina clericalis, Latin tales for clerks",
        originalTitle = "Disciplina clericalis",
        author = "Petrus Alfonsi",
        year = 1110,
        summary = "Petrus Alfonsi's Disciplina clericalis is a Latin collection of short tales, fables and proverbs for the instruction of clerks, drawn from Arabic and Hebrew sources.",
        details = "Alfonsi was a Jew before his baptism at Huesca on 29 June 1106, and the book was written after that date. Its date of composition is not recorded, so about 1110 is the usual estimate.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "Theology",
        title = "Anselm's De concordia, a Latin treatise on grace and free will",
        originalTitle = "De concordia",
        author = "Anselm of Canterbury",
        year = 1108,
        summary = "Anselm's De concordia is a Latin treatise on how God's foreknowledge and grace agree with human free will, written while he was archbishop of Canterbury.",
        details = "The usual dating is 1107 to 1108, which places the work after Anselm's return from exile in 1106. The treatise carries on the argument about the human will that runs through his later writings.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "History",
        title = "Eadmer's Historia novorum, a Latin history of recent events in England",
        originalTitle = "Historia novorum in Anglia",
        author = "Eadmer of Canterbury",
        year = 1109,
        summary = "Eadmer's Historia novorum in Anglia is a Latin history of English church affairs in his own time, written at Canterbury by a monk who had been close to Archbishop Anselm.",
        details = "The usual dating runs from about 1109 to 1115, so the record uses the start of that span. The book centres on Anselm's years as archbishop, and it is one of the main sources for the disputes with the crown in those years.",
        rare = true,
    ),

    entry(
        databaseId = 94,
        kind = Kind.LANGUAGE,
        section = "Scripture",
        title = "Stephen Harding's corrected Latin Bible of Citeaux",
        originalTitle = "Latin Bible, Citeaux text",
        author = "Stephen Harding",
        year = 1109,
        summary = "Stephen Harding, abbot of Citeaux, had the Latin Bible text corrected against Hebrew readings about 1109, an early effort to purify the text used in Cistercian houses.",
        details = "The usual date is about 1109, and the corrections are described as made with help from Hebrew readings. The spread of the corrected text came in the decades that followed.",
        rare = true,
    ),

)
