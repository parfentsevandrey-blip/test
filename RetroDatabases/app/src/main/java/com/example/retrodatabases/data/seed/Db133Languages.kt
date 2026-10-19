package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 710 to 720: the Shitong of 710, the Kojiki of 712, the Lindisfarne Gospels of about 715 and Bede's Lives of the Abbots of about 716. */
val db133Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 133,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Liu Zhiji's Shitong, a critical treatise on the writing of history, completed in 710",
        originalTitle = "Shitong",
        author = "Liu Zhiji",
        year = 710,
        summary = "The Shitong, a critical study of Chinese historical writing by Liu Zhiji, was completed in 710 and tested the official histories by their sources and methods.",
        details = "Liu Zhiji, who lived from 661 to 721, worked as a historian at the Tang court, and the treatise points out errors in earlier histories and in the way their compilers worked. Its completion in 710 is the usual date, and the book is still read as an early theory of historical method.",
        rare = true,
    ),

    entry(
        databaseId = 133,
        kind = Kind.LANGUAGE,
        section = "Japanese",
        title = "The Kojiki, the oldest chronicle of Japan, completed for the court in 712",
        originalTitle = "Kojiki",
        author = "O no Yasumaro",
        year = 712,
        summary = "The Kojiki, a record of the ancient myths, rulers and songs of Japan, was completed by O no Yasumaro and presented to Empress Genmei in 712.",
        details = "It was written down from the recitation of Hieda no Are, who had learned the old traditions by heart, and it mixes Chinese characters used for their sense with characters used for their sound. Its preface is dated 712, and it is the oldest surviving chronicle of Japan.",
    ),

    entry(
        databaseId = 133,
        kind = Kind.LANGUAGE,
        section = "Northumbrian",
        title = "The Lindisfarne Gospels, a Northumbrian gospel book with carpet pages, made at Lindisfarne about 715",
        originalTitle = "Lindisfarne Gospels",
        author = "Eadfrith, bishop of Lindisfarne, as scribe",
        year = 715,
        summary = "The Lindisfarne Gospels, an illuminated Latin book of the four gospels, was written at Lindisfarne about 715 by the scribe Eadfrith, who was bishop there.",
        details = "Current scholarship puts it around 715, but accounts range from about 700 to about 720, and its inscription names Eadfrith as both scribe and bishop. Its interlace, carpet pages and figure work are in the same Insular style as the Book of Kells, and the Old English gloss added in the tenth century is a later addition.",
    ),

    entry(
        databaseId = 133,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Bede's Lives of the Abbots of Wearmouth and Jarrow, which tells of Ceolfrith's journey to Rome in 716",
        originalTitle = "Historia abbatum",
        author = "Bede",
        year = 716,
        summary = "Bede's Lives of the Abbots, a short Latin history of the abbots of Wearmouth and Jarrow, was written about 716 and tells of Ceolfrith's journey to Rome.",
        details = "The book covers Benedict Biscop and the abbots who followed him, Ceolfrith among them, and it records that Ceolfrith died on the way to Rome in 716 with a large Bible meant as a gift for the pope. Its date is given as about 716 in the usual accounts, and it is a key source for the making of that Bible.",
        rare = true,
    ),

)
