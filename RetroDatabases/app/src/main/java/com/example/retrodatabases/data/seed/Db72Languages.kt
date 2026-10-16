package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1320 to 1330: a Latin logic, a Chinese rhyme book, a Latin defence of peace, a Catalan chronicle, a Japanese Buddhist history in Literary Chinese and a Scottish letter in Latin. */
val db72Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Ockham's Summa logicae, a Latin treatise on terms and arguments",
        originalTitle = "Summa logicae",
        author = "William of Ockham",
        year = 1323,
        summary = "William of Ockham wrote his Summa logicae in Latin about 1323, a systematic treatise on terms, propositions and arguments that became a standard logic text.",
        details = "The work is usually dated to about 1323, before Ockham was summoned to Avignon in 1324, and it is divided into three parts. Accounts differ on how much of it was written in that year, so 1323 is the usual round date.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Zhongyuan yinyun, a rhyme book for northern song lyrics",
        originalTitle = "Zhongyuan yinyun",
        author = "Zhou Deqing",
        year = 1324,
        summary = "Zhou Deqing compiled the Zhongyuan yinyun in 1324, a rhyme book that sorted the characters of northern speech for writers of song lyrics and plays.",
        details = "The book was made for writers of the sanqu lyric and the zaju play, whose rhymes followed the northern speech of the Yuan period rather than older southern rhyme tables. The year 1324 is the date of its completion, and the book was printed in later editions.",
        rare = true,
    ),

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Marsilius of Padua's Defensor pacis, a Latin treatise on peace",
        originalTitle = "Defensor pacis",
        author = "Marsilius of Padua",
        year = 1324,
        summary = "Marsilius of Padua completed the Defensor pacis in 1324, a Latin treatise that argued for the authority of a general council and the citizens over the papacy.",
        details = "The treatise was completed in 1324 and condemned by Pope John XXII in a bull of October 1327. The author's date of completion is the one usually given, and the work was copied widely in the fourteenth century.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Kokan Shiren's Genko Shakusho, a Buddhist history in Literary Chinese of 1322",
        originalTitle = "Genko Shakusho",
        author = "Kokan Shiren",
        year = 1322,
        summary = "Kokan Shiren, a Zen monk, completed the Genko Shakusho in 1322, a history of Buddhist teaching in Japan and China written in Literary Chinese.",
        details = "The work is a chronicle of Buddhist schools and their succession, and it was written in the learned Literary Chinese used by monks of the period. The year 1322 is the completion date usually given.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Declaration of Arbroath, a Latin letter to the pope of 1320",
        originalTitle = "Declaration of Arbroath",
        author = "The barons and clergy of Scotland",
        year = 1320,
        summary = "The Declaration of Arbroath is a Latin letter of 6 April 1320 in which the Scottish nobles and clergy wrote to Pope John XXII to defend the independence of Scotland.",
        details = "The letter is dated at Arbroath on 6 April 1320 and sent to the pope in Avignon. Its best-known passage argues that a king is accepted only while he defends the freedom of the people, and historians still debate how far it reflects a single author's hand.",
    ),

    entry(
        databaseId = 72,
        kind = Kind.LANGUAGE,
        section = "Catalan",
        title = "Muntaner's Chronicle, a Catalan account of the Crown of Aragon",
        originalTitle = "Cronica",
        author = "Ramon Muntaner",
        year = 1325,
        summary = "Ramon Muntaner wrote his Chronicle in Catalan in the years about 1325 to 1328, a personal history of the Crown of Aragon and of the Catalan companies in the eastern Mediterranean.",
        details = "The Chronicle is dated to the years about 1325 to 1328, and accounts differ on the exact span of its composition. Its author had served in the Catalan companies, and his account of their campaigns is one of the earliest long prose works in Catalan.",
        rare = true,
    ),

)
