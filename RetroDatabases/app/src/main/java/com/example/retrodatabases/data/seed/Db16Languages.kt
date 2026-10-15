package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1880 to 1890: logic, vector and set notations, a phonetic alphabet and an auxiliary language, each from its first public form. */
val db16Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Peirce's algebra of logic and the quantifier sign",
        originalTitle = "On the Algebra of Logic: A Contribution to the Philosophy of Notation",
        author = "Charles Sanders Peirce",
        year = 1885,
        summary = "Peirce wrote an algebra of logic with the sum sign for some and the product sign for all, the first use of those symbols as quantifiers.",
        details = "The 1885 paper extended Boole's algebra to relations and introduced the sum and product signs as quantifiers. An earlier paper of 1880, also titled On the Algebra of Logic, had set out the Boolean part of the system. This record uses 1885, the year of the quantifier notation.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Arithmetic notation",
        title = "Peano's symbols for the natural numbers",
        originalTitle = "Arithmetices principia, nova methodo exposita",
        author = "Giuseppe Peano",
        year = 1889,
        summary = "A short book in which the natural numbers are built from a zero and a successor sign, with induction written as a formula.",
        details = "Peano published the book in Turin in 1889, in Latin and with his own symbols. Its axioms became the usual starting point for the foundations of arithmetic. The notation of this book grew into the Formulario mathematico, which is in the database for 1900 to 1910.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Set notation",
        title = "Cantor's transfinite ordinals and the symbol omega",
        originalTitle = "Grundlagen einer allgemeinen Mannigfaltigkeitslehre",
        author = "Georg Cantor",
        year = 1883,
        summary = "Cantor extended the counting numbers past every finite number with transfinite ordinals and gave the first infinite one the symbol omega.",
        details = "Cantor's 1883 Grundlagen introduced ordinal numbers as a way of continuing a well-ordered sequence beyond all finite numbers. The first infinite ordinal received the symbol omega, which later became standard in set theory. The record covers that notation of 1883 and not the later theory built on it.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Vector notation",
        title = "Gibbs's vector notation for physics students",
        originalTitle = "Elements of Vector Analysis",
        author = "Josiah Willard Gibbs",
        year = 1881,
        summary = "Gibbs wrote vectors with dot and cross products for physics students, in notes first printed privately in 1881.",
        details = "Gibbs printed the first part of his vector notes for his Yale students in 1881, and a fuller edition followed in 1884. Heaviside worked on a rival vector notation in the 1890s, and the Gibbs form won in the English-speaking world. Edwin Wilson's 1901 textbook was based on Gibbs's notes.",
        rare = true,
    ),

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Esperanto, Zamenhof's first textbook",
        originalTitle = "Unua Libro",
        author = "L. L. Zamenhof, writing as Doktoro Esperanto",
        year = 1887,
        summary = "A constructed language with a grammar of sixteen rules and no irregular forms, set out in its first textbook.",
        details = "Zamenhof published the textbook in Warsaw in 1887 under the pen name Doktoro Esperanto, and the name later became the name of the language. The grammar fits in sixteen rules, and word endings mark the part of speech, so the vocabulary is built from roots. The record gives the year of first publication.",
    ),

    entry(
        databaseId = 16,
        kind = Kind.LANGUAGE,
        section = "Phonetic notation",
        title = "The first International Phonetic Alphabet",
        originalTitle = "Dhi Fonetik Alfabet",
        author = "Paul Passy and the Phonetic Teachers' Association",
        year = 1888,
        summary = "A set of letters, one for each speech sound, so that the same word can be written the same way in any language.",
        details = "The Phonetic Teachers' Association was founded in Paris in 1886 by Paul Passy and others, and it printed its first alphabet in 1888 under the name Dhi Fonetik Alfabet. The idea was one letter for one sound, so a learner could read any language from the same table. The association later became the International Phonetic Association, and the alphabet took the name IPA.",
        rare = true,
    ),
)
