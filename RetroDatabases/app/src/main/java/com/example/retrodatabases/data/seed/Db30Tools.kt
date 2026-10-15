package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1740 to 1750: a type specimen, a commentary on Newton, a natural history, an algebra and two books of the age. */
val db30Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Printing",
        title = "Fournier's specimen of printing types",
        originalTitle = "Modeles des caracteres de l'imprimerie",
        author = "Pierre-Simon Fournier le Jeune",
        year = 1742,
        summary = "A printed book that showed the typefaces of a Paris foundry, with each face set out in its sizes so that printers could compare and order type.",
        details = "Fournier printed the specimen in Paris in 1742. Printers used such books to choose type by face and size, and the book shows how a foundry sold its letters.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Mechanics",
        title = "The Le Seur and Jacquier commentary on Newton's Principia",
        originalTitle = "Philosophiae naturalis principia mathematica, perpetuo commentario illustrata",
        author = "Thomas Le Seur and Francois Jacquier",
        year = 1742,
        summary = "A three-volume Latin commentary that works through the propositions of Newton's Principia step by step, with the mathematics written out.",
        details = "The commentary was printed in Geneva in three volumes, the first in 1739 and the last in 1742. Its notes fill in the steps that Newton left to the reader, so that a student could follow the proofs of the orbits.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Natural history",
        title = "Buffon's Natural History, first volumes",
        originalTitle = "Histoire naturelle, generale et particuliere",
        author = "Georges-Louis Leclerc, comte de Buffon",
        year = 1749,
        summary = "A multi-volume natural history that described animals, minerals and the earth in one connected account, written for readers as well as for scholars.",
        details = "The first volumes appeared in Paris in 1749, and the series grew to many volumes over the following decades. Its later volumes were argued over for years by naturalists in several countries.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Saunderson's Elements of Algebra",
        originalTitle = "The Elements of Algebra",
        author = "Nicholas Saunderson",
        year = 1740,
        summary = "A textbook of algebra by a blind Cambridge professor, printed after his death, which taught the subject from the first principles.",
        details = "Saunderson, who had lost his sight in infancy, taught mathematics at Cambridge. The book was printed in 1740, the year after his death in 1739.",
        rare = true,
    ),

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Voyages",
        title = "Anson's voyage round the world",
        originalTitle = "A Voyage Round the World in the Years MDCCXL, I, II, III, IV",
        author = "Richard Walter, after George Anson",
        year = 1748,
        summary = "A published account of a naval squadron's voyage of 1740 to 1744, which sailed round Cape Horn and across the Pacific.",
        details = "Richard Walter, chaplain of the squadron, compiled the narrative, which was published in 1748, four years after the voyage ended. Most of the men who died on the voyage were lost to scurvy, and the book records the Pacific route in detail.",
    ),

    entry(
        databaseId = 30,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Wright's Original Theory of the Universe",
        originalTitle = "An Original Theory or New Hypothesis of the Universe",
        author = "Thomas Wright of Durham",
        year = 1750,
        summary = "A book proposing that the stars form a disc, with the Sun among them, which is seen from inside as the band of the Milky Way.",
        details = "Wright published the theory in 1750 with plates that showed how the stars might be arranged. Kant later discussed Wright's idea in his own cosmology, and it is an early model of the galaxy as a flat system.",
    ),
)
