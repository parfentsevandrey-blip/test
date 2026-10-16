package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1420 to 1430: Bruni's rules for translation, Guarino's Latin grammar, a French poem on Joan of Arc, a Persian Shahnama and Florentine abacus teaching in Italian. */
val db62Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "Translation",
        title = "Bruni's rules for turning Greek into Latin",
        originalTitle = "De interpretatione recta",
        author = "Leonardo Bruni",
        year = 1424,
        summary = "Bruni's treatise on translation asks the translator to render the sense of a Greek text in good Latin rather than word for word.",
        details = "Bruni wrote the treatise in Florence in the 1420s, and the usual dating is about 1424 to 1426, so the year 1424 is used here and accounts differ on the exact year. It criticized translators who kept the Greek word order and vocabulary, and it set out the aim of keeping the meaning of the original.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "Latin grammar",
        title = "Guarino's Latin grammar of the Regulae",
        originalTitle = "Regulae grammaticales",
        author = "Guarino Veronese",
        year = 1425,
        summary = "Guarino's Regulae grammaticales is a short Latin grammar from the Veronese teacher, used to teach boys the rules of Latin.",
        details = "Accounts disagree on when it was written: Britannica gives 1418, while a biography in the Rutgers scholars' database gives a window of about 1425 to 1450. The year 1425 is used here as the earliest date in that wider window, and the Rutgers source says he taught from the grammar without giving a writing date.",
        rare = true,
    ),

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "French verse",
        title = "Christine de Pizan's poem on Joan of Arc",
        originalTitle = "Ditie de Jehanne d'Arc",
        author = "Christine de Pizan",
        year = 1429,
        summary = "Christine de Pizan's French poem of 1429 praises Joan of Arc and her victories while the war was still being fought.",
        details = "The poem is dated to 1429, after Joan's relief of Orleans in May of that year. It was written in French verse by a writer who lived at the royal court in Paris, and it is among the earliest poems about Joan.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "Persian verse",
        title = "The Shahnama made for Prince Baysunghur",
        originalTitle = "Baysunghur Shahnama",
        author = "Workshop of Prince Baysunghur at Herat",
        year = 1430,
        summary = "The Shahnama made for Prince Baysunghur at Herat is a lavish copy of Firdausi's Persian epic in verse.",
        details = "The manuscript is dated to about 1430 in most accounts, and it was made at the Timurid court of Herat. It copies the verse of Firdausi's epic about the kings of Persia, and its calligraphy and paintings are studied by historians of the book.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "Italian arithmetic",
        title = "Arithmetic taught in Italian in Florence",
        originalTitle = "Libro d'abaco",
        author = "Florentine abacus masters",
        year = 1420,
        summary = "Florentine abacus masters taught the sons of merchants their arithmetic in Italian rather than in Latin.",
        details = "Their books gave the rules of exchange, partnership and interest in the vernacular so that a merchant's son could read them without Latin. The year 1420 stands for this tradition, which ran through most of the fifteenth century.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.LANGUAGE,
        section = "Hussite songs",
        title = "The Jistebnice hymnal of the Hussites",
        originalTitle = "Jistebnicky kancional",
        author = "Hussite compilers in Bohemia",
        year = 1420,
        summary = "The Jistebnice hymnal is a Czech songbook of the Hussite movement, written for worship in the Czech language.",
        details = "The hymnal is dated to the early fifteenth century, and the year 1420 is used here as a convention for the decade. Its Czech chants and hymns show the vernacular worship that the Hussites defended in the years of their wars.",
        rare = true,
    ),

)
