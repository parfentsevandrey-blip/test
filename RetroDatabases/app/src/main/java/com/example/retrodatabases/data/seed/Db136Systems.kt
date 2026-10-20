package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 680 to 690: the Third Council of Constantinople, the Synod of Hatfield and the Asuka Kiyomihara code. */
val db136Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 136,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Third Council of Constantinople, held from 680 to 681, which condemned the teaching of one will in Christ",
        originalTitle = "Constantinopolitanum III",
        author = "Constantine IV and the council fathers",
        year = 680,
        summary = "The Third Council of Constantinople, called by Constantine IV, declared that Christ has two wills and condemned the teaching of a single will.",
        details = "It met from November 680 to September 681 and is counted as the sixth ecumenical council. Its acts also condemned Pope Honorius I, and historians still argue over whether that condemnation was just.",
    ),

    entry(
        databaseId = 136,
        kind = Kind.SYSTEM,
        section = "English",
        title = "The Synod of Hatfield of 680, at which the English bishops professed the catholic faith",
        originalTitle = "Synodus Hatfeldensis",
        author = "Theodore of Tarsus and the English bishops",
        year = 680,
        summary = "The Synod of Hatfield, presided over by Theodore of Tarsus, saw the English bishops profess the catholic faith in a formal declaration.",
        details = "Bede gives the synod no year, saying only that it met about that time, while the Anglo-Saxon Chronicle and later accounts date it to 680. Bede names Eutychian error as the danger, but later accounts name Monothelite teaching, so accounts differ on which error the synod opposed.",
        rare = true,
    ),

    entry(
        databaseId = 136,
        kind = Kind.SYSTEM,
        section = "Japanese",
        title = "The Asuka Kiyomihara code of 689, one of the earliest ritsuryo codes of Japan",
        originalTitle = "Asuka Kiyomihara ryo",
        author = "The Japanese court",
        year = 689,
        summary = "The Asuka Kiyomihara code was promulgated in 689 as one of the earliest codes of the ritsuryo kind in Japan.",
        details = "Its promulgation in 689 is the date usually given, and it came before the Taiho code of 701, which is usually seen as its successor. Accounts differ on whether the code had a penal part (ritsu) or only the administrative part (ryo), so its make-up is disputed.",
        rare = true,
    ),

)
