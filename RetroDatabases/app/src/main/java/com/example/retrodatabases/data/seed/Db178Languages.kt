package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 260 to 270: Liu Hui's commentary on the Nine Chapters and Shapur I's trilingual inscription at Naqsh-e Rustam. */
val db178Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 178,
        kind = Kind.LANGUAGE,
        section = "Mathematics",
        title = "Liu Hui's commentary on the Nine Chapters on the Mathematical Art",
        originalTitle = "Jiuzhang suanshu zhu",
        author = "Liu Hui",
        year = 263,
        summary = "Liu Hui completed his commentary on the Nine Chapters on the Mathematical Art in 263, giving reasons and proofs for the methods of the older problem collection.",
        details = "Liu Hui dates the commentary to 263, the fourth year of the Jingyuan era of Wei. The commentary explains why the methods of the older collection work, and it adds proofs of its own, including a method for the value of pi.",
    ),

    entry(
        databaseId = 178,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Shapur I's inscription on the Ka'ba-ye Zartosht, in Middle Persian, Parthian and Greek",
        originalTitle = "Res Gestae Divi Saporis",
        author = "Shapur I, king of the Sasanian empire",
        year = 262,
        summary = "Shapur I had his deeds carved in Middle Persian, Parthian and Greek on the Ka'ba-ye Zartosht at Naqsh-e Rustam, about 262.",
        details = "The Greek version is the longest, at about seventy lines, and the Middle Persian and Parthian versions run to about thirty-five and thirty lines, each on a different wall of the monument. The text covers the king's lineage, the provinces under his rule, his clashes with Rome and the fire temples he commissioned, and its date of about 262 is an estimate. The name Res Gestae Divi Saporis is a modern scholarly title, not the king's own.",
        rare = true,
    ),

)
