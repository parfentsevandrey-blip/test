package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1590 to 1600: Vieta's letters for unknowns, Bales's shorthand, two word books, a romanised epic, the Clementine Vulgate and Florio's phrasebook. */
val db45Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Algebra",
        title = "Vieta's letters for unknowns in the Isagoge",
        originalTitle = "In artem analyticem isagoge",
        author = "Francois Vieta",
        year = 1591,
        summary = "Vieta's Isagoge of 1591 named unknown quantities by vowels and known ones by consonants, so that an equation could be written as one general rule.",
        details = "The introduction to the analytic art was printed at Tours in 1591 and set out algebra with letters for magnitudes instead of particular numbers. Vieta's convention of vowels for unknowns and consonants for knowns was his own, and later algebraists replaced it with other signs, so it stands as an early step toward symbolic algebra.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Bales's shorthand in The Writing Schoolemaster",
        originalTitle = "The Writing Schoolemaster",
        author = "Peter Bales",
        year = 1590,
        summary = "Bales's brachygraphy of 1590 opened his Writing Schoolemaster with a shorthand written in signs, so that speech could be taken down quickly.",
        details = "Accounts tie the brachygraphy to 1590, when it appeared as the first of three parts of the Writing Schoolemaster, the others covering spelling and penmanship. A separate Arte of Brachygraphy dated 1597 is recorded in a printing with additions, and the 1590 date is used here as the first public form.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Minsheu's Spanish and English dictionary",
        originalTitle = "A Dictionarie in Spanish and English",
        author = "John Minsheu",
        year = 1599,
        summary = "Minsheu's dictionary of 1599 gave Spanish words with their English meanings in an early word book printed in London.",
        details = "The dictionary was printed in London in 1599 for English readers who wanted Spanish. Minsheu later published the Ductor in linguas of 1617, a guide to several languages, and early Spanish and English word books are few.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Heike monogatari in romanised Japanese",
        originalTitle = "Heike monogatari",
        author = "Jesuit printers at Amakusa",
        year = 1592,
        summary = "The Heike monogatari printed at Amakusa in 1592 gave the Japanese war tale in Roman letters for Jesuit readers learning the language.",
        details = "The Jesuit press at Amakusa printed the text in the romanised spelling of the mission, which shows how Japanese sounds were written by Europeans of the period. Accounts give 1592 as the printing date, and the book is one of several Japanese works the mission printed in Roman letters.",
        rare = true,
    ),

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "The Clementine Vulgate of 1592",
        originalTitle = "Biblia sacra vulgatae editionis",
        author = "Pope Clement VIII",
        year = 1592,
        summary = "The Clementine Vulgate of 1592 gave the Church an official Latin text of the Bible, printed at Rome under Pope Clement VIII.",
        details = "The edition replaced the Sixtine Vulgate of 1590, which had been withdrawn after errors were found in it. The Clementine text remained the official Latin Bible of the Catholic Church until the late twentieth century, when the Nova Vulgata took its place.",
    ),

    entry(
        databaseId = 45,
        kind = Kind.LANGUAGE,
        section = "Italian",
        title = "Florio's Second Frutes of Italian phrases",
        originalTitle = "Second Frutes",
        author = "John Florio",
        year = 1591,
        summary = "Florio's Second Frutes of 1591 gave Italian dialogues, proverbs and phrases with English, as a sequel to his earlier phrasebook for English learners.",
        details = "The book was printed in London in 1591 and followed the method of Florio's First Fruites of 1578, which had taught Italian through dialogues. Florio later compiled a much larger Italian word book, but the record names only the 1591 phrasebook.",
    ),

)
