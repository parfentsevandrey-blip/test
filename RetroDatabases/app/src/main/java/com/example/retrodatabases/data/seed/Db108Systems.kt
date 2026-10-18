package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 960 to 970: the Song founding, Otto I's imperial crown, the Samanids, Later Shu and Fatimid Cairo. */
val db108Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Song dynasty, founded in 960 when Zhao Kuangyin took the throne from the Later Zhou",
        originalTitle = "Song dynasty",
        author = "Zhao Kuangyin, Emperor Taizu of Song",
        year = 960,
        summary = "Zhao Kuangyin was proclaimed emperor in early 960 and founded the Song dynasty, which took over the Later Zhou state at Kaifeng.",
        details = "The proclamation followed a military revolt at Chenqiao, near Kaifeng, and the first era name of the new court was Jianlong, which ran from 960 to 963. The Song never regained the northern frontier districts that the Khitan Liao held. The year 960 is the start of the Song's own count, and its institutions were built over the following decades.",
    ),

    entry(
        databaseId = 108,
        kind = Kind.SYSTEM,
        section = "Germany and Italy",
        title = "Otto I's coronation as emperor at Rome in 962, often taken as the start of the Holy Roman Empire",
        originalTitle = "Coronation of Otto I as emperor",
        author = "Otto I and Pope John XII",
        year = 962,
        summary = "Pope John XII crowned Otto I emperor at Rome in 962, and the coronation is often taken as the start of the Holy Roman Empire.",
        details = "Accounts give 2 February 962 for the coronation, which followed Otto's crowning as king at Aachen in 936. The name Holy Roman Empire came into use only later, so the coronation is a convenient starting point rather than a date the state gave itself. The Ottonian line kept the imperial title until the death of Henry II in 1024.",
    ),

    entry(
        databaseId = 108,
        kind = Kind.SYSTEM,
        section = "Bukhara",
        title = "The Samanid emir Mansur I takes power at Bukhara in 961 and rules until 976",
        originalTitle = "Samanid emirate at Bukhara",
        author = "Mansur I ibn Nuh",
        year = 961,
        summary = "Mansur I ibn Nuh became Samanid emir at Bukhara in 961, and his court was the setting for the Persian history commissioned from Bal'ami.",
        details = "Mansur I succeeded his father Abd al-Malik I and held Bukhara until his death in 976. The Samanids ruled a Persian-speaking state in Transoxiana and Khorasan, and the Tarikhnama was commissioned under Mansur in 963. The year 961 marks the start of his reign, not a state document.",
        rare = true,
    ),

    entry(
        databaseId = 108,
        kind = Kind.SYSTEM,
        section = "Sichuan",
        title = "The Northern Song takes Later Shu in 965, ending Meng Chang's kingdom based at Chengdu",
        originalTitle = "Conquest of Later Shu",
        author = "Meng Chang and the Northern Song",
        year = 965,
        summary = "The Northern Song conquered Later Shu in 965, ending the kingdom that Meng Chang had ruled from Chengdu since 934.",
        details = "Meng Chang adopted the Guangzheng era name in 938, and the court at Chengdu counted its years by that era. The year 965 is the end of his reign, when the Northern Song took the kingdom, and Later Shu was one of the Ten Kingdoms that the Northern Song absorbed in turn. The court's stone classics are tied to the same Guangzheng years.",
        rare = true,
    ),

    entry(
        databaseId = 108,
        kind = Kind.SYSTEM,
        section = "Egypt",
        title = "The Fatimid conquest of Egypt in 969 and the founding of al-Qahira, the palace city that became Cairo",
        originalTitle = "Founding of al-Qahira",
        author = "Jawhar al-Siqilli for the caliph al-Mu'izz",
        year = 969,
        summary = "In 969 the general Jawhar al-Siqilli took Egypt for the Fatimid caliph al-Mu'izz, and the palace city of al-Qahira was founded beside Fustat.",
        details = "Accounts date both the conquest and the founding to 969, and the city named al-Qahira later gave its name to Cairo. The caliph al-Mu'izz moved his court to the city in 973, which made Egypt the centre of the Fatimid state. The Fatimids kept Cairo as their capital until their dynasty ended in 1171.",
    ),

)
