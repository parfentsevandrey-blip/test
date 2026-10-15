package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1560 to 1570: Porta's cipher book, Dee's Monas, a Latin thesaurus, a Welsh grammar, a Gaelic prayer book and Ascham's Scholemaster. */
val db48Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Ciphers",
        title = "Porta's cipher book on secret letters",
        originalTitle = "De furtivis literarum notis",
        author = "Giambattista della Porta",
        year = 1563,
        summary = "Porta's book of 1563 on secret writing described a reciprocal cipher in which a key letter chooses one of thirteen rows of paired letters.",
        details = "The four books of De furtivis literarum notis were printed in Naples in 1563 and cover ciphers and the hiding of messages. The same table encrypts and decrypts, which is why such systems are called reciprocal, and the guide to thirteen rows models the idea without copying Porta's own table.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Symbols",
        title = "Dee's Monas hieroglyphica, a sign for the cosmos",
        originalTitle = "Monas hieroglyphica",
        author = "John Dee",
        year = 1564,
        summary = "Dee's Monas of 1564 packs the symbols of the sun, the moon and the planets into one sign, which he explained in twenty-four numbered propositions.",
        details = "The book was printed in Antwerp in 1564 and dedicated to the emperor Maximilian II. Readers have long disputed whether its propositions were meant as mathematics or as mystical teaching, and the sign is still discussed as an early symbolic notation.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Cooper's Latin and English thesaurus",
        originalTitle = "Thesaurus Linguae Romanae et Britannicae",
        author = "Thomas Cooper",
        year = 1565,
        summary = "Cooper's thesaurus of 1565 gave English meanings for Latin words, and later editions followed during the same century.",
        details = "The book was printed in London in 1565 as a store of Latin words with English equivalents for schoolroom and scholarly use. Accounts differ on how many of its entries Cooper drew from earlier Latin dictionaries.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Welsh",
        title = "Gruffydd Robert's Welsh grammar",
        originalTitle = "Dosparth Byrr",
        author = "Gruffydd Robert",
        year = 1567,
        summary = "Gruffydd Robert's grammar of 1567 set out the forms of Welsh for readers, and it was printed in Milan while the author worked there for Cardinal Borromeo.",
        details = "The first part was printed in Milan in 1567, when the author was in Italy working for the cardinal archbishop. Later parts followed years afterwards, so the grammar is a series of works rather than one book of that year.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Gaelic",
        title = "Carswell's Gaelic prayer book of 1567",
        originalTitle = "Foirm na n-Urrnuidheadh",
        author = "John Carswell",
        year = 1567,
        summary = "Carswell's prayer book of 1567 is often named as the first book printed in Scottish Gaelic, and it set out a written form for the language.",
        details = "It was printed in Edinburgh in 1567, when Carswell was a Reformed superintendent in Argyll. Accounts differ on how settled its spelling was, so the book is usually described as an early step toward Gaelic printing rather than a fixed standard.",
        rare = true,
    ),

    entry(
        databaseId = 48,
        kind = Kind.LANGUAGE,
        section = "Teaching",
        title = "Ascham's The Scholemaster on teaching Latin",
        originalTitle = "The Scholemaster",
        author = "Roger Ascham",
        year = 1570,
        summary = "Ascham's Scholemaster of 1570 gave advice on teaching Latin to boys, and it appeared in print two years after the author's death.",
        details = "The book was printed in London in 1570, and Ascham had died in 1568. It is read as a guide to grammar teaching, and accounts differ on how much of its advice was new to English schools.",
    ),

)
