package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 600 to 610: the laws of AEthelberht of Kent, the Seventeen-Article Constitution and the Grand Canal of the Sui. */
val db144Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 144,
        kind = Kind.SYSTEM,
        section = "English",
        title = "The laws of AEthelberht of Kent, the earliest surviving English law code, usually dated 602 or 603",
        originalTitle = "AEthelberhtes domas",
        author = "King AEthelberht of Kent",
        year = 602,
        summary = "The laws of AEthelberht of Kent, written in Old English about 602 or 603, are the oldest law code to survive in English.",
        details = "Only one manuscript preserves the code, the Textus Roffensis, a volume compiled at Rochester in the early twelfth century, so the text reaches us some five centuries after it was made. Its clauses fix payments as multiples of a base sum, with the highest multiple, twelvefold, for offences against the property of God and the church. The dating leans partly on the death of Augustine of Canterbury, placed about 604, so the year 602 or 603 is a scholarly estimate rather than a recorded date.",
        rare = true,
    ),

    entry(
        databaseId = 144,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Seventeen-Article Constitution attributed to Prince Shotoku, dated 604 and disputed as to its authorship",
        originalTitle = "Jushichijo kenpo",
        author = "Prince Shotoku (attributed)",
        year = 604,
        summary = "A set of seventeen short articles of moral and administrative precepts for officials, attributed to Prince Shotoku and dated 604 in the Nihon Shoki.",
        details = "The Nihon Shoki, completed in 720, places the proclamation in the twelfth year of Empress Suiko, which is 604, and the articles are written in Classical Chinese with Buddhist and Confucian ideas. Scholars dispute whether the text was composed in 604, and many think the chronicle's compilers wrote or reshaped it later, so its authenticity remains disputed.",
    ),

    entry(
        databaseId = 144,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "The Grand Canal of China, begun in 605 under the Sui to join the Yellow and Yangzi river systems",
        originalTitle = "Da Yunhe",
        author = "Emperor Yang of Sui",
        year = 605,
        summary = "Emperor Yang of Sui began the Grand Canal in 605 to carry grain and troops between the north and the Yangzi valley.",
        details = "The work was built in stages, and the southern reach toward Hangzhou was finished about 610, though accounts differ on the completion dates of the different sections. Later dynasties rebuilt and extended the canal, which carried tribute grain for many centuries.",
    ),

)
