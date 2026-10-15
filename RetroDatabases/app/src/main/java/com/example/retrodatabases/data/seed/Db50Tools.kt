package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1540 to 1545: an English arithmetic, the first account of Copernicus, the heliocentric book, a German cosmography, Cardano's algebra and Gesner's universal bibliography. */
val db50Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Arithmetic",
        title = "Recorde's Grounde of Artes, arithmetic in English",
        originalTitle = "The Grounde of Artes",
        author = "Robert Recorde",
        year = 1543,
        summary = "Recorde's arithmetic taught whole-number reckoning in English to readers who had no Latin, and its first version set out the rule of three.",
        details = "The first edition is usually dated 1543, and the book was reprinted many times until 1700, though a catalogue record mentions a 1542 facsimile, so accounts differ on the first date. The first version called the rule of three the golden rule, and fractions and false position came later in an enlarged edition of 1552.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Rheticus's Narratio prima, the first printed account of Copernicus",
        originalTitle = "Narratio prima",
        author = "Georg Joachim Rheticus",
        year = 1540,
        summary = "Rheticus's Narratio prima of 1540 was the first printed account of Copernicus's sun-centred plan of the heavens, published three years before the great book.",
        details = "It was printed at Danzig in 1540, after Rheticus had visited Copernicus at Frombork and studied his theory. The little book set out the outline of the new arrangement of the planets before the De revolutionibus appeared in 1543.",
        rare = true,
    ),

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Copernicus's De revolutionibus, the heliocentric book",
        originalTitle = "De revolutionibus orbium coelestium",
        author = "Nicolaus Copernicus",
        year = 1543,
        summary = "Copernicus's book of 1543 set out the sun-centred arrangement of the planets, with the tables and trigonometry needed to calculate their places.",
        details = "It was printed at Nuremberg in 1543, and an unsigned preface by Osiander presented the new arrangement as a mathematical hypothesis rather than a physical truth. Accounts differ on whether Copernicus saw the first printed copy before his death in that same year.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Cosmography",
        title = "Munster's Cosmographia, the world described in German",
        originalTitle = "Cosmographia",
        author = "Sebastian Munster",
        year = 1544,
        summary = "Munster's Cosmographia of 1544 described the lands of the world with woodcut maps and city views, and it was printed in German for a wide readership.",
        details = "The first edition appeared at Basel in 1544, and its German text made it a best seller that was reprinted for decades. A Latin edition followed in 1550.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Cardano's Ars magna, the solution of cubic and quartic equations",
        originalTitle = "Ars magna",
        author = "Girolamo Cardano",
        year = 1545,
        summary = "Cardano's Ars magna of 1545 printed methods for cubic and quartic equations, the first published solutions of equations of the third and fourth degree.",
        details = "It was printed at Nuremberg in 1545, and it gave the cubic method that Cardano had learned from Tartaglia under an oath of secrecy, so the account of its discovery is disputed. The quartic method is credited to Cardano's pupil Ferrari and is printed in the same book.",
    ),

    entry(
        databaseId = 50,
        kind = Kind.TOOL,
        section = "Bibliography",
        title = "Gesner's Bibliotheca universalis, the universal bibliography",
        originalTitle = "Bibliotheca universalis",
        author = "Conrad Gesner",
        year = 1545,
        summary = "Gesner's Bibliotheca universalis of 1545 listed the authors and books of the learned world in one alphabetical work, and it is among the first universal bibliographies.",
        details = "It was printed at Zurich in 1545 by Froschauer and listed books in Latin, Greek and Hebrew under their authors. Later catalogues of printed books owe something to its plan of listing every author in one order.",
    ),

)
