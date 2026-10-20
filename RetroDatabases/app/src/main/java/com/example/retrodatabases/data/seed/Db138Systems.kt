package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 660 to 670: the Umayyad caliphate at Damascus, the Synod of Whitby, the Omi code, the fall of Goguryeo and the Kogo register. */
val db138Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 138,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "Muawiya's Umayyad caliphate at Damascus, begun in 661 after the Year of Unity",
        originalTitle = "al-khilafa al-umawiyya",
        author = "Muawiya ibn Abi Sufyan",
        year = 661,
        summary = "Muawiya, governor of Syria, took the caliphate and made Damascus its seat, and his Umayyad line of caliphs ruled from there until 750.",
        details = "The sources give two years for his rise: 660, when he was proclaimed at Jerusalem, and 661, the Year of Unity after the murder of Ali and the withdrawal of Hasan, and this record uses 661. Damascus stayed the seat of the caliphs until the dynasty fell in 750.",
    ),

    entry(
        databaseId = 138,
        kind = Kind.SYSTEM,
        section = "Northumbrian",
        title = "The Synod of Whitby in 664, which chose the Roman Easter for the Northumbrian church",
        originalTitle = "Streanaeshalch synod",
        author = "King Oswiu of Northumbria",
        year = 664,
        summary = "At a synod held under King Oswiu, the Northumbrian church chose the Roman way of fixing Easter over the Irish one.",
        details = "Colman of Lindisfarne defended the Irish reckoning, and Wilfrid argued for the Roman one; Bede gives the account in book 3 of his Ecclesiastical History. After the decision Colman withdrew to Iona, and Northumbria kept the Roman reckoning from then on.",
    ),

    entry(
        databaseId = 138,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Omi code, a law code that the Nihon Shoki says Tenji issued in 668, whose existence is disputed",
        originalTitle = "Omi-ryo",
        author = "Emperor Tenji",
        year = 668,
        summary = "The Nihon Shoki records a law code, the Omi code, issued under Emperor Tenji in 668, but historians doubt that it existed as a full code.",
        details = "The Nihon Shoki is the main source for the code and its date, and no text of it survives, so the year is the date given in the chronicle and not a proven one. Scholars differ on whether the Omi code was a full code or a set of decrees.",
        rare = true,
    ),

    entry(
        databaseId = 138,
        kind = Kind.SYSTEM,
        section = "Korean",
        title = "The fall of Goguryeo in 668, when Tang and Silla armies took its capital Pyongyang",
        originalTitle = "Goguryeo",
        author = "The Tang and Silla armies",
        year = 668,
        summary = "The northern Korean kingdom of Goguryeo ended in 668, when its capital Pyongyang fell to Tang and Silla armies.",
        details = "King Bojang surrendered in that year, but Silla and the Tang fought on for several years over the old kingdom's lands, so 668 marks the fall of the kingdom and not the end of the war. This record gives the year only.",
    ),

    entry(
        databaseId = 138,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Kogo register of 670, the earliest known national household register of Japan, compiled under Emperor Tenji",
        originalTitle = "Kogo-nen-jaku",
        author = "The court of Emperor Tenji",
        year = 670,
        summary = "The Kogo register of 670, the earliest known national register of households in Japan, was compiled under Emperor Tenji.",
        details = "No copy is known to survive, so the register is known only from later references to it. It was an early step toward the household registers that later codes required, and this record takes 670 as the year of its making.",
        rare = true,
    ),

)
