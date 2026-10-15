package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1630 to 1640: the cross for multiplication, the inequality signs, a sentence primer, a Coptic grammar, Italian dialogues and an English grammar. */
val db41Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "Symbols",
        title = "Oughtred's Clavis Mathematicae and the multiplication cross",
        originalTitle = "Clavis Mathematicae",
        author = "William Oughtred",
        year = 1631,
        summary = "Oughtred's Clavis Mathematicae of 1631 gave the cross as the sign of multiplication, and the notation spread through English and continental texts in the decades that followed.",
        details = "The Clavis was printed in London in 1631 as a short introduction to algebra and its notation. Later writers chose the dot or simple juxtaposition for multiplication, so the cross was one notation among several rather than the only one.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "Symbols",
        title = "Harriot's Artis analyticae praxis and the inequality signs",
        originalTitle = "Artis analyticae praxis ad aequationes algebraicas resolvendas",
        author = "Thomas Harriot",
        year = 1631,
        summary = "Harriot's algebra, printed in 1631 after his death in 1621, gave the signs for less than and greater than that are still in use.",
        details = "Harriot had used the signs in his manuscripts before the book appeared, and Walter Warner, a friend of his, saw the book through the press. The same book also set out how the roots of an equation relate to its coefficients.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "Language teaching",
        title = "Comenius's Janua linguarum, a gate of languages unlocked",
        originalTitle = "Janua linguarum reserata",
        author = "Johann Amos Comenius",
        year = 1631,
        summary = "Comenius's Latin primer built its lessons from short sentences that together cover the common words of a language, so a learner meets the whole vocabulary in order.",
        details = "The Janua was written at Leszno in Poland and printed in 1631, and it was soon translated into several other languages. Comenius meant it as a gate to all the others, so each sentence brings in words from one more field of life or learning.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "Egyptian",
        title = "Kircher's Coptic grammar, the Prodromus Coptus",
        originalTitle = "Prodromus Coptus sive Aegyptiacus",
        author = "Athanasius Kircher",
        year = 1636,
        summary = "Kircher's Coptic grammar of 1636 treated Coptic as the last form of the Egyptian language, and he hoped it would unlock the sense of the hieroglyphs.",
        details = "Kircher published the Prodromus in Rome in 1636 as an introduction to Coptic, the language of Christian Egypt. He believed Coptic kept the sounds of the hieroglyphs, a claim that was set aside once Champollion read the hieroglyphs in the 1820s.",
        rare = true,
    ),

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "Vernacular",
        title = "Galileo's Italian dialogues against the Latin of the schools",
        originalTitle = "Dialogo sopra i due massimi sistemi del mondo",
        author = "Galileo Galilei",
        year = 1632,
        summary = "Galileo wrote his 1632 dialogue on the two chief world systems in Italian rather than Latin, so that readers beyond the universities could follow the argument.",
        details = "The Dialogo was licensed by the Florentine censors and printed in Florence in 1632, and the Roman censors ordered it withdrawn that same year. Its three speakers talk in ordinary speech, and the choice of Italian was part of a claim that natural philosophy belonged to any educated reader.",
    ),

    entry(
        databaseId = 41,
        kind = Kind.LANGUAGE,
        section = "English",
        title = "Jonson's English Grammar, printed in the 1640 folio",
        originalTitle = "The English Grammar",
        author = "Ben Jonson",
        year = 1640,
        summary = "Ben Jonson's short grammar of English was printed in the folio of his works in 1640, an early attempt to describe the English language in its own terms.",
        details = "The grammar appeared three years after Jonson's death in 1637, in the folio collection of his Works. It borrows much of its framework from Latin grammar, and its rules are short.",
        rare = true,
    ),

)
