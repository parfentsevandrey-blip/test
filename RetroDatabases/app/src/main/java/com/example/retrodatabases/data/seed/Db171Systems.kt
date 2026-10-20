package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 330 to 340: Constantine's new capital, the Council of Tyre, the church over the Holy Sepulchre, the emperor's death and the division among his sons. */
val db171Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 171,
        kind = Kind.SYSTEM,
        section = "Capital",
        title = "The dedication of Constantinople as the new imperial capital",
        originalTitle = "Nova Roma",
        author = "Constantine the Great",
        year = 330,
        summary = "Constantine dedicated Byzantium, refounded as Constantinople, as the new capital of the Roman empire on 11 May 330.",
        details = "Building had begun in the 320s, and accounts differ on the exact start year. The dedication date of 11 May 330 is the one usually given for the city.",
    ),

    entry(
        databaseId = 171,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Tyre, which deposed Athanasius of Alexandria",
        originalTitle = "Synodus Tyria",
        author = "Bishops summoned by Constantine",
        year = 335,
        summary = "A council of bishops at Tyre, called by Constantine in 335, deposed Athanasius of Alexandria and sent him into exile.",
        details = "The council met in the summer of 335 and condemned Athanasius on charges that included violence against a rival bishop. Constantine then sent him into exile at Trier in Gaul, and some of the bishops went on to Jerusalem for the dedication of the Holy Sepulchre later that year.",
    ),

    entry(
        databaseId = 171,
        kind = Kind.SYSTEM,
        section = "Churches",
        title = "The consecration of Constantine's church over the Holy Sepulchre at Jerusalem",
        originalTitle = "Anastasis",
        author = "Constantine the Great and Bishop Macarius of Jerusalem",
        year = 335,
        summary = "Constantine's basilica over the rock tomb of Christ at Jerusalem was consecrated on 13 September 335, in the thirtieth year of his reign.",
        details = "The complex had a columned rotunda called the Anastasis and a large basilica to its east, and construction is usually dated to the mid-320s. Accounts differ on who performed the consecration, with some naming Bishop Macarius of Jerusalem and others Constantine himself.",
    ),

    entry(
        databaseId = 171,
        kind = Kind.SYSTEM,
        section = "Emperors",
        title = "The death of Constantine the Great at Nicomedia",
        originalTitle = "Constantinus Magnus",
        author = "No single author",
        year = 337,
        summary = "Constantine died at Ancyrona near Nicomedia on 22 May 337, after receiving baptism in his last illness.",
        details = "He had been ill for some time before he died, and accounts differ on the details of his baptism. The date 22 May 337 is the one usually given for his death.",
    ),

    entry(
        databaseId = 171,
        kind = Kind.SYSTEM,
        section = "Succession",
        title = "The division of the empire among Constantine's three surviving sons",
        originalTitle = "Divisio imperii",
        author = "Constantine II, Constantius II and Constans",
        year = 337,
        summary = "After Constantine's death his three surviving sons divided the empire, giving the east to Constantius II, Gaul, Britain and Spain to Constantine II, and Italy and Africa to Constans.",
        details = "A purge of other male relatives, including Constantine's nephews, followed in the same year. The arrangement did not last, and Constantine II was killed in an ambush near Aquileia in 340 during his quarrel with Constans.",
    ),

)
