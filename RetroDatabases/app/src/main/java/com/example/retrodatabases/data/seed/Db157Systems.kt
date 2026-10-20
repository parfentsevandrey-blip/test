package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 470 to 480: Euric's law code, the fall of Hanseong, the deposition of Romulus Augustulus and the founding of the Southern Qi. */
val db157Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 157,
        kind = Kind.SYSTEM,
        section = "Visigoths",
        title = "Euric's law code for the Visigoths, among the earliest written Germanic law codes",
        originalTitle = "Codex Euricianus",
        author = "Euric, king of the Visigoths",
        year = 475,
        summary = "Euric's code gave the Visigoths of Toulouse a written law in Latin, and the text survives only in a fragmentary copy.",
        details = "Dates given for its completion include 475 and 483, and the text is usually placed before 480, most likely at Toulouse. Euric is the usual author, though one view credits Alaric II, and much of the content comes from Roman law.",
        rare = true,
    ),

    entry(
        databaseId = 157,
        kind = Kind.SYSTEM,
        section = "Korea",
        title = "The fall of Hanseong, the Paekche capital, to Goguryeo in 475",
        originalTitle = "The fall of Hanseong, the Paekche capital, to Goguryeo in 475",
        author = "King Jangsu of Goguryeo",
        year = 475,
        summary = "In 475 a Goguryeo attack took Hanseong, the capital of the Paekche kingdom in Korea, and King Gaero was executed.",
        details = "Sources agree on 475 for the attack and the king's death, and the court then moved south to Ungjin, now Gongju. Accounts differ on the date of that move, which is given as 474, 475 or 476.",
        rare = true,
    ),

    entry(
        databaseId = 157,
        kind = Kind.SYSTEM,
        section = "Italy",
        title = "The deposition of Romulus Augustulus, the traditional end of the western Roman emperors",
        originalTitle = "The deposition of Romulus Augustulus, the traditional end of the western Roman emperors",
        author = "Odoacer, commander of the Germanic troops in Italy",
        year = 476,
        summary = "In 476 Odoacer deposed the young emperor Romulus Augustulus at Ravenna, and later writers took the event as the end of the western Roman line.",
        details = "The year 476 is the traditional date, and historians still debate whether it marks an end, since Julius Nepos kept the western title in Dalmatia until his murder in 480. Odoacer sent the imperial insignia to Constantinople and went on to rule Italy.",
    ),

    entry(
        databaseId = 157,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Southern Qi dynasty founded at Jiankang in 479",
        originalTitle = "Nan Qi",
        author = "Xiao Daocheng, later Emperor Gao of Qi",
        year = 479,
        summary = "In 479 Xiao Daocheng became the first emperor of the Southern Qi at Jiankang, after the Liu Song court had given up the throne.",
        details = "The last Liu Song emperor, Shun, handed over the throne in 479, and the Southern Qi lasted until 502, when the Liang dynasty replaced it. Its capital was Jiankang, the city that is now Nanjing.",
        rare = true,
    ),

)
