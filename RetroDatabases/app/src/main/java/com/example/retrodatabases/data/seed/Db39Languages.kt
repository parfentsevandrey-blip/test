package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1650 to 1660: a sign for infinity, a division mark, an English grammar in Latin, a picture book of words, a dictionary of hard words and a polyglot Bible. */
val db39Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Wallis's sign for infinity",
        originalTitle = "De sectionibus conicis",
        author = "John Wallis",
        year = 1655,
        summary = "Wallis used a figure of eight lying on its side for an infinite quantity, and the sign has been used for infinity ever since.",
        details = "The treatise on conic sections is the usual place for the first printed use, dated 1655, but accounts differ and some put the first print use in his Arithmetica infinitorum of 1656, where the sign recurs. Wallis used it in his method of infinite series, where it stood for a quantity larger than any finite number.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Rahn's division sign in the Teutsche Algebra",
        originalTitle = "Teutsche Algebra",
        author = "Johann Heinrich Rahn",
        year = 1659,
        summary = "Rahn printed a short line with a dot above and a dot below as the mark for division in an algebra book.",
        details = "The German algebra was printed at Zurich in 1659 and used the mark in its text. Later algebra books in several languages took the mark up, and it is still the common sign for division in schoolbooks.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Wallis's Latin grammar of English",
        originalTitle = "Grammatica linguae anglicanae",
        author = "John Wallis",
        year = 1653,
        summary = "Wallis wrote a grammar of English in Latin for foreign readers, and it described the sounds of English as well as its word forms.",
        details = "The grammar was printed at Oxford in 1653, and its account of English sounds is an early careful description of the language's pronunciation. Writing in Latin let scholars abroad read the rules without knowing English first.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Picture books",
        title = "Comenius's Orbis pictus, the picture book of words",
        originalTitle = "Orbis sensualium pictus",
        author = "Johann Amos Comenius",
        year = 1658,
        summary = "Comenius printed a woodcut of each thing beside its words, so that a child learned the words of a language from the pictures.",
        details = "The book appeared at Nuremberg in 1658 as a school text, with short Latin and German wording under each picture. Later editions were translated into other languages, which shows how widely the picture method was used in schools.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Dictionaries",
        title = "Blount's Glossographia of hard words",
        originalTitle = "Glossographia",
        author = "Thomas Blount",
        year = 1656,
        summary = "Blount's dictionary explained the hard words of English, many of them borrowed from other languages, in alphabetical order.",
        details = "It was printed in London in 1656 and aimed at readers who met unfamiliar words in books. It is one of the early English dictionaries of hard words, and it gave the meanings that the word lists of the schools left out.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.LANGUAGE,
        section = "Polyglots",
        title = "Walton's London Polyglot Bible",
        originalTitle = "Biblia Sacra Polyglotta",
        author = "Brian Walton",
        year = 1657,
        summary = "Walton's polyglot Bible set the same biblical text beside its translations in several languages, so that readers could compare them on one opening.",
        details = "The volumes were printed in London in parts from 1653 and completed in 1657. It is a reference work for scholars, and its parallel columns made the languages of the text easy to compare.",
        rare = true,
    ),

)
