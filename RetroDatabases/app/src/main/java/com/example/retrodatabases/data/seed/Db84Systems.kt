package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1200 to 1210: a Paris privilege for scholars, a crusader empire at Constantinople, a Mongol script, the Delhi Sultanate and the first Mongol state. */
val db84Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 84,
        kind = Kind.SYSTEM,
        section = "France",
        title = "Philip Augustus's privilege of 1200 for the scholars of Paris",
        originalTitle = "Latin charter of 1200 to the scholars",
        author = "Philip Augustus of France",
        year = 1200,
        summary = "In 1200 Philip Augustus of France granted the scholars of Paris a privilege that exempted them from the regular courts and punished townspeople who harmed them.",
        details = "The privilege exempted the scholars from the regular courts, the church courts included. Accounts differ on whether the grant was a royal ordinance or a charter, and on how old the scholars' community already was in 1200, and a Church charter of 1229 is also given as its first formal recognition, so 1200 marks an early stage rather than a founding date.",
        rare = true,
    ),

    entry(
        databaseId = 84,
        kind = Kind.SYSTEM,
        section = "Byzantium",
        title = "The Latin Empire of Constantinople, a crusader state of 1204",
        originalTitle = "The Latin Empire of Constantinople",
        author = "Baldwin of Flanders and the crusade leaders",
        year = 1204,
        summary = "The Latin Empire was a crusader state set up at Constantinople in 1204, after the Fourth Crusade had taken the city.",
        details = "The city fell in April 1204, and Baldwin of Flanders was crowned emperor in Hagia Sophia later that spring. The empire lasted until Michael VIII Palaiologos of Nicaea retook Constantinople on 25 July 1261.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.SYSTEM,
        section = "Mongolia",
        title = "The Mongol script, adapted from Uyghur writing about 1204",
        originalTitle = "Mongol bichig",
        author = "Tatatunga, a Uyghur official",
        year = 1204,
        summary = "The Mongol script was adapted from the Uyghur alphabet about 1204, when a Uyghur official was set to teaching it to the Mongol princes.",
        details = "The account comes from the Secret History of the Mongols and other traditional sources, and the date follows from Chinggis Khan's defeat of the Naiman. Accounts differ on the date and on how much of the Uyghur letter set was taken over, so the year is given as approximate.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.SYSTEM,
        section = "India",
        title = "The Delhi Sultanate, founded in 1206 under Qutb al-Din Aibak",
        originalTitle = "Delhi Sultanate",
        author = "Qutb al-Din Aibak",
        year = 1206,
        summary = "The Delhi Sultanate was a Muslim state in northern India, founded in 1206 by Qutb al-Din Aibak and ruled from Delhi until 1526.",
        details = "Aibak had been a slave soldier of Muhammad of Ghur, and the sultanate grew out of the Ghurid conquests in northern India. Accounts differ on how firmly the first sultans held power in their early years, so 1206 is the year most often given for the start of the state.",
    ),

    entry(
        databaseId = 84,
        kind = Kind.SYSTEM,
        section = "Mongolia",
        title = "The Mongol Empire, proclaimed at the kurultai of 1206",
        originalTitle = "Yeke Mongol Ulus",
        author = "Chinggis Khan",
        year = 1206,
        summary = "The Mongol Empire took its form in 1206, when Temujin was proclaimed Chinggis Khan at a great assembly on the Onon river.",
        details = "The assembly set up the first guards and offices of the new state, and it is the usual starting date for the empire. The name Yeke Mongol Ulus, the Great Mongol State, is the usual later form, and accounts differ on the arrangements made at the assembly.",
    ),

)
