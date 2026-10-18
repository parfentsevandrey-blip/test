package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1240 to 1250: a Jutland law, a Paris trial of the Talmud, a council at Lyon, a Mongol kurultai and the Mamluk sultanate of Egypt. */
val db80Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 80,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Jutland law of 1241, the Danish law of King Valdemar II",
        originalTitle = "Jydske Lov",
        author = "King Valdemar II and the Jutland assembly",
        year = 1241,
        summary = "The Jutland law, the Jydske Lov, was issued in 1241 for the Danish province of Jutland under King Valdemar II and remained the law of the province for centuries.",
        details = "It was agreed at an assembly at Vejle and written in Danish, and it set out fines for offences such as wounding and theft. Accounts differ on how fully its rules were applied in the years after 1241, and the text survives in later copies.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Talmud trial at Paris, with copies burned in 1242",
        originalTitle = "Trial of the Talmud",
        author = "Nicholas Donin and the Paris court",
        year = 1240,
        summary = "A disputation at Paris in 1240 put the Talmud on trial on charges brought by a convert, and copies of the book were burned at Paris in 1242.",
        details = "Nicholas Donin, a convert from Judaism, brought the charges, and the trial was held in Paris before churchmen. Accounts differ on the number of volumes burned and on the exact days, so 1240 and 1242 are the usual dates.",
        rare = true,
    ),

    entry(
        databaseId = 80,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The First Council of Lyon, which deposed Frederick II in 1245",
        originalTitle = "Concilium Lugdunense I",
        author = "Pope Innocent IV",
        year = 1245,
        summary = "The First Council of Lyon met under Pope Innocent IV in 1245, and its public sittings deposed the emperor Frederick II on 17 July.",
        details = "The public sittings were held on 28 June, 5 July and 17 July, and the deposition was pronounced at the last of them, although some accounts count a preliminary gathering on 26 June. Frederick did not attend and sent Thaddeus of Suessa to speak for him.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Mongol kurultai of 1246 that raised Guyuk to the throne",
        originalTitle = "Quriltai",
        author = "Mongol princes under the regent Toregene",
        year = 1246,
        summary = "The kurultai, an assembly of Mongol princes, raised Guyuk to the khanate in 1246 after several years in which his mother Toregene had governed as regent.",
        details = "Toregene governed after the death of Ogedei in 1241, and accounts differ on the exact day of the assembly, which the sources place in the summer of 1246. The papal envoy John of Plano Carpini was at the Mongol court at the time and described the gathering in his report.",
    ),

    entry(
        databaseId = 80,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Mamluk sultanate of Egypt, begun in 1250",
        originalTitle = "Dawlat al-Mamalik",
        author = "The Mamluk soldiers of Cairo",
        year = 1250,
        summary = "The Mamluk sultanate of Egypt began in 1250, when the military slaves of the Ayyubid army at Cairo took power and set Shajar al-Durr on the throne.",
        details = "Aybak, a Mamluk commander, soon married Shajar al-Durr and became sultan, and the sultanate lasted until the Ottoman conquest of 1517. Accounts differ on how far the first Mamluk rulers were accepted outside Cairo, and 1250 is the usual year of the change.",
    ),

)
