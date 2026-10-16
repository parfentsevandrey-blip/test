package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1450 to 1460: Donatus's grammar in movable type, the Worin seokbo, Gothic textura, a Qur'an in three languages, Chinese characters in metal and Valla's Latin Thucydides. */
val db59Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Grammars",
        title = "Donatus's Ars minor printed with movable type",
        originalTitle = "Ars minor",
        author = "Aelius Donatus, the grammarian",
        year = 1450,
        summary = "Donatus's short Latin grammar for schoolboys was printed with movable type in the 1450s, and a few fragments of those printings survive.",
        details = "The surviving fragments are generally dated to about 1450 to 1455, and accounts differ on whether Gutenberg's workshop or another printer made them. The grammar was printed in many later editions, but the early fragments are few, so the first printed editions are known only from small pieces.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Korean",
        title = "The Worin seokbo, a Buddhist text in the new Korean alphabet",
        originalTitle = "Worin seokbo",
        author = "Compiled under King Sejo of Joseon",
        year = 1459,
        summary = "The Worin seokbo of 1459 joins a life of the Buddha to a hymn of praise that uses the moon's reflection in a thousand streams, both written in the Korean alphabet devised in the 1440s.",
        details = "The book combines two earlier texts of 1447 that were written in the new alphabet, the Seokbo sangjeol and the Worin cheongang jigok. The year 1459 is the date given for the compilation, and it places the book within the reign of Sejo.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Scripts",
        title = "Textura, the blackletter hand that Gutenberg cut in type",
        originalTitle = "Textura",
        author = "Gothic book scribes",
        year = 1455,
        summary = "Textura is the dense Gothic book hand whose letter shapes Gutenberg's types copied for the 42-line Bible of about 1455.",
        details = "Textura was the usual hand of large German and Latin books in the fifteenth century, with letters that are narrow, angular and closely set. The types of the 42-line Bible follow its forms closely, and the same hand was copied by other printers of the 1460s.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Qur'an translation",
        title = "Juan de Segovia's Qur'an in Arabic, Castilian and Latin",
        originalTitle = "Alcoran",
        author = "Juan de Segovia, Castilian theologian",
        year = 1456,
        summary = "Juan de Segovia's Qur'an with Arabic, Castilian and Latin texts was finished in the spring of 1456, and the book itself is now lost.",
        details = "Accounts date the work to 1455 and 1456, and a 2024 study gives spring 1456 for its completion. Only Segovia's prologue and a Latin version of a letter from Yca, his collaborator, are known to survive, so the three-language book itself is lost.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Chinese characters",
        title = "The Eulhae type of Chinese characters, cast under King Sejo in 1455",
        originalTitle = "Eulhaeja",
        author = "The court of King Sejo of Joseon",
        year = 1455,
        summary = "The Eulhae type of 1455 is a set of Chinese characters cast for the Korean court under King Sejo, and it takes its name from the sexagenary year 1455.",
        details = "The name Eulhae comes from the sexagenary year that 1455 bears, and the set was one of several Chinese-character types the Korean court cast in the 1400s. Accounts differ on how many pieces survive and on how they were made, so no count or material is given here.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.LANGUAGE,
        section = "Translations",
        title = "Valla's Latin Thucydides, made for Pope Nicholas V",
        originalTitle = "Historia tou Peloponnesiakou polemou",
        author = "Lorenzo Valla, for Pope Nicholas V",
        year = 1452,
        summary = "Lorenzo Valla's Latin version of Thucydides, made for Pope Nicholas V in the early 1450s, gave Latin readers the Greek history of the Peloponnesian War.",
        details = "Valla worked in Rome for Nicholas V, and the translation is generally dated to the early 1450s, with 1452 used here as a single year for it. Accounts differ on the year of completion, so 1452 is an approximation.",
    ),

)
