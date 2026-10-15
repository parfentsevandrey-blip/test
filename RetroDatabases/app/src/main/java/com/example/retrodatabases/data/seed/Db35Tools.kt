package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1690 to 1700: a calculus textbook, a posthumous book on other worlds, a reference dictionary, a treatise on light, a mathematical dictionary and a theory of the Earth. */
val db35Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Calculus",
        title = "de l'Hopital's Analyse des infiniment petits",
        originalTitle = "Analyse des infiniment petits pour l'intelligence des lignes courbes",
        author = "Guillaume de l'Hopital",
        year = 1696,
        summary = "The first printed textbook of differential calculus, which taught the method of infinitely small quantities for finding the slope of a curve.",
        details = "It was printed in Paris in 1696 and drew on lectures that Johann Bernoulli had given to the author. The book contains the rule for fractions that take the form zero over zero, which carries the author's name although many historians credit Johann Bernoulli with finding it.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Huygens's Cosmotheoros",
        originalTitle = "Cosmotheoros, sive de terris coelestibus",
        author = "Christiaan Huygens",
        year = 1698,
        summary = "A posthumous book in which Huygens argued that the planets are inhabited, and reasoned about their sizes and distances.",
        details = "Huygens died in 1695, and his brother Constantijn published the Latin text in 1698, with an English version appearing the same year. The book argues by analogy from the Earth to the other planets, and it is an early case of reasoning about life beyond the Earth.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Bayle's Historical and Critical Dictionary",
        originalTitle = "Dictionnaire historique et critique",
        author = "Pierre Bayle",
        year = 1697,
        summary = "A reference work of short biographies and articles whose long notes used history to test received opinion.",
        details = "The first edition was printed in 1697 in two folio volumes, and Bayle's notes often contained more argument than the articles themselves. The work was read widely across Europe through the next century and was reprinted with additions.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Optics",
        title = "Huygens's Traite de la lumiere",
        originalTitle = "Traite de la lumiere",
        author = "Christiaan Huygens",
        year = 1690,
        summary = "Huygens's treatise presented light as waves spreading from every point of a wavefront, and explained reflection and refraction by that rule.",
        details = "It was published in Leiden in 1690 and set against Newton's corpuscular theory of light, which later prevailed in Britain. The construction of the wavefront is still taught under the name Huygens's principle.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Dictionaries",
        title = "Ozanam's mathematical dictionary",
        originalTitle = "Dictionnaire mathematique, ou idee generale des mathematiques",
        author = "Jacques Ozanam",
        year = 1691,
        summary = "A French dictionary of mathematical terms and topics, arranged alphabetically, for readers who wanted a general survey of the subject.",
        details = "Ozanam published it in Paris in 1691, and later editions followed in the next decades. Its articles covered arithmetic, geometry, astronomy and mechanics, with short definitions and notes on the history of each topic.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.TOOL,
        section = "Earth",
        title = "Whiston's New Theory of the Earth",
        originalTitle = "A New Theory of the Earth",
        author = "William Whiston",
        year = 1696,
        summary = "Whiston explained the Flood and the Earth's history by the passage of a comet, in a book that drew controversy for decades.",
        details = "The book was printed in London in 1696 and argued that a comet's close passage caused the Deluge. Its claims about the Earth's history were contested by other natural philosophers for decades afterwards.",
    ),

)
