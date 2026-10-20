package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 650 to 660: the Hakuchi era name of 650, the Yongwei code of 651 and Kotoku's Naniwa palace of 652. */
val db139Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Hakuchi era name of 650, Japan's second era name, adopted when a white pheasant was presented at court",
        originalTitle = "Hakuchi",
        author = "Emperor Kotoku",
        year = 650,
        summary = "The era name Hakuchi began in 650 as Japan's second era name after Taika, and it was taken from a white pheasant presented at court.",
        details = "The Nihon Shoki records the pheasant as an omen, and the Hakuchi name ran until 654, the year Emperor Kotoku died. Both the omen and the name come from the court record, so 650 is the start of the era as that record gives it.",
        rare = true,
    ),

    entry(
        databaseId = 139,
        kind = Kind.SYSTEM,
        section = "Chinese",
        title = "The Yongwei code of 651, the Tang legal statutes of the Yonghui era",
        originalTitle = "Yongwei lu",
        author = "Emperor Gaozong's court",
        year = 651,
        summary = "The Yongwei code, the Tang legal statutes of the Yonghui era, was issued in 651 and became the base for the Tang codes that followed.",
        details = "Accounts differ between 651 for the statutes and 653 for the Tang Code with its commentary, and the year used here is that of the statutes. The Yonghui era ran from 650 to 655, and the code takes its name from that era, so the statutes and the era name belong together.",
        rare = true,
    ),

    entry(
        databaseId = 139,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "Kotoku's palace at Naniwa, Nagara Toyosaki, completed in 652, where the court sat before its return to Asuka",
        originalTitle = "Naniwa Nagara-Toyosaki",
        author = "Emperor Kotoku",
        year = 652,
        summary = "Emperor Kotoku's palace at Naniwa, called Nagara Toyosaki, was completed in 652, and the court sat there until it went back to Asuka.",
        details = "The palace is dated here by its completion in 652, though the court had been at Naniwa from 645, so accounts differ on when the move began. Kotoku died at the palace in 654, and the return to Asuka is given as 654 or 655 in different sources.",
        rare = true,
    ),

)
