package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1720 to 1730: a shorthand, a theory of chords, a chemistry of phlogiston, a star catalogue, an Irish flora and a thunderstone paper. */
val db32Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Shorthand",
        title = "Byrom's universal English shorthand",
        originalTitle = "The Universal English Shorthand",
        author = "John Byrom",
        year = 1720,
        summary = "A shorthand system for English that Byrom taught in London from about 1718, with a book that did not appear until 1767.",
        details = "Byrom returned to England in 1716 with his system, and accounts differ on when it reached its final form. The book was printed in 1767, four years after his death in 1763, so the printed text is later than the system itself. The year given here is the period of his teaching, not a printing date.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Music",
        title = "Rameau's theory of harmony",
        originalTitle = "Traite de l'harmonie reduite a ses principes naturels",
        author = "Jean-Philippe Rameau",
        year = 1722,
        summary = "A treatise that explains chords as stacks of thirds built on the notes of a scale, with one note named as the root of each chord.",
        details = "Rameau published the treatise in Paris in 1722. It gave the fundamental bass, the root note under each chord, and later writers used his root idea to teach harmony.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Chemistry",
        title = "Stahl's phlogiston chemistry",
        originalTitle = "Fundamenta chymiae dogmatico-experimentalis",
        author = "Georg Ernst Stahl",
        year = 1723,
        summary = "A chemistry textbook of 1723 in which burning and the making of metal ashes are explained as the release of a principle called phlogiston.",
        details = "Stahl's book taught chemistry from the phlogiston idea, which earlier writers had used, and accounts differ on how far he changed it before he published. The theory was replaced in the 1780s by Lavoisier's chemistry of oxygen. The name phlogiston remained the standard term for the principle until then.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "Flamsteed's numbers for stars in the Historia coelestis",
        originalTitle = "Historia coelestis Britannica",
        author = "John Flamsteed",
        year = 1725,
        summary = "A star catalogue whose stars are numbered within each constellation, so that a number and a constellation name a star.",
        details = "Flamsteed had died in 1719, and the three-volume edition of 1725 was the posthumous catalogue he had prepared. An edition of 1712, made without his consent, had been withdrawn, which is why the 1725 volumes are the authorised text. The numbers he gave his stars are still used in catalogues.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Botany",
        title = "Threlkeld's Irish flora",
        originalTitle = "Synopsis Stirpium Hibernicarum",
        author = "Caleb Threlkeld",
        year = 1726,
        summary = "A list of the plants found in Ireland, each named and described in Latin, and described as the earliest flora of Ireland.",
        details = "Threlkeld published the Synopsis at Dublin in October 1726. It gives each plant a name in Latin, so it is a catalogue of names as much as a list of plants.",
        rare = true,
    ),

    entry(
        databaseId = 32,
        kind = Kind.LANGUAGE,
        section = "Antiquities",
        title = "De Jussieu's thunderstones",
        originalTitle = "De l'Origine et des usages de la Pierre de Foudre",
        author = "Antoine de Jussieu",
        year = 1723,
        summary = "A paper that puts fossils, stone tools and meteorites under one name, thunderstone, and asks where each kind came from and what it was used for.",
        details = "The paper is dated here to 1723. Its subjects were the stone objects that people had believed to fall from the sky, and the paper treats them as a group under one name.",
        rare = true,
    ),

)
