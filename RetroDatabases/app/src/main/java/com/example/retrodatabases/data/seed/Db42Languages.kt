package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1620 to 1630: a manual alphabet, a Japanese grammar, an English word list, a Kikongo catechism, a shorthand and a Slavonic lexicon. */
val db42Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Deaf education",
        title = "Bonet's manual alphabet for the deaf",
        originalTitle = "Reduccion de las letras y arte para ensenar a ablar los mudos",
        author = "Juan Pablo Bonet",
        year = 1620,
        summary = "Bonet's treatise of 1620 gives a one-handed alphabet, with a handshape for each letter, as a first step in teaching deaf pupils to read and to speak.",
        details = "The book was printed in Madrid in 1620. The hand alphabet is usually traced to Pedro Ponce de Leon, whose teaching Bonet drew on, so the alphabet is not presented here as his invention. Some sources date the book to the mid-1620s, but the Madrid date of 1620 is the common one.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Rodrigues's grammar of spoken Japanese",
        originalTitle = "Arte da lingoa de Iapam",
        author = "Joao Rodrigues",
        year = 1620,
        summary = "A grammar of the Japanese language in Portuguese, printed at Nagasaki in 1620 by the Jesuit press, that gave missionaries a systematic account of the spoken language.",
        details = "Rodrigues was a Portuguese Jesuit who had lived in Japan for decades, and he wrote the grammar for missionaries learning the language. It was printed at Nagasaki in 1620, and it counts among the earliest grammars of Japanese written in a European language.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Cockeram's dictionary of hard English words",
        originalTitle = "The English Dictionarie: or, an Interpreter of hard English words",
        author = "Henry Cockeram",
        year = 1623,
        summary = "An English dictionary of 1623 that explains hard English words in plainer English, and one of the earliest word lists of its kind in English.",
        details = "Cockeram's book was printed in London in 1623 and gives short explanations for words that its readers found hard. Most of its entries are English words explained in English, and writers differ on how far it should be counted as the first dictionary of that kind.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Kikongo",
        title = "Cardoso's catechism in Kikongo",
        originalTitle = "Doutrina Christam",
        author = "Mattheus Cardoso",
        year = 1624,
        summary = "A Christian catechism in the Kikongo language, printed in Lisbon in 1624 for the Kongo mission, and often named as one of the first books printed in a Bantu language.",
        details = "The catechism was printed in Lisbon in 1624 and was meant for use in the Kongo mission, with Kikongo text set in the Latin alphabet. Its claim to be among the first printed books in a Bantu language rests on the printing date of 1624, which is the date given here.",
        rare = true,
    ),

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Shelton's Tachygraphy, a shorthand of 1626",
        originalTitle = "Tachygraphy",
        author = "Thomas Shelton",
        year = 1626,
        summary = "Shelton's shorthand system, printed in London in 1626, let a writer take down speech in short marks, and it was used for decades after its first printing.",
        details = "The system was printed in 1626 and reprinted in later editions. Samuel Pepys wrote his diary in a shorthand based on Shelton's system in the 1660s, which is the best-known later use of the method.",
    ),

    entry(
        databaseId = 42,
        kind = Kind.LANGUAGE,
        section = "Slavonic",
        title = "Berynda's Slavonic-Russian lexicon of 1627",
        originalTitle = "Leksikon slavenorosskii imen tolkovanie",
        author = "Pamva Berynda",
        year = 1627,
        summary = "Berynda's lexicon, printed at Kiev in 1627, explains the hard words of Church Slavonic in the vernacular and is among the first dictionaries of its kind.",
        details = "The lexicon was printed at the Kiev Pechersk press in 1627. Writers name it in different ways, some calling it the first Slavonic-Russian dictionary and others placing it among the first, so its rank is left open here.",
        rare = true,
    ),

)
