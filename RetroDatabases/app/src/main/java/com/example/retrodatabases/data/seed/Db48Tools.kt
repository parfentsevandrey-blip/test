package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1560 to 1570: Mercator's world map, Nunes's algebra, Cardano's proportions, a chess book, a polyglot Bible and Ramus's school books. */
val db48Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Navigation",
        title = "Mercator's world map in his projection",
        originalTitle = "Nova et aucta orbis terrae descriptio ad usum navigantium emendate accommodata",
        author = "Gerardus Mercator",
        year = 1569,
        summary = "Mercator's world map of 1569 kept compass courses straight by spacing the lines of latitude further apart toward the poles.",
        details = "The map was printed at Duisburg in 1569 for navigators, and its grid of meridians and parallels became the basis of the projection that bears his name. Mercator did not print the formula that spaces the parallels, and the rule is usually credited to Edward Wright in 1599.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Algebra",
        title = "Nunes's algebra in Castilian",
        originalTitle = "Libro de algebra en arithmetica y geometria",
        author = "Pedro Nunes",
        year = 1567,
        summary = "Nunes's book of algebra in Castilian, printed in Antwerp in 1567, set out linear, quadratic and cubic equations for practical readers.",
        details = "The Antwerp printing of 1567 was a Castilian version of a Portuguese text that had circulated in manuscript. Accounts differ on the dates of that original, which different writers give as 1532, 1533 and 1534, and on the printer.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Proportions",
        title = "Cardano's Opus novum on proportions",
        originalTitle = "Opus novum de proportionibus numerorum",
        author = "Gerolamo Cardano",
        year = 1570,
        summary = "Cardano's book of 1570 on proportions set out how numbers and their ratios are compared, a theme that ran through his later mathematics.",
        details = "The book was printed at Basel in 1570 and deals with proportions of numbers. Accounts differ on how much of its material was new to Cardano's readers.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Chess",
        title = "Ruy Lopez's book of chess openings",
        originalTitle = "Libro de la invencion liberal y arte del juego del axedrez",
        author = "Ruy Lopez de Segura",
        year = 1561,
        summary = "Ruy Lopez's book of 1561 analysed chess openings and endgames for players, under a title that calls chess a liberal art of invention.",
        details = "The book was printed in 1561, and its title places the game among the liberal arts. Accounts differ on how far its analysis was original, and the opening that carries his name is linked to the book.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Bible",
        title = "The Antwerp polyglot Bible of 1568",
        originalTitle = "Biblia Regia",
        author = "Benito Arias Montano",
        year = 1568,
        summary = "The Antwerp polyglot Bible, printed from 1568, set the sacred text in several languages in parallel columns on one page.",
        details = "Philip II supported the project, and the printing ran in Antwerp from 1568 to about 1573. Accounts differ on the number of languages in each volume, so the record gives no count.",
    ),

    entry(
        databaseId = 48,
        kind = Kind.TOOL,
        section = "Mathematics",
        title = "Ramus's mathematics school books",
        originalTitle = "Scholarum mathematicarum libri unus et triginta",
        author = "Petrus Ramus",
        year = 1569,
        summary = "Ramus's book of 1569 on the mathematical arts was printed as a set of school books on arithmetic, geometry and related subjects.",
        details = "The work was printed at Basel in 1569 and gathers Ramus's teaching on the mathematical arts in thirty-one books. Accounts differ on how original his treatment was.",
    ),

)
