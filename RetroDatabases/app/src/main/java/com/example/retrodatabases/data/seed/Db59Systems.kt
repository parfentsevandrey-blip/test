package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1450 to 1460: papal bulls for the Portuguese crown, a papal sanction for a university at Freiburg, the University of Greifswald and the Malatestiana library at Cesena. */
val db59Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 59,
        kind = Kind.SYSTEM,
        section = "Papal grants",
        title = "The bull Romanus Pontifex of 1455 for the Portuguese crown",
        originalTitle = "Romanus Pontifex",
        author = "Pope Nicholas V, sent to King Afonso V of Portugal",
        year = 1455,
        summary = "The bull Romanus Pontifex of January 1455 backed the Portuguese crown in its wars along the West African coast and allowed it to hold non-Christians in perpetual servitude.",
        details = "The bull was issued at Rome in January 1455 and addressed to King Afonso V of Portugal. Accounts give 5 or 8 January 1455 for its date, and one gives 1454, so the year is used here without the day. Historians link its terms to the later Portuguese traffic in enslaved Africans, and the fall of Constantinople in 1453 is the background to the crusading appeals of those years.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.SYSTEM,
        section = "Papal grants",
        title = "The bull Dum diversas of 1452 to Afonso V",
        originalTitle = "Dum diversas",
        author = "Pope Nicholas V, sent to King Afonso V of Portugal",
        year = 1452,
        summary = "The bull Dum diversas of 18 June 1452 gave King Afonso V of Portugal licence to invade non-Christian lands and to hold their peoples in perpetual servitude.",
        details = "It was issued at Rome three years before Romanus Pontifex, and historians often read it as the first of the papal grants that backed Portugal's voyages along the African coast. Its wording is widely quoted, and accounts differ on how far its terms were applied in practice.",
        rare = true,
    ),

    entry(
        databaseId = 59,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Greifswald, founded in 1456",
        originalTitle = "Universitaet Greifswald",
        author = "Heinrich Rubenow, the mayor who began it",
        year = 1456,
        summary = "The University of Greifswald was founded on 17 October 1456 on the initiative of the mayor Heinrich Rubenow, with the approval of the pope and the emperor.",
        details = "The university's own history gives 17 October 1456 as the founding date, and an older encyclopedia dates the foundation bull to 29 May 1456, so the bull and the founding day are kept apart. Some sources count an earlier start, from 1436, when the University of Rostock taught at Greifswald for seven years.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The papal sanction of 1455 for a university at Freiburg",
        originalTitle = "Universitas Friburgensis",
        author = "The papacy, for the Freiburg plan",
        year = 1455,
        summary = "Papal sanction for a university at Freiburg is dated 20 April 1455, before the founding that a university history page places in 1457 or 1458.",
        details = "The sanction is dated 20 April 1455 in that history, and the founding is given as 1457 or 1458, so the two dates are kept apart. The year used is that of the sanction.",
    ),

    entry(
        databaseId = 59,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Malatestiana library at Cesena, a municipal library of the 1450s",
        originalTitle = "Biblioteca Malatestiana",
        author = "Malatesta Novello, lord of Cesena",
        year = 1454,
        summary = "The Malatestiana library at Cesena was opened to the public in 1454, and sources describe it as the earliest municipal library in Europe.",
        details = "Construction is dated 1447 to 1452, and accounts differ between completion in 1452 and the public opening of 15 August 1454, which is the year used here. Papal authorization for the plan is dated 1445, and ownership passed to the city rather than to the clergy or the nobles.",
        rare = true,
    ),

)
