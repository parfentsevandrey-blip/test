package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Books of 940 to 947: the Huajian ji lyrics, the Old Book of Tang, al-Mas'udi's Meadows of Gold and Narshakhi's history of Bukhara. */
val db110Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 110,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "The Huajian ji, an anthology of about five hundred ci lyrics compiled in Later Shu in 940",
        originalTitle = "Huajian ji",
        author = "Zhao Chongzuo",
        year = 940,
        summary = "Zhao Chongzuo compiled the Huajian ji, an anthology of about five hundred ci lyrics, at the Later Shu court in 940.",
        details = "The preface, by Ouyang Jiong, is dated 940. Accounts differ on how many poets contributed, ten or eighteen, and most of the lyrics treat romance and parting, many of them sung by courtesans.",
    ),

    entry(
        databaseId = 110,
        kind = Kind.LANGUAGE,
        section = "China",
        title = "The Old Book of Tang, the official history of the Tang dynasty, presented to the Later Jin court in 945",
        originalTitle = "Jiu Tangshu",
        author = "Liu Xu, with Zhao Ying as the main editor",
        year = 945,
        summary = "The Old Book of Tang, the official history of the Tang dynasty, was presented to the Later Jin emperor in 945 after work begun in 941.",
        details = "Shi Jingtang, the founder of the Later Jin, ordered the compilation in 941, and it reached Emperor Chu of Jin in 945, with Liu Xu credited as chief editor and most of the editing attributed to Zhao Ying. The work has 200 chapters, arranged as annals, treatises and biographies, and in 1060 the New Book of Tang was presented as its replacement.",
    ),

    entry(
        databaseId = 110,
        kind = Kind.LANGUAGE,
        section = "Arabic histories",
        title = "The Meadows of Gold, al-Mas'udi's history and geography of the world, completed around 947",
        originalTitle = "Muruj al-dhahab wa ma'adin al-jawhar",
        author = "al-Mas'udi",
        year = 947,
        summary = "Al-Mas'udi finished the Meadows of Gold, an Arabic history and geography of the world, around 947.",
        details = "The completion is commonly given as 947 (AH 336), but a passage of the text gives 943 (AH 331) as the present year, so accounts differ by a few years. The book surveys peoples, kings and the lands of the known world, and al-Mas'udi later wrote a shorter revised work, the Book of Notice and Revision.",
    ),

    entry(
        databaseId = 110,
        kind = Kind.LANGUAGE,
        section = "Samanid Bukhara",
        title = "The history of Bukhara by Narshakhi, dedicated to the Samanid amir Nuh I in 943 or 944",
        originalTitle = "Tarikh-i Bukhara",
        author = "Abu Bakr Muhammad ibn Ja'far al-Narshakhi",
        year = 943,
        summary = "Narshakhi wrote his history of Bukhara in Arabic for the Samanid amir Nuh I, and the Arabic text is lost, but a Persian version of 1128 survives.",
        details = "The dedication is dated 943 or 944, and accounts differ by a year. The Persian version of 1128 or 1129 by al-Qubawi is a reworked adaptation rather than a literal translation, and it carries the narrative on to 975.",
        rare = true,
    ),

)
