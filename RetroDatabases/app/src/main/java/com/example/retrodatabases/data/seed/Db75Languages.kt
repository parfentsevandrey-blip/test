package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1290 to 1300: Marco Polo's travel book, a Marathi commentary on the Gita, the Ramkhamhaeng stone, Zhou Daguan's account of Cambodia, the Codex Manesse and an Occitan verse encyclopedia. */
val db75Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "Travel",
        title = "Marco Polo's travel book, written with Rustichello in 1298 to 1299",
        originalTitle = "Le Divisament dou monde",
        author = "Marco Polo and Rustichello of Pisa",
        year = 1298,
        summary = "Marco Polo's account of his travels in Asia was dictated in 1298 to 1299 while he was held in a Genoese prison, and Rustichello of Pisa wrote it down in Franco-Italian.",
        details = "Polo had been taken after the naval battle of Curzola in 1298, and the book circulated in many versions with different texts. Accounts differ on the year the book was finished, so the date given is that of its composition.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "Marathi",
        title = "Jnaneshvari, a Marathi commentary on the Gita finished in 1290",
        originalTitle = "Bhavarthadipika",
        author = "Jnaneshvar of Nevasa",
        year = 1290,
        summary = "Jnaneshvar finished his Marathi commentary on the Bhagavad Gita at Nevasa in 1290, a long poem in the ovi metre that brought the Gita into the Marathi vernacular.",
        details = "The text gives its date as Saka 1212, which is 1290 in the Common Era. Accounts differ on the poet's dates, and one tradition gives his death in 1296.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "Thai",
        title = "The Ramkhamhaeng inscription, a stone of Sukhothai dated 1292",
        originalTitle = "Ramkhamhaeng inscription",
        author = "King Ramkhamhaeng of Sukhothai",
        year = 1292,
        summary = "The Ramkhamhaeng inscription is a stone from Sukhothai, written in an early Thai script and dated 1292, and it is the best-known early text in the Thai language.",
        details = "Most accounts give the date as 1292, though a 1909 study gives 1293. The stone's authenticity was challenged in the late 1980s and 1990s, and most specialists now accept that it is at least partly genuine.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "Cambodia",
        title = "Zhou Daguan's account of Cambodia after a stay at Angkor",
        originalTitle = "Zhenla fengtu ji",
        author = "Zhou Daguan",
        year = 1297,
        summary = "Zhou Daguan, a Chinese envoy, returned from Angkor in 1297 and wrote a description of the Khmer court and its customs, one of the few eyewitness accounts of the city.",
        details = "He stayed in Angkor from August 1296 to July 1297, and the date when he finished his book is not documented. The text that survives is thought to be an abridged version of the original.",
        rare = true,
    ),

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "German",
        title = "The Codex Manesse, a German songbook begun about 1300",
        originalTitle = "Grosse Heidelberger Liederhandschrift",
        author = "Scribes and painters of Zurich",
        year = 1300,
        summary = "The Codex Manesse, a German manuscript of love songs with painted portraits of their poets, was begun in Zurich about 1300.",
        details = "The manuscript takes its name from the Manesse family of Zurich, but accounts differ on who commissioned it, and its copying ran on into the 1330s. The painted portraits show the poets as knights and lovers, so the book is also a gallery of pictures.",
    ),

    entry(
        databaseId = 75,
        kind = Kind.LANGUAGE,
        section = "Occitan",
        title = "Matfre Ermengaud's Breviari d'amor, an Occitan verse encyclopedia",
        originalTitle = "Breviari d'amor",
        author = "Matfre Ermengaud of Beziers",
        year = 1290,
        summary = "Matfre Ermengaud, a notary of Beziers, wrote the Breviari d'amor in Occitan verse, a long encyclopedia of learning arranged under the theme of love.",
        details = "Scholars date the poem to about 1288 to 1292, and its many manuscripts show how widely it was read. It draws on Latin learning, so it is a rare example of a vernacular encyclopedia of the period.",
        rare = true,
    ),

)
