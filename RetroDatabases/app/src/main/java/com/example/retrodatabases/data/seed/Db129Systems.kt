package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 750 to 760: the battle of the Zab of 750, the Council of Hieria of 754, the An Lushan rebellion of 755, the emirate of Cordoba and the Donation of Pepin of 756. */
val db129Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.SYSTEM,
        section = "Caliphate",
        title = "The battle of the Zab in January 750, which broke the army of the last Umayyad caliph, Marwan II",
        originalTitle = "Ma'rakat al-Zab",
        author = "Abd Allah ibn Ali",
        year = 750,
        summary = "The battle of the Zab, fought in January 750, broke the army of the last Umayyad caliph, Marwan II, and handed the caliphate to the Abbasids.",
        details = "The battle was fought near the Great Zab, a tributary of the Tigris, and the Abbasid army was led by Abd Allah ibn Ali. Marwan fled through Syria and Palestine to Egypt, where he was killed at Busir in August 750, and the Umayyad caliphate of Damascus came to an end.",
    ),

    entry(
        databaseId = 129,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "The Council of Hieria in 754, which condemned the veneration of icons under Constantine V",
        originalTitle = "Council of Hieria",
        author = "Constantine V",
        year = 754,
        summary = "The council that Constantine V called at the palace of Hieria near Constantinople in 754 condemned the veneration of icons.",
        details = "The Second Council of Nicaea in 787 declared its decisions void and restored the veneration of icons. Its acts are lost, and what is known of them comes mainly from the replies of its opponents. Neither the Eastern nor the Western churches count Hieria among the ecumenical councils.",
        rare = true,
    ),

    entry(
        databaseId = 129,
        kind = Kind.SYSTEM,
        section = "Tang",
        title = "The An Lushan rebellion, begun at Fanyang in December 755 and ended in 763",
        originalTitle = "An Lushan zhi luan",
        author = "An Lushan",
        year = 755,
        summary = "The An Lushan rebellion began at Fanyang in December 755, took both Tang capitals in 756, and ended in 763 after eight years of war.",
        details = "An Lushan was a Tang general of mixed Sogdian and Turkic descent, and his armies took Luoyang and then Chang'an in 756, forcing the emperor Xuanzong to flee. An Lushan himself was killed in 757, and the war was not ended until 763.",
    ),

    entry(
        databaseId = 129,
        kind = Kind.SYSTEM,
        section = "Umayyad",
        title = "The emirate of Cordoba, founded in 756 by Abd al-Rahman I, a surviving Umayyad prince",
        originalTitle = "Imarat Qurtuba",
        author = "Abd al-Rahman I",
        year = 756,
        summary = "In 756 Abd al-Rahman I, an Umayyad prince who had escaped the Abbasid massacre, defeated the governor of al-Andalus near Cordoba and founded an independent emirate.",
        details = "He had reached Spain in 755, and his victory over Yusuf al-Fihri near Cordoba gave the Umayyads a state outside the Abbasid caliphate. The rulers of Cordoba kept the title of emir until 929, when Abd al-Rahman III took the title of caliph.",
        rare = true,
    ),

    entry(
        databaseId = 129,
        kind = Kind.SYSTEM,
        section = "Frankish",
        title = "The Donation of Pepin, a grant of conquered Lombard lands to the papacy in 756",
        originalTitle = "Donatio Pippini",
        author = "Pepin the Short",
        year = 756,
        summary = "In 756 Pepin the Short, king of the Franks, gave the papacy the lands he had taken from the Lombards, including the exarchate of Ravenna.",
        details = "The Lombard king Aistulf had taken Ravenna in 751, and Pepin's campaigns of 754 and 756 forced him to give up the conquered cities. The original text of the grant is not preserved, and accounts differ on its exact extent.",
    ),

)
