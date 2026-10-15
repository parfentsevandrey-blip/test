package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1580 to 1590: Stevin's decimal fractions, Vigenere's tableau, Bright's word signs, a Latin dictionary, the Ostroh Bible and English spelling. */
val db46Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Stevin's De Thiende, decimal fractions",
        originalTitle = "De Thiende",
        author = "Simon Stevin",
        year = 1585,
        summary = "Stevin's pamphlet of 1585 showed how to write and reckon fractions in tenths, hundredths and so on, which made decimal fractions practical for ordinary calculation.",
        details = "De Thiende was printed at Leiden in 1585 in Dutch, and a French version, La Thiende, appeared the same year. Stevin marked each digit with a circled number for its place, and the later choice of a point or a comma as the decimal mark was settled only gradually.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "Ciphers",
        title = "Vigenere's Traicte des chiffres, the tableau cipher",
        originalTitle = "Traicte des chiffres, ou secretes manieres d'escrire",
        author = "Blaise de Vigenere",
        year = 1586,
        summary = "Vigenere's treatise of 1586 described a square table of shifted alphabets, in which a keyword chooses the shift for each letter of a message, and an autokey form that builds the key from the message itself.",
        details = "The treatise was printed in Paris in 1586, and the cipher bears his name, although accounts differ on how much of the method was his. Giovan Battista Bellaso had described a repeated-keyword method in 1553, so the tableau is best read as a development of earlier ciphers.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Bright's Characterie, a sign for each word",
        originalTitle = "Characterie: An Arte of Short, Swift, and Secret Writing by Character",
        author = "Timothy Bright",
        year = 1588,
        summary = "Bright's book of 1588 gave each common word its own sign, built from strokes, circles and semicircles, so that a writer copied whole words rather than letters.",
        details = "Characterie was printed in London in 1588 and is among the earliest English shorthand systems in print. Its word signs came before John Willis's alphabetic shorthand of 1602.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Thomas Thomas's Latin and English dictionary",
        originalTitle = "Dictionarium Linguae Latinae et Anglicanae",
        author = "Thomas Thomas",
        year = 1587,
        summary = "Thomas's dictionary of 1587 gave the English meanings of Latin words in alphabetical order for students of the language.",
        details = "The Dictionarium was printed in 1587 for students of Latin, and its entries give English equivalents for Latin headwords. It is one of the Latin word books of the later sixteenth century that were made for use in schools.",
    ),

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "Slavonic",
        title = "The Ostroh Bible in Church Slavonic",
        originalTitle = "Ostrozhskaya Bibliya",
        author = "Ivan Fedorov and Pyotr Mstislavets",
        year = 1581,
        summary = "The Ostroh Bible of 1581 was printed in Cyrillic type in Church Slavonic and is counted as the first complete Bible printed in the East Slavic lands.",
        details = "It was printed at Ostroh in Volhynia in 1580 and 1581 under the patronage of Prince Konstanty Ostrogski, with Ivan Fedorov as the leading printer. The type was set by hand, and the book is a landmark of early printing in Cyrillic.",
        rare = true,
    ),

    entry(
        databaseId = 46,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "Mulcaster's Elementarie, rules for English spelling",
        originalTitle = "The First Part of the Elementarie",
        author = "Richard Mulcaster",
        year = 1582,
        summary = "Mulcaster's Elementarie of 1582 set out how English words should be written and taught, with rules for the letters that English spelling needs.",
        details = "The book was printed in London in 1582 for teachers of reading and writing. Mulcaster argued that English words should be spelt by regular rules, and he gave the rules for the letters in a school setting.",
    ),

)
