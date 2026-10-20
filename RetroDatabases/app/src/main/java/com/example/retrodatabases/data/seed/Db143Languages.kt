package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 610 to 620: the Zhubing yuanhou lun, a Sui treatise on diseases completed in 610, and Prince Shotoku's Lotus commentary, traditionally dated 615. */
val db143Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Zhubing yuanhou lun, a Sui treatise on the causes and symptoms of diseases, completed by decree in 610",
        originalTitle = "Zhubing yuanhou lun",
        author = "Chao Yuanfang and others (attributed)",
        year = 610,
        summary = "The Zhubing yuanhou lun, a Chinese encyclopedia of disease causes and symptoms, was completed by Sui imperial decree in 610.",
        details = "It sorts diseases into categories, gives the cause and the signs of each, and puts diet and bodily exercises ahead of drugs and needles as remedies. The Book of Sui and the Old Book of Tang name Wu Jingxian and Wu Jing as compilers, and Chao Yuanfang appears as author only from the Song period, so the attribution is disputed. The work survives complete, and the usual date of its completion is 610, under the second Sui emperor's decree.",
        rare = true,
    ),

    entry(
        databaseId = 143,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "Prince Shotoku's commentary on the Lotus Sutra, the Hokke gisho, traditionally dated 615",
        originalTitle = "Hokke gisho",
        author = "Prince Shotoku (attributed)",
        year = 615,
        summary = "The Hokke gisho, a commentary on the Lotus Sutra, is traditionally dated to 615 and ascribed to Prince Shotoku.",
        details = "Tradition counts it as one of three sutra commentaries, on the Lotus, the Vimalakirti and the Srimala, that the prince is said to have written. Scholars are divided on whether he wrote it, and about seven-tenths of its text follows the Lotus commentary of the Liang monk Fayun (467 to 529). A colophon says the book belonged to the prince's private collection and was not from overseas, but its brushwork differs from the text and is thought by some to be a later addition.",
        rare = true,
    ),

)
