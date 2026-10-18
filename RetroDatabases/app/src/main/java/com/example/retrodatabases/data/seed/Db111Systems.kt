package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 930 to 939: Iceland's assembly and its law, a palace city in Cordoba, a southern kingdom at Jinling, a Khitan capital and the Ngo dynasty of Vietnam. */
val db111Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 111,
        kind = Kind.SYSTEM,
        section = "Iceland",
        title = "The Althing of Iceland, the general assembly of the Icelandic Commonwealth, traditionally founded in 930",
        originalTitle = "Althing",
        author = "Ulfljot and the Icelandic chieftains",
        year = 930,
        summary = "The Althing, the general assembly of Iceland, was founded in 930 by tradition, and the Icelandic Commonwealth that it anchored lasted from 930 to 1262.",
        details = "Tradition credits Ulfljot with bringing a code of law from Norway, and the assembly's Law Speaker held the law in memory and recited a third of it at each summer session. The year 930 is the traditional start given in the Icelandic histories, and the Commonwealth is dated 930 to 1262 in the usual reference works.",
    ),

    entry(
        databaseId = 111,
        kind = Kind.SYSTEM,
        section = "Spain",
        title = "Madinat al-Zahra, the palace city built outside Cordoba for Abd al-Rahman III",
        originalTitle = "Madinat al-Zahra",
        author = "Abd al-Rahman III",
        year = 936,
        summary = "Abd al-Rahman III began the palace city of Madinat al-Zahra outside Cordoba about 936, with the caliph's palace, the offices of government and the gardens set on terraces.",
        details = "Accounts usually date the start of the city to about 936, and the terraces give the site its layout, with the palace at the top and the working quarters below it. The usual account names the city after Abd al-Rahman's favourite concubine, Al-Zahra, and it served as a royal seat of the Cordoba caliphate.",
    ),

    entry(
        databaseId = 111,
        kind = Kind.SYSTEM,
        section = "China",
        title = "The Southern Tang state founded at Jinling by Li Bian in 937",
        originalTitle = "Nan Tang",
        author = "Li Bian",
        year = 937,
        summary = "Li Bian founded the Southern Tang state at Jinling, modern Nanjing, in 937, and it held the lower Yangtze valley among the southern kingdoms of the Ten Kingdoms period.",
        details = "The state took its name from the Tang dynasty that had fallen in 907, and it held its capital at Jinling until it submitted to the Song in 975. The founding date of 937 is the common reference date, and the state is one of the Ten Kingdoms that existed beside the northern Five Dynasties.",
    ),

    entry(
        databaseId = 111,
        kind = Kind.SYSTEM,
        section = "China",
        title = "Youzhou made the Khitan southern capital Nanjing in 938, the start of the five-capital arrangement",
        originalTitle = "Nanjing",
        author = "Taizong of Liao",
        year = 938,
        summary = "In 938 the Khitan court of the Liao made Youzhou, the site of modern Beijing, its Southern Capital Nanjing, and the upgrade set up the five-capital arrangement of the Liao.",
        details = "The city had passed to the Khitan in the settlement of the Sixteen Prefectures, and the five capitals were Shangjing, Zhongjing, Dongjing, Xijing and Nanjing. Accounts differ on how these capitals ranked, since one modern source counts Nanjing among four secondary capitals while a 2023 study holds that the Liao capitals cannot be sorted into primary and auxiliary roles.",
        rare = true,
    ),

    entry(
        databaseId = 111,
        kind = Kind.SYSTEM,
        section = "Vietnam",
        title = "Ngo Quyen's victory on the Bach Dang river and the Ngo dynasty of 939",
        originalTitle = "Ngo dynasty",
        author = "Ngo Quyen",
        year = 939,
        summary = "Ngo Quyen defeated a Southern Han fleet on the Bach Dang river in 938 and took the royal title in 939, starting the Ngo dynasty that ended Chinese rule over the Viet lands.",
        details = "The chronicles of Vietnam, written centuries later, say that the Southern Han fleet was trapped by iron-tipped stakes set in the river bed, and accounts differ by a year on the dates of the battle and the royal title. The year 939 is used for the dynasty, which is one of the first independent states of the Viet lands after a long period of Chinese administration.",
        rare = true,
    ),

)
