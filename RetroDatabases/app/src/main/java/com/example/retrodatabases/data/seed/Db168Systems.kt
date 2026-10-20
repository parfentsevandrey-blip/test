package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 360 to 370: Julian's school edict, the Alexandrian council of 362, Julian's death, Jovian's peace with Shapur II and the plan to rebuild the Temple. */
val db168Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 168,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "Julian's school edict of 362, which set rules for who could teach in the cities",
        originalTitle = "Edict on teachers",
        author = "The emperor Julian",
        year = 362,
        summary = "Julian's school edict of 17 June 362 set the terms on which teachers could practise in the cities, and it is often read as a measure against Christian teachers.",
        details = "The edict required teachers to be men of good character, and it was in force only from July 362 to January 364. Accounts differ on how far it kept Christians from teaching the classics, and the sources date it to 17 June 362.",
        rare = true,
    ),

    entry(
        databaseId = 168,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The council of Alexandria of 362 and its Tome to the Antiochenes",
        originalTitle = "Tomus ad Antiochenos",
        author = "Athanasius of Alexandria",
        year = 362,
        summary = "The council that Athanasius held at Alexandria in 362 sent a letter, the Tome, to the Antiochenes to heal the split among their Nicene believers.",
        details = "The council met in the spring or summer of 362, after Julian had let exiled bishops return, and it took the Nicene Confession as the basis of faith. The Tome was carried to Antioch to end a quarrel between two Nicene groups there, and historians differ on how far that quarrel was about wording rather than doctrine.",
        rare = true,
    ),

    entry(
        databaseId = 168,
        kind = Kind.SYSTEM,
        section = "War",
        title = "The death of Julian in Persia on 26 June 363",
        originalTitle = "The death of Julian",
        author = "Roman and Sasanian armies",
        year = 363,
        summary = "Julian, the last non-Christian emperor, died on 26 June 363 from a wound received in fighting with Sasanian forces in Persia.",
        details = "The wound came in the fighting near Samarra, and accounts differ on the battle's name, since some place the wound at Ctesiphon, and on the hour of his death. The army then chose Jovian as emperor.",
    ),

    entry(
        databaseId = 168,
        kind = Kind.SYSTEM,
        section = "Diplomacy",
        title = "Jovian's peace with Shapur II, which gave Nisibis to Persia in 363",
        originalTitle = "The peace of Jovian",
        author = "Jovian and Shapur II",
        year = 363,
        summary = "Jovian's peace with the Sasanian king Shapur II in 363 handed Nisibis and five provinces beyond the Tigris to Persia so that the Roman army could leave.",
        details = "Jovian made the treaty after Julian's death, when the army was short of food and could not hold its position in Persia. The sources name Nisibis, Singara and five provinces beyond the Tigris as the cession, and they describe the terms as the price of the army's safe return.",
    ),

    entry(
        databaseId = 168,
        kind = Kind.SYSTEM,
        section = "Religion",
        title = "Julian's plan to rebuild the Temple at Jerusalem, stopped in 363",
        originalTitle = "Rebuilding the Temple",
        author = "Julian and the Jewish community of Jerusalem",
        year = 363,
        summary = "In 363 Julian ordered the Temple at Jerusalem to be rebuilt, and the work stopped after fire broke out at the site.",
        details = "Ammianus describes balls of fire that drove the workers from the foundations, and the project was dropped after Julian's death. Accounts differ on how far the work went and on what Julian intended by it.",
        rare = true,
    ),

)
