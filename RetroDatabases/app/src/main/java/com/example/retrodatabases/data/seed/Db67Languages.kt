package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1370 to 1379: Barbour's Brus, Gower's Mirour, Oresme's French Aristotle, Sir Gawain, and two Chaucer poems, the Book of the Duchess and the House of Fame. */
val db67Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "Scots",
        title = "The Brus, John Barbour's poem of Robert Bruce in Scots",
        originalTitle = "The Brus",
        author = "John Barbour",
        year = 1375,
        summary = "John Barbour's Brus, a long poem about the Scottish king Robert Bruce, is one of the earliest substantial poems in Scots.",
        details = "Barbour was archdeacon of Aberdeen, and scholars usually date the poem to 1375. The oldest surviving manuscript was copied more than a century after the poem was written.",
        rare = true,
    ),

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Chaucer's House of Fame, a dream vision of about 1380",
        originalTitle = "The House of Fame",
        author = "Geoffrey Chaucer",
        year = 1379,
        summary = "Chaucer's House of Fame is a Middle English dream vision, usually dated to about 1379 to 1380, and it breaks off before the poem is finished.",
        details = "A narrator is carried by an eagle to a house where the reports of the world are gathered, and the poem is one of Chaucer's works of the late 1370s and early 1380s. Scholars differ on the exact date, and some place the poem a year or two later, so the year given is the start of the usual range.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "Anglo-Norman",
        title = "John Gower's Mirour de l'omme, a moral poem in Anglo-Norman French",
        originalTitle = "Mirour de l'omme",
        author = "John Gower",
        year = 1376,
        summary = "Gower's Mirour de l'omme is a long moral poem in Anglo-Norman French, written in England in the later 1370s.",
        details = "Scholars date the poem to about 1376 to 1379, which makes it earlier than Gower's Latin and English works of the 1380s and 1390s. Accounts differ on how far it was revised later, so the year given is the first of the range.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "French",
        title = "Oresme's French Aristotle, the Livre du ciel et du monde",
        originalTitle = "Le livre du ciel et du monde",
        author = "Nicole Oresme",
        year = 1377,
        summary = "Nicole Oresme completed a French translation of Aristotle's On the Heavens, with his own commentary, in 1377 for King Charles V of France.",
        details = "The commentary discusses the heavens and the motion of the earth, and the French text helped build a vocabulary for natural philosophy in the vernacular. The year 1377 is the usual date of completion, and the king's commission explains why the book was written in French.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Sir Gawain and the Green Knight, an alliterative romance of about 1375",
        originalTitle = "Sir Gawain and the Green Knight",
        author = "An unknown English poet",
        year = 1375,
        summary = "Sir Gawain and the Green Knight is an anonymous English romance in alliterative verse, usually dated to the later fourteenth century from about 1375.",
        details = "Its only surviving copy is in a manuscript of about 1400 that also holds three other poems, so the poem may be older than its copy. Accounts differ on its exact date, and the range 1375 to 1400 is the one most often given.",
    ),

    entry(
        databaseId = 67,
        kind = Kind.LANGUAGE,
        section = "Middle English",
        title = "Chaucer's Book of the Duchess, an elegy written about 1370",
        originalTitle = "The Book of the Duchess",
        author = "Geoffrey Chaucer",
        year = 1370,
        summary = "The Book of the Duchess is an early elegy by Chaucer in Middle English, written about 1370 for the death of Blanche of Lancaster.",
        details = "Blanche died in 1368, and scholars date the poem to about 1368 to 1372, so 1370 is the middle of that range. Accounts differ on the exact date, and the poem is one of Chaucer's earliest surviving works.",
    ),

)
