package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1700 to 1710: two books by Newton, a navigation chart, a set of logarithm tables, a Russian arithmetic and a catalogue of comets. */
val db34Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Newton's Opticks",
        originalTitle = "Opticks",
        author = "Isaac Newton",
        year = 1704,
        summary = "A book on light and colour that set out Newton's experiments with prisms, the colours of thin films and the rings made by a lens on glass, and ended with queries for further research.",
        details = "Newton published Opticks in London in 1704, in English, and a Latin edition followed in 1706. The queries at the end suggested many experiments that later workers took up, and the book fixed the view that white light is a mixture of colours.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Newton's Arithmetica universalis",
        originalTitle = "Arithmetica universalis, sive de compositione et resolutione arithmetica liber",
        author = "Isaac Newton",
        year = 1707,
        summary = "A textbook of algebra built from Newton's lectures, with rules for equations and for the relations between the roots of an equation and its coefficients.",
        details = "The book appeared at Cambridge in 1707, edited by William Whiston from Newton's lecture notes. It collected the rules for relating roots to coefficients, and it went through several editions in the decades after its first appearance.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Halley's chart of the variation of the compass",
        originalTitle = "Chart of the variation of the compass",
        author = "Edmond Halley",
        year = 1701,
        summary = "A chart that joined places with the same compass variation across the Atlantic, one of the earliest printed charts of its kind.",
        details = "The sheet was delivered to the Royal Society in June 1701, and its title refers to observations made in 1700 under royal command. Accounts differ on the claim to be the first printed chart of its kind, and the lines it drew were called Halleyan lines before the term isogonic came into use about a century later.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Tables",
        title = "Sherwin's Mathematical Tables",
        originalTitle = "Mathematical Tables",
        author = "Henry Sherwin, editor",
        year = 1705,
        summary = "A book of logarithms for numbers up to 101,000, with proportional parts and trigonometric tables, edited under the names of four English mathematicians.",
        details = "The title page bears 1705, and a 1706 reissue is recorded. The page credits Briggs, Wallis, Halley and Sharp, and later editions were revised by William Gardiner, whose third edition is dated 1741 in one source and 1742 in another, and by Samuel Clark, whose fifth edition is dated 1771.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Magnitsky's Arithmetic",
        originalTitle = "Arifmetika, sie est nauka chislitelnaya",
        author = "Leonty Magnitsky",
        year = 1703,
        summary = "A Russian textbook of arithmetic and practical mathematics, written for the Moscow school and full of worked problems for navigators and surveyors.",
        details = "Magnitsky finished the book in 1703, and it was printed in Moscow with a long introduction and many practical problems. It was a major early mathematics textbook in Russian and remained in use in schools for decades.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.TOOL,
        section = "Comets",
        title = "Halley's Synopsis of the Astronomy of Comets",
        originalTitle = "Synopsis astronomiae cometicae",
        author = "Edmond Halley",
        year = 1705,
        summary = "A catalogue of the orbits of comets seen between 1337 and 1698, each computed as a parabolic path.",
        details = "Halley published the catalogue in London in 1705. It listed the orbital elements of 24 comets, and it noted that three of them seemed to share one orbit, a remark that later became famous.",
    ),

)
