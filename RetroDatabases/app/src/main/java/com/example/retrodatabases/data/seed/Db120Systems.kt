package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 840 to 850: the Treaty of Verdun of 843, the Huichang persecution of 845 and Chang Pogo's sea network, ended by his death in 846. */
val db120Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.SYSTEM,
        section = "Carolingian",
        title = "The Treaty of Verdun, which divided the Carolingian empire into three kingdoms",
        originalTitle = "Treaty of Verdun",
        author = "Lothair I, Louis the German and Charles the Bald",
        year = 843,
        summary = "The Treaty of Verdun of 843 divided the Carolingian empire among three grandsons of Charlemagne, an early shape for the later frontier between France and Germany.",
        details = "The three kings were the sons of Louis the Pious: Lothair kept the middle kingdom and the imperial title, Louis the German took the east and Charles the Bald the west. The treaty was agreed in August 843, and the later settlement of Meerssen in 870 changed the middle kingdom again.",
    ),

    entry(
        databaseId = 120,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Huichang persecution of Buddhism in Tang China, 845",
        originalTitle = "Huichang fanan",
        author = "Emperor Wuzong of Tang",
        year = 845,
        summary = "In 845 the Tang state under Emperor Wuzong closed thousands of Buddhist monasteries, returned their monks to lay life and melted bronze images for coin.",
        details = "The campaign, named after the Huichang era of 841 to 846, targeted monasteries and their property. Emperor Xuanzong restored Buddhism after Wuzong's death in 846, and Ennin's diary records the closures as a foreign monk saw them.",
    ),

    entry(
        databaseId = 120,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "Chang Pogo's Cheonghae sea garrison and trade network, ended by his death in 846",
        originalTitle = "Jang Bogo",
        author = "Chang Pogo, Silla naval commander and trader",
        year = 846,
        summary = "Chang Pogo built a garrison and trade network around the Yellow Sea from the Cheonghae base on Wando, and it ended with his death in 846.",
        details = "The garrison on Wando island was founded in the 820s, and it let a Silla commander shape the trade routes between Silla, Tang China and Japan. Chang Pogo was killed in 846 in a dispute at the Silla court, and the garrison was closed after his death.",
        rare = true,
    ),

)
