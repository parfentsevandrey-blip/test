package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1340 to 1350: the Ayenbite of Inwyt, Boccaccio's Teseida, Machaut's Remede de Fortune, Villani's chronicle, the Poema de Alfonso XI and Konrad's Book of Nature. */
val db70Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "The Ayenbite of Inwyt, a Kentish moral treatise of 1340",
        originalTitle = "Ayenbite of Inwyt",
        author = "Dan Michel of Northgate",
        year = 1340,
        summary = "The Ayenbite of Inwyt is a Middle English prose translation of a French moral treatise, finished in the Kentish dialect in 1340 by Dan Michel of Northgate.",
        details = "The title means the again-biting of conscience, and the translation keeps the Kentish speech of its Canterbury author. Dan Michel names 1340 as the year he finished it, and the book survives in a single manuscript.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Boccaccio's Teseida, the first Italian epic in ottava rima",
        originalTitle = "Teseida delle nozze d'Emilia",
        author = "Giovanni Boccaccio",
        year = 1341,
        summary = "Boccaccio's Teseida is a long Italian verse epic of about 1341 and the first epic written in the eight-line ottava rima stanza.",
        details = "The poem of twelve books tells the story of Theseus and Emilia, and it was written at Naples in the years around 1339 to 1341. Boccaccio had already used the stanza in his shorter Filostrato, so the Teseida is the first epic in the stanza rather than its first use, and Chaucer later drew on it for his Knight's Tale.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Machaut's Remede de Fortune, a French love poem of about 1340",
        originalTitle = "Le Remede de Fortune",
        author = "Guillaume de Machaut",
        year = 1340,
        summary = "Guillaume de Machaut's Remede de Fortune is a French narrative poem of love and fortune, written about 1340, in which lyric verse is set within a story.",
        details = "The poem is one of the earliest long works of Machaut, the poet and composer, and it mixes narrative with lyric verse. Its date is usually given as about 1340, and accounts differ on whether it was finished before or after that year.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "Florentine",
        title = "Giovanni Villani's Nuova Cronica, a history of Florence in the vernacular",
        originalTitle = "Nuova Cronica",
        author = "Giovanni Villani",
        year = 1348,
        summary = "Giovanni Villani's Nuova Cronica is a history of Florence in the Florentine vernacular, and it ends with the plague of 1348 that killed its author.",
        details = "Villani is said to have begun the chronicle after the Roman jubilee of 1300, and his brother Matteo carried the history on to 1363. Accounts differ on how much of the text Giovanni revised before his death, so 1348 marks the end of his own part.",
    ),

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "Chronicles",
        title = "The Poema de Alfonso XI, a rhymed chronicle of the battle of the Salado",
        originalTitle = "Poema de Alfonso XI",
        author = "An unknown Castilian poet",
        year = 1348,
        summary = "The Poema de Alfonso XI is an anonymous Castilian chronicle in rhymed verse that celebrates the king's victory at the Salado in 1340, and it was probably written about 1348.",
        details = "The poem is written in cuaderna via, a stanza of four long lines with one rhyme, and it covers the reign of Alfonso XI. Its date is an estimate from its content, and accounts differ on whether it was written soon after the battle or later.",
        rare = true,
    ),

    entry(
        databaseId = 70,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Konrad von Megenberg's Book of Nature, a natural history in German prose",
        originalTitle = "Das Buch der Natur",
        author = "Konrad von Megenberg",
        year = 1350,
        summary = "Konrad von Megenberg finished his Book of Nature in German prose in 1350, a natural history of animals, plants, stones and the heavens drawn from Latin writers.",
        details = "Konrad was a canon at Regensburg, and his book adapts the Latin natural history of Thomas of Cantimpre for readers who did not know Latin. It is one of the earliest large books of natural history written in German.",
        rare = true,
    ),

)
