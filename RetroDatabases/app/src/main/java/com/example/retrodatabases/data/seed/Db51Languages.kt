package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1530 to 1540: a French grammar for English readers, two dictionaries, Luther's Bible of 1534, a Portuguese grammar and a Latin grammar of causes. */
val db51Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Palsgrave's French grammar for English readers",
        originalTitle = "Lesclarcissement de la langue francoyse",
        author = "John Palsgrave",
        year = 1530,
        summary = "Palsgrave's grammar of 1530 taught the French language to English readers in English, and it is among the earliest grammars of French printed in England.",
        details = "The book was printed in London in 1530, and its title means a clearing up of the French language. It sets out the French parts of speech and verbs for English learners, and it is one of the first French grammars written for an English audience.",
        rare = true,
    ),

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Elyot's Latin-English dictionary of 1538",
        originalTitle = "The Dictionary of Syr Thomas Eliot knight",
        author = "Sir Thomas Elyot",
        year = 1538,
        summary = "Elyot's dictionary of 1538 gave the English meanings of Latin words, and it is one of the first Latin-English dictionaries printed in England.",
        details = "Thomas Berthelet printed the dictionary in London in 1538, and Elyot wrote it so that English readers could follow the Latin words in their books. Later editions enlarged it.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "Luther's complete German Bible of 1534",
        originalTitle = "Biblia: das ist die gantze Heilige Schrifft Deudsch",
        author = "Martin Luther",
        year = 1534,
        summary = "Luther's whole Bible in German, printed at Wittenberg in 1534, gave the German of the scriptures a standard form that spread through the printing trade.",
        details = "The complete German Bible was printed at Wittenberg in 1534, after Luther's New Testament of 1522. Its wide use in print helped to fix a common German for religious writing.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Oliveira's grammar of the Portuguese language",
        originalTitle = "Grammatica da lingoagem portuguesa",
        author = "Fernao de Oliveira",
        year = 1536,
        summary = "Oliveira's grammar of 1536 is commonly named the first printed grammar of the Portuguese language, and it describes the language in Portuguese.",
        details = "The book was printed at Lisbon in 1536, and it treats the sounds, letters and parts of speech of Portuguese. Its place as the first printed Portuguese grammar is the common account.",
        rare = true,
    ),

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Estienne's French-Latin dictionary of 1539",
        originalTitle = "Dictionaire francoislatin, autrement dict les mots francois, avec les latins qui y correspondent",
        author = "Robert Estienne",
        year = 1539,
        summary = "Estienne's dictionary of 1539 gave French words with the Latin words that answer them, so that a reader could turn a French word into Latin.",
        details = "The dictionary was printed in Paris in 1539 by the Estienne press, which printed many learned word books of the period. Its title explains its plan, French words followed by the Latin that corresponds to them, and it was used by readers who wrote Latin from French.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Scaliger's thirteen books on the causes of Latin",
        originalTitle = "De causis linguae Latinae libri tredecim",
        author = "Julius Caesar Scaliger",
        year = 1540,
        summary = "Scaliger's book of 1540 explained the forms of Latin by their causes, in thirteen books, each treating a part of Latin grammar.",
        details = "The book was printed at Lyon in 1540 and divided into thirteen books. Its method of giving causes for grammatical forms was discussed by later grammarians.",
        rare = true,
    ),

)
