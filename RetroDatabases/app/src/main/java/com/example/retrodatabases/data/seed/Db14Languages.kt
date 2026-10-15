package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1900 to 1910: formal notations and auxiliary languages, each described from its original publication. */
val db14Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Peano's Formulario Mathematico",
        originalTitle = "Formulario mathematico, fifth edition (1908)",
        author = "Giuseppe Peano and collaborators",
        year = 1908,
        summary = "A mathematical notation in symbols for logic and sets, in which Peano's group stated definitions and theorems in one format.",
        details = "The Formulario appeared in several editions from 1895, each a larger collection of definitions and theorems in Peano's own symbols. This record is the fifth edition of 1908. Russell adopted much of the notation in his early work.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Latino sine flexione, Peano's simplified Latin",
        originalTitle = "De Latino sine flexione, lingua auxiliare internationale (1903)",
        author = "Giuseppe Peano",
        year = 1903,
        summary = "A simplified Latin with no inflections, so each word keeps one form, proposed as an international auxiliary language.",
        details = "Peano first published the language in 1903. Work on it ran from 1887 to 1914 under the Academia pro Interlingua, which Peano chaired. The record covers the 1903 publication.",
        rare = true,
    ),

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Ido, a reform of Esperanto",
        originalTitle = "Ido, a reform of Esperanto (1907)",
        author = "Louis Couturat and Louis de Beaufront",
        year = 1907,
        summary = "A constructed language split off from Esperanto in 1907 after a dispute over its grammar and word forms.",
        details = "A group of Esperanto reformers led by Couturat and de Beaufront proposed Ido in 1907, with changes to the grammar and to the rules for building words.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Peirce's existential graphs",
        originalTitle = "Recent Developments of Existential Graphs (1906)",
        author = "Charles Sanders Peirce",
        year = 1906,
        summary = "A diagram language for logic, in which statements are drawn on a sheet and enclosed in cuts to show negation.",
        details = "Peirce developed the graphs from the 1890s and set out their later form in 1906. A proof in the system is a sequence of changes to a diagram, each allowed by a fixed rule. One source dates the mature framework to 1896.",
        rare = true,
    ),

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Tensor notation",
        title = "Ricci calculus, the index notation for tensors",
        originalTitle = "Methodes de calcul differentiel absolu et leurs applications (1900)",
        author = "Gregorio Ricci-Curbastro and Tullio Levi-Civita",
        year = 1900,
        summary = "A notation with indexed symbols for tensors, so a law can be written once and kept in the same form under any change of coordinates.",
        details = "The memoir sets out the absolute differential calculus, and its indexed symbols later became the working language of general relativity. Bibliographies date the memoir to 1900 or to 1901, when it appeared in Mathematische Annalen volume 54; this record uses 1900.",
        rare = true,
    ),

    entry(
        databaseId = 14,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Frege's two-dimensional notation",
        originalTitle = "Grundgesetze der Arithmetik, volume 2 (1903)",
        author = "Gottlob Frege",
        year = 1903,
        summary = "A notation in which the structure of a formula is drawn in two dimensions, with strokes for judgment and for content.",
        details = "Frege wrote his logic in the notation of his 1879 Begriffsschrift, and the second volume of Grundgesetze, published in 1903, used it for arithmetic. Russell's letter of 1902 showed that Basic Law V of this system leads to a contradiction, and Frege added an appendix on the problem in 1903.",
    ),
)
