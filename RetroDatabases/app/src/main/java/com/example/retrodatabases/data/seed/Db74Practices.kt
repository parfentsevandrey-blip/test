package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1300 to 1310: a Florentine company's ledger at Nimes, daily fresco work at Padua, sealed royal orders against the Templars and a painter's contract at Siena. */
val db74Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 74,
        kind = Kind.PRACTICE,
        section = "Accounts",
        title = "The Farolfi company's ledger, kept at Nimes in 1299 to 1300",
        originalTitle = "Farolfi ledger",
        author = "Agents of the Farolfi company of Florence",
        year = 1300,
        summary = "The ledger of the Farolfi company's Provence branch, kept in 1299 and 1300, is among the earliest surviving records with entries posted as both debits and credits.",
        details = "The firm was Florentine, the branch was at Nimes, and its customers included the Archbishop of Arles. Some writers give the Genoese accounts of 1340 as the earliest complete double-entry set, so the claim for the Farolfi ledger depends on what is meant by complete.",
        rare = true,
    ),

    entry(
        databaseId = 74,
        kind = Kind.PRACTICE,
        section = "Painting",
        title = "The daily patches of Giotto's frescoes in the Arena Chapel at Padua",
        originalTitle = "Giornate",
        author = "Giotto di Bondone and his workshop",
        year = 1305,
        summary = "Giotto's workshop painted the Arena Chapel frescoes at Padua on fresh plaster, laying one day's patch at a time, and the joins between the patches can still be seen.",
        details = "The chapel was consecrated in 1305, and the frescoes are usually dated to about 1303 to 1305. The daily patches are called giornate, and the seams between them are one of the ways scholars trace how the work was done.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.PRACTICE,
        section = "Royal orders",
        title = "Philip IV's sealed orders for the arrest of the Templars, to be opened on one day",
        originalTitle = "Lettres closes",
        author = "Philip IV of France",
        year = 1307,
        summary = "In 1307 Philip IV sent sealed royal letters across France for the arrest of the Templars, with orders that they be opened together on 13 October.",
        details = "The letters were sent in September 1307, and the instruction to open them only on the fixed day let the arrests happen in one movement. Accounts differ on the exact days of sending, so the year 1307 is the safe date.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.PRACTICE,
        section = "Painting",
        title = "The Siena contract for Duccio's Maesta altarpiece of 1308",
        originalTitle = "Maesta",
        author = "Duccio di Buoninsegna and the works committee of Siena Cathedral",
        year = 1308,
        summary = "A contract of 1308 committed Duccio to paint a large altarpiece, the Maesta, for the cathedral of Siena, and the finished panel was carried through the city in 1311.",
        details = "The agreement is usually dated to 9 October 1308, and the finished panel was carried in procession through Siena in 1311. A 1980 review disputes that the 1308 document is the commission, arguing that it builds on a lost earlier contract.",
    ),

    entry(
        databaseId = 74,
        kind = Kind.PRACTICE,
        section = "Trade",
        title = "Alauddin Khalji's fixed prices in the markets of Delhi",
        originalTitle = "Market regulation of Alauddin Khalji",
        author = "Alauddin Khalji, sultan of Delhi",
        year = 1304,
        summary = "Alauddin Khalji, sultan of Delhi, fixed the prices of grain, cloth and other goods in the city's markets, a control system usually dated to about 1303 to 1304.",
        details = "The main account comes from Ziauddin Barani, who wrote in the mid-1300s, and the controls are usually linked to the army's supply needs after the Mongol raids of about 1303. Accounts differ on the exact dates and on how strictly the prices were enforced.",
    ),

)
