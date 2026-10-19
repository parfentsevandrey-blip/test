package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 880 to 890: the Peace of Alfred and Guthrum of about 886, Leo VI's Basilika of 888, the Epanagoge of 886, Hincmar's De ordine palatii of 882, and the kampaku of 887. */
val db116Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Peace of Alfred and Guthrum, which fixed the boundary of the Danelaw",
        originalTitle = "Peace of Alfred and Guthrum",
        author = "King Alfred of Wessex and the Danish king Guthrum",
        year = 886,
        summary = "The Peace of Alfred and Guthrum, made about 886, set the frontier between English and Danish law along the Thames, the Lea and Watling Street.",
        details = "The peace is usually dated to about 886, after Alfred had taken London, and it settled the Danelaw, the part of eastern England where Danish law was kept. The boundary it names runs up the Thames and the Lea and then along Watling Street, and the Danelaw it defines remained a distinct legal area for decades.",
    ),

    entry(
        databaseId = 116,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "The Basilika, Leo VI's Greek codification of Justinian's law in sixty books",
        originalTitle = "Basilika",
        author = "Leo VI the Wise, emperor of Byzantium",
        year = 888,
        summary = "The Basilika, Leo VI's Greek version of Justinian's law in sixty books, is usually dated to 888.",
        details = "Basil I began the work of turning Justinian's Corpus Juris Civilis into Greek, and his son Leo VI carried it through, since Latin had fallen out of common use in Constantinople by the ninth century. Accounts differ on the dates: some give 888 for the code and others place its completion about 892. The sixty books are the usual description of its size.",
    ),

    entry(
        databaseId = 116,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "The Epanagoge, a Byzantine summary of law issued under Basil I in 886",
        originalTitle = "Epanagoge",
        author = "Compilers working for Basil I",
        year = 886,
        summary = "The Epanagoge, a short Byzantine summary of law issued under Basil I in 886, gave judges and students a guide to the principles of the law.",
        details = "It was a revision of an earlier introduction to the law, and it was written as a guide for those who applied the law rather than as a full code. Its date of 886 is the one usually given, and it stands beside the larger Basilika, which carried the reform further under Leo VI.",
        rare = true,
    ),

    entry(
        databaseId = 116,
        kind = Kind.SYSTEM,
        section = "Francia",
        title = "Hincmar's De ordine palatii, a treatise on the officers of the royal household",
        originalTitle = "De ordine palatii",
        author = "Hincmar, archbishop of Reims",
        year = 882,
        summary = "Hincmar of Reims wrote De ordine palatii in 882 to set out the duties and order of the officers of the Frankish royal household.",
        details = "The treatise was addressed to the two young kings of West Francia, Louis III and Carloman, and it describes the palace offices as a set of duties in a fixed order. The date 882 is the one usually given. It is a short work of advice rather than a law code, but it treats the household as a system of offices with its own rules.",
        rare = true,
    ),

    entry(
        databaseId = 116,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The kampaku, the adult regency first held by Fujiwara no Mototsune",
        originalTitle = "Kampaku",
        author = "Fujiwara no Mototsune",
        year = 887,
        summary = "The kampaku, the regency for an adult emperor, was first held by Fujiwara no Mototsune, and its formal title is usually dated 887.",
        details = "Mototsune had already taken charge of court business from 884, and accounts differ over when the office began. The formal title is usually dated 887, but an imperial edict granting it was withdrawn after protests in June 888, and his elevation to kampaku in that year is said to have settled the matter. The office then became a long-lived post of the Fujiwara family.",
        rare = true,
    ),

)
