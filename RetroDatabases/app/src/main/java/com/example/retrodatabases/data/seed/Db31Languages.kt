package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1730 to 1740: Euler's function notation, Linnaeus's ranks and names, a semimetal, a catalogue and a music manual. */
val db31Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Notation",
        title = "Euler's function notation f(x)",
        originalTitle = "Commentarii academiae scientiarum Petropolitanae",
        author = "Leonhard Euler",
        year = 1734,
        summary = "Euler wrote a function of x as f(x), a short notation for any rule that links a quantity to x, and it is still in use.",
        details = "Euler used f(x) for a function in a paper printed in the St Petersburg Commentarii. The volume that holds the paper is dated 1735 and was printed in 1740, so the year of first print is disputed, and the record uses 1734, the year of the first use.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Botany",
        title = "Linnaeus's rules for botanical terms",
        originalTitle = "Fundamenta botanica",
        author = "Carl Linnaeus",
        year = 1736,
        summary = "A set of numbered rules for the words used to describe plants and for the way plants are named.",
        details = "Linnaeus printed the Fundamenta botanica in Amsterdam in 1736, during his years in the Netherlands. It gathers his numbered aphorisms on botanical terms and on naming, and it prepared the ground for his Philosophia botanica of 1751.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Classification",
        title = "Linnaeus's ranks in Systema naturae",
        originalTitle = "Systema naturae",
        author = "Carl Linnaeus",
        year = 1735,
        summary = "A classification that sorts nature into kingdoms, classes, orders, genera and species, each rank with its own name.",
        details = "The first edition of the Systema naturae was printed in Leiden in 1735 as a short work. Its ranks of class, order, genus and species became the frame for later classification.",
    ),

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Chemistry",
        title = "Brandt's cobalt as a semimetal of its own",
        originalTitle = "Dissertatio de semimetallis",
        author = "Georg Brandt",
        year = 1735,
        summary = "Brandt showed that the blue of glass comes from cobalt and not from bismuth, and named cobalt a semimetal of its own.",
        details = "The dissertation was printed in the Acta Literaria et Scientiarum Sveciae in 1735. Histories give other dates for the announcement, one near 1739, so the year of the claim is disputed, and the record uses the printed dissertation. Brandt later listed cobalt among the semimetals and then treated it as a distinct element.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Natural history",
        title = "Seba's numbered thesaurus of natural objects",
        originalTitle = "Locupletissimi rerum naturalium thesauri",
        author = "Albertus Seba",
        year = 1734,
        summary = "An illustrated catalogue in which each animal, shell or other specimen is drawn, numbered and named, so that collectors could refer to one plate.",
        details = "Seba, an Amsterdam apothecary, began publishing his thesaurus of natural specimens in 1734, and it was completed in several volumes by the 1760s. Its numbered plates gave collectors and zoologists a shared way to point at a specimen.",
        rare = true,
    ),

    entry(
        databaseId = 31,
        kind = Kind.LANGUAGE,
        section = "Music theory",
        title = "Mattheson's manual for conductors",
        originalTitle = "Der vollkommene Capellmeister",
        author = "Johann Mattheson",
        year = 1739,
        summary = "A large German manual for the director of a musical ensemble, covering intervals, melody, harmony and the work of leading a performance.",
        details = "Mattheson printed the manual in Hamburg in 1739, and it was written in German rather than in Latin. It sets out the theory of intervals and melody for the leader of a musical group, and it is a key German treatise of its period.",
        rare = true,
    ),
)
