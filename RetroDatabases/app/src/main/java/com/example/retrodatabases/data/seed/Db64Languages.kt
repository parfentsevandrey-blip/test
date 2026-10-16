package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1400 to 1410: a Greek grammar of questions, two manuscripts of tales, a French book of ladies, a Ming college of translation and a Tibetan treatise. */
val db64Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Chrysoloras's Greek grammar of questions and answers",
        originalTitle = "Erotemata",
        author = "Manuel Chrysoloras",
        year = 1400,
        summary = "Chrysoloras's Erotemata, a Greek grammar set out as questions and answers, was written for Italian students of Greek in the years around 1400.",
        details = "Chrysoloras taught Greek at Florence from 1397, and the grammar was printed at Milan in 1484, long after it had been copied and used in manuscript. Accounts differ on the exact year the Erotemata was composed, so 1400 is a round date for the period.",
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "The Hengwrt Chaucer, a Canterbury Tales copy of about 1400",
        originalTitle = "The Canterbury Tales",
        author = "Geoffrey Chaucer",
        year = 1400,
        summary = "The Hengwrt manuscript copies Chaucer's Canterbury Tales in a hand dated about 1400, and it is among the oldest surviving witnesses to the poem.",
        details = "The manuscript takes its name from the Hengwrt estate in Wales where it was kept, and its scribe is usually named as Adam Pinkhurst. The date of about 1400 is an estimate from the handwriting, and editors still compare its text with the later Ellesmere copy.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "Welsh",
        title = "The Red Book of Hergest, a Welsh manuscript of tales and poems",
        originalTitle = "Llyfr Coch Hergest",
        author = "Several scribes of the Hergest circle",
        year = 1400,
        summary = "The Red Book of Hergest is a Welsh manuscript that gathers tales, poems and prose copied in the decades around 1400.",
        details = "It takes its name from the Hergest family of Herefordshire and holds the Mabinogion among its contents. Its dates are given as a span from about 1375 to about 1410, and accounts differ on when most of the copying was done.",
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Christine de Pizan's City of Ladies, written in French",
        originalTitle = "Le Livre de la cite des dames",
        author = "Christine de Pizan",
        year = 1405,
        summary = "Christine de Pizan completed her Book of the City of Ladies in French in 1405, a defence of women built from examples drawn from history and legend.",
        details = "The book was written in French, the language of the royal court, and it answers writers who had mocked women. The year 1405 is the date usually given for its completion.",
    ),

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "Translation",
        title = "The Siyi guan, the Ming college of translation",
        originalTitle = "Siyi guan",
        author = "The Yongle court",
        year = 1407,
        summary = "The Ming court set up the Siyi guan in 1407 to train interpreters in the languages of its neighbours and to compile bilingual word lists.",
        details = "The college stood inside the Hanlin Academy and had eight language divisions, and its staff compiled the Huayi yiyu word lists. The date 1407 is the founding date, while one study dates the Persian glossary itself to the fifteenth century, so not every word list belongs to 1407.",
        rare = true,
    ),

    entry(
        databaseId = 64,
        kind = Kind.LANGUAGE,
        section = "Tibetan",
        title = "Tsongkhapa's Great Treatise on the Stages of the Path",
        originalTitle = "Lam rim chen mo",
        author = "Tsongkhapa",
        year = 1402,
        summary = "Tsongkhapa completed the Great Treatise on the Stages of the Path in 1402, a long Tibetan prose work that became a central text of the Gelug school.",
        details = "The treatise was written in Tibetan for monks who studied the path to enlightenment in stages, and it draws on earlier Indian and Tibetan writers. The year 1402 is the date usually given for its completion.",
        rare = true,
    ),

)
