package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1740 to 1750: a numeral notation for music and a German grammar, with two more notations. */
val db30Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Music",
        title = "Rousseau's numerals for music",
        originalTitle = "Projet concernant de nouveaux signes pour la musique",
        author = "Jean-Jacques Rousseau",
        year = 1742,
        summary = "Notes written as the digits 1 to 7 for the scale degrees, with dots above and below for the octaves, so that a melody could be written without staves.",
        details = "Rousseau presented his plan to the Paris Academy of Sciences in 1742. The cipher notation used the scale degrees as numbers and kept the rhythm in separate marks, and he later used it in his music dictionary of 1768.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Gottsched's grammar of the German language",
        originalTitle = "Grundlegung einer deutschen Sprachkunst",
        author = "Johann Christoph Gottsched",
        year = 1748,
        summary = "A German grammar that set out the parts of speech and the forms of words in an ordered rule book, which schools used to teach the standard language.",
        details = "Gottsched's grammar appeared in Leipzig in 1748 and was reprinted in several editions. It fixed a standard German for writers in the north, and it shows how a rule book was used to standardise a language.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Larramendi's trilingual dictionary of Basque",
        originalTitle = "Diccionario trilingue del castellano, bascuence y latin",
        author = "Manuel de Larramendi",
        year = 1745,
        summary = "A dictionary that set Basque words beside their Spanish and Latin equivalents, so that readers could learn the language through the two others.",
        details = "Larramendi, a Jesuit, wrote in defence of Basque before compiling this dictionary, which was printed in 1745. Each entry gives a Basque word with its Spanish and Latin meaning.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The French Academy's dictionary, third edition",
        originalTitle = "Dictionnaire de l'Academie francoise, troisieme edition",
        author = "Academie francaise",
        year = 1740,
        summary = "A two-volume dictionary of French, issued by the Academy as the standard record of the words of the language and their meanings.",
        details = "The third edition was printed in Paris in two volumes, with its title page dated MDCCXL, which is 1740. The Academy had first printed the dictionary in 1694.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Spelling",
        title = "Dilworth's spelling book for English schools",
        originalTitle = "A New Guide to the English Tongue",
        author = "Thomas Dilworth",
        year = 1740,
        summary = "A school book of words, spelling and forms of prayer for children, which taught reading and spelling to English pupils.",
        details = "The London printing is dated 1740, and the book was reprinted in Boston and Philadelphia later in the century. The part of the book on prayer was printed for use in schools.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "The Trevoux dictionary of 1743",
        originalTitle = "Dictionnaire universel francois et latin",
        author = "Jesuit editors at Trevoux",
        year = 1743,
        summary = "A large French dictionary with a Latin-French part, printed in six folio volumes, which gave the words of both languages in one set.",
        details = "The 1743 printing ran to six folio volumes, and its last volume holds the Latin-French part. Accounts differ on the place of printing, which is given as Trevoux, near Lyon, on some copies and as Paris, with booksellers named, on others.",
    ),
)
