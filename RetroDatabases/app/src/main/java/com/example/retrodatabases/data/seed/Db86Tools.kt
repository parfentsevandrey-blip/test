package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1185 to 1188: Glanvill's law treatise, Huguccio's canon law Summa, a Chinese primer of the Changes and Andreas on courtly love. */
val db86Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 86,
        kind = Kind.TOOL,
        section = "Law",
        title = "Glanvill's treatise on the laws and customs of England, about 1188",
        originalTitle = "Tractatus de legibus et consuetudinibus regni Anglie",
        author = "Ranulf de Glanvill, by tradition",
        year = 1188,
        summary = "The treatise on the laws and customs of the kingdom of England, written about 1188 and traditionally credited to Ranulf de Glanvill, is one of the earliest systematic books on English law.",
        details = "Scholars date the work to 1187 to 1189 and dispute its author, with Hubert Walter and Henry II among the names proposed. Later English law writers drew on the book, and the year 1188 is the date most often given.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.TOOL,
        section = "Canon law",
        title = "Huguccio's Summa, a Bologna commentary on the canon law of the schools",
        originalTitle = "Summa super Decretum",
        author = "Huguccio of Pisa",
        year = 1188,
        summary = "Huguccio of Pisa wrote a Summa on Gratian's Decretum around 1188, a commentary that taught students how to read the canon law.",
        details = "He taught canon law at Bologna, and accounts give the writing of the Summa as falling in the years around 1188 to 1190. Huguccio later became bishop of Ferrara, and copies of his commentary survive in several libraries.",
    ),

    entry(
        databaseId = 86,
        kind = Kind.TOOL,
        section = "Learning",
        title = "Zhu Xi's Introduction to the Study of the Changes, a Chinese primer of 1186",
        originalTitle = "Yixue qimeng",
        author = "Zhu Xi",
        year = 1186,
        summary = "Zhu Xi completed his Introduction to the Study of the Changes in 1186, a Chinese primer that set out the diagrams and numbers of the Yijing for students.",
        details = "The primer gathers the hexagram diagrams and the numerical patterns of the Yijing, and it shaped the Neo-Confucian study of that classic in the centuries after. The year 1186 is the completion date usually given for the work.",
        rare = true,
    ),

    entry(
        databaseId = 86,
        kind = Kind.TOOL,
        section = "Courtly love",
        title = "Andreas Capellanus's De amore, a Latin handbook of courtly love",
        originalTitle = "De amore",
        author = "Andreas Capellanus",
        year = 1185,
        summary = "Andreas Capellanus wrote his Latin De amore in the mid-1180s, a handbook of courtly love with rules for lovers and dialogues between a man and a woman.",
        details = "The work is usually dated to about 1184 to 1186, and its rules are set out in dialogues that show the courtly world of the period. Scholars still disagree on whether it is a serious guide to love or a satire on courtly manners.",
    ),

)
