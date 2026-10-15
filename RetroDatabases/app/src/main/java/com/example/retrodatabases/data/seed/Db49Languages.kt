package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1550 to 1560: a Slovene primer, a keyword cipher, French algebra, a Nahuatl vocabulary, a survey of tongues and the equals sign. */
val db49Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "Slovene",
        title = "Trubar's Abecedarium, the first Slovene primer",
        originalTitle = "Abecedarium",
        author = "Primoz Trubar",
        year = 1550,
        summary = "Trubar's Abecedarium of 1550 taught the alphabet to Slovene readers, and it was printed together with his Catechism as the first books in Slovene.",
        details = "The primer was printed in the Holy Roman Empire in 1550, and accounts differ on the printing place, with Tubingen once given and Schwabisch Hall favoured by recent scholarship. Its eight leaves were a reading aid for the Catechism, and later reprints followed in 1555 and 1566.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "Ciphers",
        title = "Bellaso's keyword cipher, La cifra",
        originalTitle = "La cifra del Sig. Giovan Battista Bellaso",
        author = "Giovan Battista Bellaso",
        year = 1553,
        summary = "Bellaso's book of 1553 described a cipher that shifts each letter by the letter of a repeating keyword, a method later credited to Vigenere.",
        details = "The book was printed in Venice in 1553, and it set out the keyed shifts that were later called the Vigenere cipher, a name the method received in the nineteenth century. The keyword sets the shift for each letter, so the same plain letter can be sent in several different ways.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "Algebra",
        title = "Peletier's algebra in French, L'algebre",
        originalTitle = "L'Algebre",
        author = "Jacques Peletier du Mans",
        year = 1554,
        summary = "Peletier's L'Algebre of 1554 taught the rules of algebra in French, so that readers without Latin could follow the working of equations.",
        details = "The first edition was printed at Lyon in 1554, and it is counted among the earliest practical algebra textbooks written in French. Claims that it was the first printed algebra in French are weaker than they sound, because accounts differ on which French book of this kind came first.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Meigret's French grammar in his phonetic spelling",
        originalTitle = "Le Trette de la grammere francoeze",
        author = "Louis Meigret",
        year = 1550,
        summary = "Meigret's grammar of 1550 was the earliest grammar of French written in French, and it was set in the phonetic spelling that its author argued for.",
        details = "It was printed at Paris by Chrestien Wechel in 1550, and a run of polemics on the spelling followed in 1550 and 1551, one of them answering an apology by Jacques Peletier. Its spelling is hard to read, and its grammar was valued fully only by twentieth-century structural linguists.",
        rare = true,
    ),

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "Languages of the world",
        title = "Gesner's Mithridates, a survey of tongues",
        originalTitle = "Mithridates, de differentiis linguarum",
        author = "Conrad Gesner",
        year = 1555,
        summary = "Gesner's Mithridates of 1555 surveyed many languages and gave the Lord's Prayer in several of them, an early printed comparison of tongues.",
        details = "The book was printed at Zurich in 1555, and it compares the words of many languages with the Lord's Prayer given in a number of them. Gesner gathered his material from travellers' reports and from earlier printed books, so the choice of languages reflects what he could obtain.",
    ),

    entry(
        databaseId = 49,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Recorde's equals sign in The Whetstone of Witte",
        originalTitle = "The Whetstone of Witte",
        author = "Robert Recorde",
        year = 1557,
        summary = "Recorde's Whetstone of Witte of 1557 introduced the equals sign, two parallel lines of one length, to English readers of algebra.",
        details = "The book was printed in London in 1557, and Recorde explained that two parallel lines of one length were his sign, because no two things can be more equal. The sign is generally given as the first printed use of the equals sign, and later algebra books adopted it.",
    ),

)
