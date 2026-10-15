package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1610 to 1620: three dictionaries of 1611 and 1612, the word telescope, an Arabic grammar, and the decimal point in print. */
val db43Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Cotgrave's French and English dictionary",
        originalTitle = "A Dictionarie of the French and English Tongues",
        author = "Randle Cotgrave",
        year = 1611,
        summary = "Cotgrave's dictionary gave English readers the French words for English terms, and French readers the English words for French terms, in one alphabetical book.",
        details = "The dictionary was printed in London in 1611 and English readers and translators used it for much of the century. Its entries give several English equivalents for a French word, which is why it is still cited as a guide to the English of its period.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Covarrubias's treasury of the Castilian tongue",
        originalTitle = "Tesoro de la lengua castellana o espanola",
        author = "Sebastian de Covarrubias Orozco",
        year = 1611,
        summary = "Covarrubias's Spanish treasury gathered the words of Castilian with their origins and senses, and it is counted among the first large dictionaries of a modern European vernacular.",
        details = "The book was printed in Madrid in 1611, and it lists words in alphabetical order with notes on their history and their senses. Covarrubias wrote it as a store of the language, and later Spanish dictionaries drew on its entries for their own definitions.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The Crusca dictionary of 1612",
        originalTitle = "Vocabolario degli Accademici della Crusca",
        author = "Accademia della Crusca",
        year = 1612,
        summary = "The first edition of the Crusca dictionary fixed a standard Italian vocabulary by listing words with examples taken from earlier writers.",
        details = "The academy printed the first edition in Venice in 1612, and each entry quoted a passage from an older Tuscan author to show the word in use. The later editions followed the same method, and the dictionary became a model for later dictionaries of other languages.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "The word telescope, named at a Roman banquet",
        originalTitle = "telescopio",
        author = "Giovanni Demisiani",
        year = 1611,
        summary = "The word telescope was proposed in 1611 for Galileo's far-seeing glass, which until then had been called by the Latin word perspicillum.",
        details = "Demisiani, a Greek mathematician, gave the name at a banquet of the Accademia dei Lincei in Rome in 1611, and the Greek parts of the word mean far and to look. The exact day of the banquet is given differently in the sources that survive, so only the year is given here.",
    ),

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Erpenius's Arabic grammar",
        originalTitle = "Grammatica Arabica",
        author = "Thomas Erpenius",
        year = 1613,
        summary = "Erpenius's Arabic grammar gave European students a printed set of rules for the language of the Qur'an, based on earlier grammarians and set out for teaching.",
        details = "The grammar was printed at Leiden in 1613 and was used there for teaching the language. Erpenius was a professor of oriental languages at Leiden, and the book was used there for the teaching of Arabic.",
        rare = true,
    ),

    entry(
        databaseId = 43,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "The decimal point in Pitiscus's trigonometry",
        originalTitle = "Trigonometria",
        author = "Bartholomaeus Pitiscus",
        year = 1612,
        summary = "Pitiscus's trigonometry used a point to separate the whole part of a decimal from its fraction, a notation that later writers took up for the printed tables.",
        details = "Some accounts credit Pitiscus's trigonometric tables of 1608, and their 1612 edition, with the first printed decimal point. Clavius had used the point in a table of 1593, and an Italian manuscript of the 1440s has been reported with the point, so accounts differ on who first used it in print.",
    ),

)
