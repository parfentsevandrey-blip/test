package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 730 to 740: Leo III's edict of 730, Gregory III's Roman synod of 731, the battle of Tours of 732 and the Kaiyuan Code of 737. */
val db131Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 131,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Leo III's edict of 730 ordering icons removed from churches, and the deposition of Patriarch Germanos",
        originalTitle = "Edict against the holy images",
        author = "Leo III the Isaurian",
        year = 730,
        summary = "Leo III's edict of 730 ordered the icons removed from churches, and the patriarch Germanos I was deposed for refusing to back the policy.",
        details = "Theophanes dates Leo's first step against the images to 726, while many modern accounts put the decisive edict and Germanos's deposition in 730, so the year is disputed. Germanos had been patriarch of Constantinople since 715, and he refused to support the emperor's attack on the icons.",
    ),

    entry(
        databaseId = 131,
        kind = Kind.SYSTEM,
        section = "Papal",
        title = "Gregory III's Roman synod of 731, which condemned the destruction of images",
        originalTitle = "Synodus Romana",
        author = "Pope Gregory III",
        year = 731,
        summary = "A synod of bishops held in Rome in 731 under Pope Gregory III condemned the destruction of images and the policy of the Byzantine emperor.",
        details = "The synod answered Leo III's policy from the West, and it is one of the earliest western replies to the iconoclast quarrel. Its acts are known from the papal records of the period.",
        rare = true,
    ),

    entry(
        databaseId = 131,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The battle of Tours in 732, in which Charles Martel defeated the Umayyad army under Abd al-Rahman",
        originalTitle = "Battle of Tours",
        author = "Charles Martel",
        year = 732,
        summary = "Charles Martel, the Frankish mayor of the palace, defeated the Umayyad army under Abd al-Rahman al-Ghafiqi in 732, in a battle named for Tours or for Poitiers.",
        details = "Some accounts name the battle for Tours and others for Poitiers, and Abd al-Rahman was killed in the fighting. The chronicles give the size of the armies and the losses only loosely, so the figures are disputed.",
    ),

    entry(
        databaseId = 131,
        kind = Kind.SYSTEM,
        section = "Tang",
        title = "The Kaiyuan Code of 737, the Tang penal code issued in the reign of Emperor Xuanzong",
        originalTitle = "Kaiyuan lu",
        author = "Tang government",
        year = 737,
        summary = "The Kaiyuan Code, the Tang penal code completed in 737, kept the framework of the Tang code of 653 in twelve books.",
        details = "The code was issued in the reign of Emperor Xuanzong, and its penal scale of five punishments in twenty grades is counted in a separate record of this decade.",
    ),

)
