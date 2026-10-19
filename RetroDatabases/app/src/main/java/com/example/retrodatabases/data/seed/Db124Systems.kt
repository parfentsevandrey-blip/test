package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 800 to 810: Charlemagne's coronation, Harun al-Rashid's Mecca arrangement and the fall of the Barmakids, Nikephoros I's accession and the Kusuko incident. */
val db124Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "Charlemagne's coronation as emperor at Rome on Christmas Day 800",
        originalTitle = "Imperator Augustus",
        author = "Pope Leo III and Charlemagne",
        year = 800,
        summary = "On Christmas Day 800 Pope Leo III crowned Charlemagne emperor in St Peter's at Rome, reviving the imperial title in the West.",
        details = "Charlemagne had been king of the Franks since 768. Einhard reports that he said he would not have entered the church that day had he known of the plan, and historians still debate how much he knew; the title he received was Augustus, the first to hold the imperial title in the West since 476.",
    ),

    entry(
        databaseId = 124,
        kind = Kind.SYSTEM,
        section = "Abbasid",
        title = "Harun al-Rashid's Mecca arrangement of 802 for his sons al-Amin and al-Ma'mun",
        originalTitle = "Mecca Protocol of 802",
        author = "Harun al-Rashid",
        year = 802,
        summary = "In 802 Harun al-Rashid settled the succession between his sons al-Amin and al-Ma'mun during the pilgrimage to Mecca, with signed papers deposited at the Kaaba.",
        details = "The papers gave al-Amin the caliphate and gave al-Ma'mun a largely independent rule in the east, with a penalty for any brother who broke the terms. Historians differ on whether the arrangement was meant to secure the succession or to divide the realm, and it broke down after Harun's death in the quarrel that led to the civil war of 811 to 813.",
        rare = true,
    ),

    entry(
        databaseId = 124,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Nikephoros I takes the Byzantine throne after deposing the empress Irene in 802",
        originalTitle = "Nikephoros I",
        author = "Nikephoros I",
        year = 802,
        summary = "In 802 Nikephoros, chief minister of the imperial treasury, deposed the empress Irene and took the Byzantine throne, which he held until his death in 811.",
        details = "Irene had ruled alone since 797, when she had her son Constantine VI deposed and blinded. The reign of Nikephoros was marked by financial measures and by wars with the Bulgarians, and he died in battle against them in 811.",
    ),

    entry(
        databaseId = 124,
        kind = Kind.SYSTEM,
        section = "Abbasid",
        title = "Harun al-Rashid's fall of the Barmakid family in 803, after decades at the head of government",
        originalTitle = "Barmakids",
        author = "Harun al-Rashid",
        year = 803,
        summary = "In 803 Harun al-Rashid had his vizier Ja'far ibn Yahya executed and his father Yahya imprisoned, ending the long rule of the Barmakid family in government.",
        details = "The Barmakids had served the caliphs in the highest offices for decades, and accounts differ on the causes of the fall. Yahya was held in prison, and the family never regained its place at the centre of power.",
    ),

    entry(
        databaseId = 124,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Kusuko incident of 810, when Emperor Heizei tried to move the capital back to Nara",
        originalTitle = "Kusuko no ran",
        author = "Emperor Heizei and Fujiwara no Kusuko",
        year = 810,
        summary = "In 810 Emperor Heizei tried to move the capital from Heian-kyo back to Heijo-kyo at Nara, a plan backed by Fujiwara no Kusuko that collapsed when his brother Emperor Saga's forces moved against it.",
        details = "Heizei had given the throne to his brother Saga in 809 but still held influence at court. Accounts differ on how far the plan was Heizei's own and how far it was driven by Kusuko, and after the revolt failed Heizei became a monk.",
        rare = true,
    ),

)
