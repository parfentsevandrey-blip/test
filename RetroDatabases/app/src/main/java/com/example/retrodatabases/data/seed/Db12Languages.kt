package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1920 to 1930: formal systems, a planned language and a controlled English, all of them readings rather than programs. */
val db12Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Lesniewski's Ontology",
        originalTitle = "Ontologia, a calculus of names (Lesniewski, 1920)",
        author = "Stanislaw Lesniewski",
        year = 1920,
        summary = "A calculus in which the only basic relation holds between names, so every sentence says that one name falls under another.",
        details = "Ontology dates to 1920, when Lesniewski built it as the base for his mereology, a theory of parts and wholes. It was the first of his three systems, which he devised in the reverse order of their logical dependence. The calculus treats names rather than sets as its objects.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "Constructed languages",
        title = "Occidental, a planned European language",
        originalTitle = "Occidental, later Interlingue (de Wahl, 1922)",
        author = "Edgar de Wahl",
        year = 1922,
        summary = "A planned language built from European roots, published in 1922 by a teacher in Tallinn who had worked on it since 1906.",
        details = "Edgar de Wahl developed the language first in a private project called Auli, which ran from 1906 to 1921, and then made it public under the name Occidental in 1922. The first issue of the magazine that announced it is dated 1921 on one page and 1922 on others. The name changed to Interlingue in 1949, a switch meant to signal neutrality in the Cold War.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "Decidable theories",
        title = "Presburger arithmetic",
        originalTitle = "Presburger's paper for the Warsaw congress (1929)",
        author = "Mojzesz Presburger",
        year = 1929,
        summary = "A first-order theory of the natural numbers with addition and equality and no multiplication, which can be decided by a mechanical method.",
        details = "Presburger presented the theory at the First Congress of Mathematicians of the Slavic Countries, held in Warsaw from 23 to 27 September 1929. Britannica dates his results to 1930, so the year is a choice. Any algorithm that decides the theory needs at least doubly exponential time in the worst case, a lower bound that later work established.",
        rare = true,
    ),

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "Intuitionism",
        title = "Heyting's formal intuitionistic logic",
        originalTitle = "Die formalen Regeln der intuitionistischen Logik (Heyting, 1930)",
        author = "Arend Heyting",
        year = 1930,
        summary = "The first formal rules for Brouwer's intuitionistic logic, in which a proof must exhibit its object rather than only show that it cannot be absent.",
        details = "The rules appeared in three parts in the Sitzungsberichte of the Prussian Academy, on pages 42 to 56, 57 to 71 and 158 to 169. Bibliographic records disagree on the titles of the parts. Heyting's mentor Brouwer resisted formalising intuitionism and called the result a sterile exercise, although the formal rules became the standard reference.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "First-order logic",
        title = "Hilbert and Ackermann's Grundzuge",
        originalTitle = "Grundzuge der theoretischen Logik (Hilbert and Ackermann, 1928)",
        author = "David Hilbert and Wilhelm Ackermann",
        year = 1928,
        summary = "The textbook that presents first-order logic as a formal language and states the decision problem: whether one method can settle every formula.",
        details = "The book was published by Springer in 1928. It grew out of Hilbert's Gottingen lectures of 1917 to 1922, which Bernays helped to prepare, and Ackermann turned them into the monograph. Kurt Goedel's doctoral dissertation settled the completeness question a year later, and Church and Turing showed in 1936 that no general decision method exists for first-order logic.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.LANGUAGE,
        section = "Controlled languages",
        title = "Basic English",
        originalTitle = "Basic English: A General Introduction with Rules and Grammar (Ogden, 1930)",
        author = "C. K. Ogden",
        year = 1930,
        summary = "A controlled form of English with 850 words, meant as an international second language and as an aid for learners of English.",
        details = "Nouns supply most of the 850 entries, with 150 adjectives and 100 operative words such as can and do covering the rest. The scheme reduces the thousands of English verbs to eighteen verbs that combine with other words. Britannica dates its development to 1926 to 1930, while a Simple English Wikipedia infobox gives 1925, so the sources disagree.",
        rare = true,
    ),
)
