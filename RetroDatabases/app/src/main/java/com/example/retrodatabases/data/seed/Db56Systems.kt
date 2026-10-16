package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1480 to 1490: presses in Spain, Venice, Westminster and Lyon, and a Portuguese fort on the Gold Coast. */
val db56Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "Anton de Centenera's press at Zamora",
        originalTitle = "Vita Christi",
        author = "Antonio de Centenera, printer",
        year = 1482,
        summary = "Antonio de Centenera's workshop at Zamora printed Castilian poems and devotional books in the early 1480s, one of the first presses at work in Castile.",
        details = "The Zamora workshop printed the Vita Christi of Inigo de Mendoza and the Regimiento de principes of Gomez Manrique, and its activity is usually dated from about 1481 to 1498. The Sinodal of Aguilafuente, printed at Segovia in 1472, is often cited as the earliest dated book printed in Spain, so Zamora is not the first Spanish press.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The Venetian book trade of the 1480s",
        originalTitle = "Stamperie veneziane",
        author = "Venetian printers and booksellers",
        year = 1480,
        summary = "In the 1480s Venice was the busiest printing centre in Italy, and its presses sent Latin and vernacular books across Italy and into the north.",
        details = "Printers from Germany and Italy set up presses in Venice in the 1470s and 1480s, and booksellers carried the books to other Italian cities and beyond the Alps. Accounts differ on how many presses were working in any single year, since many shops were short-lived.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "Caxton's press at Westminster",
        originalTitle = "Westminster printing house",
        author = "William Caxton",
        year = 1485,
        summary = "William Caxton's press at Westminster printed English books through the 1480s, and its best-known title of 1485 is Sir Thomas Malory's Morte Darthur.",
        details = "Caxton set up his press at Westminster in the late 1470s, and accounts differ on whether the first book came out in 1476 or 1477. His output in the 1480s included translations from French and English originals, among them the Golden Legend of 1483 and the Morte Darthur of 1485.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "Sao Jorge da Mina, the Portuguese gold-trade fort of 1482",
        originalTitle = "Castelo de Sao Jorge da Mina",
        author = "The Portuguese crown and Diogo de Azambuja",
        year = 1482,
        summary = "The Portuguese crown built the fortified trading post of Sao Jorge da Mina on the Gold Coast in 1482, to protect a gold trade that it had brought under royal control.",
        details = "Azambuja's squadron left Portugal in December 1481 with prefabricated materials and reached Elmina on 19 January 1482, and a 1482 accord with the local ruler Caramansa cleared the way. Accounts differ on the day building began, and the Dutch took the fort in 1637.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.SYSTEM,
        section = "Printing",
        title = "The printing presses of Lyon in the 1480s",
        originalTitle = "Imprimerie lyonnaise",
        author = "Lyon printers and booksellers",
        year = 1484,
        summary = "Lyon became a printing centre in the 1470s and 1480s, and its presses printed French and Latin books for the trade along the Rhone and beyond.",
        details = "The first press at Lyon is usually linked with Guillaume Le Roy from 1473, and accounts differ on which book came first from it. Chuquet finished his manuscript Triparty at Lyon in 1484, and that book was not printed until 1880.",
        rare = true,
    ),

)
