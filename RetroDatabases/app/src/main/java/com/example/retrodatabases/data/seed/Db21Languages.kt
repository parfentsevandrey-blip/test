package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1830 to 1840: shorthand systems, telegraph codes, a symbolic algebra and a grammar of hieroglyphs, each described from its first public form. */
val db21Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Pitman's phonography, the sound-based shorthand",
        originalTitle = "Stenographic Sound-Hand (1837)",
        author = "Isaac Pitman",
        year = 1837,
        summary = "A shorthand that writes words by their spoken sounds, with each sound given a simple stroke or dot, so that the writing follows speech and not spelling.",
        details = "Pitman's first book on the system appeared in 1837 under the title Stenographic Sound-Hand. The name phonography came into use later for the same system. The record uses the 1837 publication date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Gabelsberger's German shorthand",
        originalTitle = "Anleitung zur deutschen Redezeichenkunst oder Stenographie (1834)",
        author = "Franz Xaver Gabelsberger",
        year = 1834,
        summary = "A German shorthand that writes speech with curved strokes and signs, built for the German language and not adapted from an English system.",
        details = "Gabelsberger's manual was printed in Munich in 1834, and later editions revised the signs. The system was widely used in German-speaking countries in the later nineteenth century. The record covers the 1834 publication.",
        rare = true,
    ),

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Telegraph codes",
        title = "Vail's dot-and-dash alphabet at Speedwell",
        originalTitle = "Telegraph alphabet of the Speedwell test (1838)",
        author = "Alfred Vail and Samuel Morse",
        year = 1838,
        summary = "A dot and dash alphabet for letters, credited to Alfred Vail, that was used in the first working telegraph test at the Speedwell Iron Works in New Jersey.",
        details = "Accounts differ on the date of the first show at Speedwell, with 6 January 1838 given in one account and 11 January in others. Credit for the alphabet is contested: Vail's 1845 book credited Morse with the code, while Morse's backers say he worked with a numbered codebook first.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Symbolic algebra",
        title = "Peacock's symbolical algebra",
        originalTitle = "A Treatise on Algebra (1830)",
        author = "George Peacock",
        year = 1830,
        summary = "An algebra of symbols in which the laws of ordinary arithmetic are kept as formal rules, so that negative and imaginary quantities follow the same rules as positive numbers.",
        details = "Peacock's treatise of 1830 set out the principle of the permanence of equivalent forms: a rule that holds for positive integers keeps its form when the symbols are read in a wider sense. The idea was a step toward algebra as a calculus of symbols, and the record uses the 1830 treatise date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Egyptian writing",
        title = "Champollion's Egyptian grammar",
        originalTitle = "Grammaire egyptienne, ou principes generaux de l'ecriture sacree egyptienne (1836)",
        author = "Jean-Francois Champollion",
        year = 1836,
        summary = "A grammar of the hieroglyphic script that sets out the signs for sounds, words and classifying signs, put together from the decipherer's notes after his death.",
        details = "Champollion died in 1832, and his grammar was printed in 1836 from his manuscripts. It set out the grammar behind the decipherment he had announced in 1822. The record uses the 1836 publication date.",
    ),

    entry(
        databaseId = 21,
        kind = Kind.LANGUAGE,
        section = "Telegraph codes",
        title = "Morse's numeral code for words",
        originalTitle = "Morse's numbered word code (1837)",
        author = "Samuel Finley Breese Morse",
        year = 1837,
        summary = "An early telegraph code that sent numbers, each standing for a word or phrase in a codebook, instead of sending the letters of the message one by one.",
        details = "Morse's backers say he first used a numbered codebook, and accounts of its date and use differ. The glossary Morse used no longer survives, so the encoding of the first message is unclear. The record uses 1837 for the numbered scheme, which was later replaced by the letter alphabet.",
        rare = true,
    ),
)
