package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1390 to 1400: a Ming law code, a Korean land reform, an English statute, a Japanese reunion and a Scandinavian union. */
val db65Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 65,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Da Ming lu, the Ming law code of the Hongwu court",
        originalTitle = "Da Ming lu",
        author = "The Hongwu court",
        year = 1397,
        summary = "The Da Ming lu, the Ming law code, reached its final form in 1397 and sets out the crimes and punishments of the Ming state.",
        details = "A first code was issued in 1373, revised texts followed, and the 1397 text kept the five punishments of the older Chinese codes. Accounts differ on how many articles were changed in each revision, so the 1397 text is the one usually named.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.SYSTEM,
        section = "Land",
        title = "The gwajeon land reform of Goryeo Korea, dated 1391",
        originalTitle = "Gwajeon-beop",
        author = "The Goryeo court, with Jeong Do-jeon's proposals",
        year = 1391,
        summary = "The gwajeon reform of 1391 allotted fields in Goryeo Korea to officials, military officers and retainers in place of the older grants.",
        details = "Jeong Do-jeon wanted small and equal plots, but the allotment made favoured the officials already in place and the officers and retainers who had earned recognition. The reform is usually dated 1391, and accounts differ on an earlier countrywide survey, which some place in 1388 to 1389.",
        rare = true,
    ),

    entry(
        databaseId = 65,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Statute of Praemunire, against appeals to Rome",
        originalTitle = "Statute of Praemunire",
        author = "The English Parliament of 1393",
        year = 1393,
        summary = "The English Statute of Praemunire of 1393 widened the older law against taking English cases to papal courts without the king's licence.",
        details = "It repeated and extended the statute of 1353 on papal provisions and appeals, and the 1393 act is the one usually meant by the name. It was one of several royal measures on the Church in the reign of Richard II.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The reunion of the Northern and Southern Courts of Japan",
        originalTitle = "Nanboku-cho",
        author = "The shogun Ashikaga Yoshimitsu",
        year = 1392,
        summary = "In 1392 the rival Northern and Southern Courts of Japan were joined under the Ashikaga shogun Yoshimitsu, ending a split that had run since 1336.",
        details = "The Southern emperor Go-Kameyama handed the regalia to the Northern line in 1392, and accounts differ on whether the promised terms for the Southern line were kept. The split is dated from 1336, so the reunion closed more than fifty years of two claimants to the throne.",
    ),

    entry(
        databaseId = 65,
        kind = Kind.SYSTEM,
        section = "Crowns",
        title = "The Kalmar Union, three northern crowns under one queen",
        originalTitle = "Kalmarunionen",
        author = "Queen Margaret I of Denmark",
        year = 1397,
        summary = "The Kalmar Union of 1397 joined the crowns of Denmark, Norway and Sweden under Queen Margaret I, and each kingdom kept its own laws.",
        details = "The agreement is dated June 1397 at Kalmar, and the union broke apart in stages, with Sweden leaving in 1523. Accounts differ on how far the three realms were governed as one, so historians often call it a personal union rather than a single state.",
    ),

)
