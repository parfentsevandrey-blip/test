package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1710 to 1720: books on chance, a treatise on increments, a chemical table, Newton's Principia in its second edition and Keill's astronomy lectures. */
val db33Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Probability",
        title = "Bernoulli's Ars Conjectandi",
        originalTitle = "Ars conjectandi",
        author = "Jacob Bernoulli",
        year = 1713,
        summary = "The posthumous book that sets out the theory of combinations and games of chance and states the law of large numbers.",
        details = "Nicolas Bernoulli edited the book and published it in Basel in 1713, after his uncle Jacob died in 1705. The fourth part states the law of large numbers, and the earlier parts treat combinations and games of chance, so historians count it among the first full treatises on probability.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Probability",
        title = "De Moivre's Doctrine of Chances",
        originalTitle = "The Doctrine of Chances",
        author = "Abraham de Moivre",
        year = 1718,
        summary = "A London book of rules for dice and card problems, which solved questions of chance by counting equally likely cases.",
        details = "De Moivre published the first edition in London in 1718, after earlier papers in the Philosophical Transactions. Later editions in 1738 and 1756 added new results, and the book was the standard English text on chance for much of the century.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Calculus",
        title = "Taylor's Methodus incrementorum",
        originalTitle = "Methodus incrementorum directa et inversa",
        author = "Brook Taylor",
        year = 1715,
        summary = "A London book on the method of increments, which contains the expansion of a function about a point that carries Taylor's name.",
        details = "Taylor printed the book in London in 1715, and it treats the direct and inverse methods of increments, that is, finite differences and their sums. The expansion for a function near a point appears there, although the name Taylor's series came later.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Chemistry",
        title = "Geoffroy's table of chemical affinities",
        originalTitle = "Table des differents rapports observes en chymie",
        author = "Etienne-Francois Geoffroy",
        year = 1718,
        summary = "A table that lists, column by column, the substances each one will take up or drive out, in order of attraction.",
        details = "Geoffroy presented the table to the Paris Academy of Sciences in 1718, and it was printed in the academy's memoirs. Chemists used the columns to predict which substances would displace which.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Physics",
        title = "Newton's Principia, second edition",
        originalTitle = "Philosophiae naturalis principia mathematica",
        author = "Isaac Newton, edited by Roger Cotes",
        year = 1713,
        summary = "The second edition of Newton's Principia, edited by Roger Cotes, whose preface defended universal gravitation against its critics.",
        details = "Cotes prepared the 1713 edition and wrote its preface, which answered critics of the word attraction and of the law of gravitation. The text was prepared with corrections that Newton had supplied.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Keill's astronomy lectures at Oxford",
        originalTitle = "Introductio ad veram astronomiam",
        author = "John Keill",
        year = 1718,
        summary = "Keill's Oxford lectures on Newtonian astronomy, printed in 1718, which taught Kepler's laws and their proof from gravitation to students.",
        details = "Keill held the Savilian chair of astronomy at Oxford and gave these lectures there, and the Latin book appeared in 1718. It set out the Newtonian theory of the planets and derived Kepler's laws from the law of gravitation.",
        rare = true,
    ),
)
