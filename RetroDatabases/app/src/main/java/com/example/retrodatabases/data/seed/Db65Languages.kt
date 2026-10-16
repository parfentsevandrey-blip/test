package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1390 to 1400: an English poem, an Irish manuscript, a revised English Bible, a French songbook, an English anthology and a Catalan dream. */
val db65Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "Gower's Confessio Amantis, the English poem of about 1390",
        originalTitle = "Confessio Amantis",
        author = "John Gower",
        year = 1390,
        summary = "John Gower finished the first version of his long English poem Confessio Amantis about 1390, a confession of a lover heard by a priest of Venus.",
        details = "Gower gave the first version to Richard II and revised the poem in about 1392 to 1393, when the dedication was changed to Henry of Derby, later Henry IV. Accounts differ on the exact years of the two versions, so 1390 is the usual date of the first.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "Irish",
        title = "The Book of Ballymote, an Irish manuscript of about 1390",
        originalTitle = "Leabhar Bhaile an Mhota",
        author = "Manus O'Duignan and other scribes",
        year = 1390,
        summary = "The Book of Ballymote is a large Irish manuscript of about 1390, made at Ballymote Castle in County Sligo, and it gathers histories, genealogies, law and the grammar Auraicept na nEces.",
        details = "It was compiled for the MacDonagh patron Tomaltagh, with Manus O'Duignan doing most of the work, and it is now Royal Irish Academy MS 23 P 12. Reference works give 1390 or 1391, while one scholar proposes 1384 to 1405, so the date is a range and not a single year.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The later Wycliffite Bible, a revised English text of about 1395",
        originalTitle = "Later Version of the Wycliffite Bible",
        author = "Wycliffite translators, with John Purvey credited for the revision",
        year = 1395,
        summary = "The later Wycliffite Bible is a revised English translation of the Bible that reads more plainly than the early version, and it was completed by about 1395.",
        details = "Most scholars credit the revision to John Purvey and date the later text to about 1395 and the early text to about 1382. Both dates are estimates drawn from the manuscripts, not from firm records.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "Music",
        title = "The Chantilly Codex, a French songbook of about 1390",
        originalTitle = "Chantilly Codex",
        author = "Several composers of the late fourteenth century",
        year = 1390,
        summary = "The Chantilly Codex is a French songbook of about 1390 that gathers secular songs written in the ars subtilior, a notation of intricate rhythms.",
        details = "The codex takes its name from the Chantilly library where it is kept. Most scholars date it to about 1390, though some place its making from about 1380 to 1400, and it is the main source for the style.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "The Vernon manuscript, a Middle English anthology of about 1390",
        originalTitle = "Vernon manuscript",
        author = "Several scribes of the late fourteenth century",
        year = 1390,
        summary = "The Vernon manuscript is a large English anthology of about 1390, copied in several hands, with mainly devotional prose and verse and a few romances.",
        details = "It is kept in the Bodleian Library at Oxford, and its date is an estimate from the handwriting, with accounts giving a span of some years around 1390. The scribes are not named, so the date rests on the script alone.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.LANGUAGE,
        section = "Catalan",
        title = "Metge's Lo Somni, a Catalan dream dialogue of 1399",
        originalTitle = "Lo Somni",
        author = "Bernat Metge",
        year = 1399,
        summary = "Lo Somni is a Catalan prose dream dialogue written in 1399 by a Barcelona jurist and royal secretary, and it draws on classical authors.",
        details = "Metge served the Aragonese crown as a secretary, and the work is usually dated to 1398 or 1399, with 1399 the year most often given. The dream form lets the author set Catalan prose beside the learning of the ancients.",
        rare = true,
    ),

)
