package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1680 to 1690: the Leipzig journal, Ray's plant history, Borelli's animal motion, Wallis's algebra, Petty's arithmetic and Hevelius's star catalogue. */
val db36Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Journals",
        title = "Acta Eruditorum, the learned journal of Leipzig",
        originalTitle = "Acta Eruditorum",
        author = "Otto Mencke",
        year = 1682,
        summary = "Acta Eruditorum was one of the first learned journals in Germany, printed in Latin at Leipzig, with reviews and new papers in mathematics and science.",
        details = "Otto Mencke founded the journal, and its first issue appeared at Leipzig in January 1682. Leibniz published his paper on maxima and minima there in October 1684, and his living-force argument against Descartes in 1686, which made the journal a channel for new mathematics.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Botany",
        title = "John Ray's Historia plantarum",
        originalTitle = "Historia plantarum generalis",
        author = "John Ray",
        year = 1686,
        summary = "Ray's Historia plantarum set out to describe every known plant, grouping them by their parts, and its first volume appeared in London in 1686.",
        details = "Later volumes followed, and the series was not complete until 1704. Ray treated a species as a kind of plant that keeps its form when raised from seed, a test that his classification depended on.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Physiology",
        title = "Borelli's De motu animalium on animal motion",
        originalTitle = "De motu animalium",
        author = "Giovanni Alfonso Borelli",
        year = 1680,
        summary = "Borelli's treatise explained the movements of animals as the work of levers and muscles, and it computed the forces the body must exert.",
        details = "Borelli died in 1679, and the first part of the book appeared in Rome in 1680, with the second part in 1681. His account of the body as a set of levers was among the first full mechanical treatments of animal movement.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "A Treatise of Algebra, both Historical and Practical",
        originalTitle = "A Treatise of Algebra, both Historical and Practical",
        author = "John Wallis",
        year = 1685,
        summary = "Wallis's treatise gave a history of algebra and proposed a geometric picture for imaginary roots, a step toward the plane of complex numbers.",
        details = "The treatise was printed in London in 1685 and written in English for a wide readership, not in Latin. In its chapter on imaginary quantities Wallis argued that the root of a negative product could be pictured as a mean proportional between lengths.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Statistics",
        title = "Political Arithmetick",
        originalTitle = "Political Arithmetick",
        author = "William Petty",
        year = 1690,
        summary = "Petty's Political Arithmetick used counts of people, houses and trade to compare the wealth of nations, and it argued that counts could replace guesswork.",
        details = "Petty wrote the book in the 1670s, and it was printed in London in 1690, three years after his death. Later writers followed its habit of turning national questions into counts.",
    ),

    entry(
        databaseId = 36,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Hevelius's Prodromus Astronomiae, a star catalogue",
        originalTitle = "Prodromus astronomiae",
        author = "Johannes Hevelius",
        year = 1690,
        summary = "Hevelius's catalogue lists 1564 stars observed with large sighting instruments rather than telescopes, and it was printed after his death.",
        details = "Hevelius of Danzig died in 1687, and his widow Elisabeth printed the catalogue in 1690, the same year as his star atlas. The positions were measured with large sighting instruments of his own design, the older method he kept after the telescope had come into use.",
        rare = true,
    ),

)
