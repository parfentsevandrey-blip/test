package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 440 to 450: the Vandal kingdom of Carthage, the Robber Council of Ephesus, an edict on the Roman see, Taiwu's campaign against Buddhism and the Burgundian settlement in Sapaudia. */
val db160Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 160,
        kind = Kind.SYSTEM,
        section = "Vandals",
        title = "The Vandal kingdom of Carthage, taken in 439 and given Roman recognition by a treaty of 442",
        originalTitle = "Regnum Vandalorum",
        author = "Gaiseric and the Vandal kings",
        year = 442,
        summary = "Gaiseric's Vandals took Carthage in 439, and a treaty of 442 with the western empire gave their kingdom in North Africa Roman recognition.",
        details = "The city fell on 19 October 439, and accounts differ on whether it was taken after a siege or without a battle. The 442 treaty is read in two ways, as Rome granting the Vandal state its independence or as Rome conceding a hold on Carthage that it could no longer reverse.",
    ),

    entry(
        databaseId = 160,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Second Council of Ephesus, the Robber Council of 449 that deposed Flavian of Constantinople",
        originalTitle = "Latrocinium Ephesinum",
        author = "Dioscorus of Alexandria",
        year = 449,
        summary = "The Second Council of Ephesus met in 449 under Dioscorus of Alexandria, deposed Flavian of Constantinople, and was called a robbery by Pope Leo.",
        details = "Theodosius II called the council for August 449, and Dioscorus refused the request of Rome's envoys to have Leo's Tome read aloud. The council was disowned at Chalcedon in 451, and accounts of Flavian's fate after the council differ.",
    ),

    entry(
        databaseId = 160,
        kind = Kind.SYSTEM,
        section = "Church law",
        title = "Valentinian III's edict of 445 that gave the rulings of the Roman see a legal footing in the West",
        originalTitle = "Edictum Valentiniani III",
        author = "Valentinian III",
        year = 445,
        summary = "In 445 the western emperor Valentinian III issued an edict that made the decisions of the Roman bishop binding on the churches of the western provinces.",
        details = "The edict answered a quarrel between Leo and Hilary, bishop of Arles, and it gave the Roman see a legal footing across the West. Historians still debate how far it went beyond earlier imperial rulings, and 445 is the usual date given for it.",
        rare = true,
    ),

    entry(
        databaseId = 160,
        kind = Kind.SYSTEM,
        section = "Buddhism",
        title = "Emperor Taiwu's campaign of 446 against Buddhist monasteries and clergy in the Northern Wei",
        originalTitle = "Taiwu mie fo",
        author = "Emperor Taiwu of the Northern Wei",
        year = 446,
        summary = "The Northern Wei emperor Taiwu began in 446 a campaign against Buddhist monasteries and clergy, the first of the great persecutions of Buddhism in China.",
        details = "The campaign followed the discovery of weapons in temples and was aimed at the monks, their buildings and their books. Its formal end came in 452, when Taiwu's grandson Wencheng came to the throne, and accounts differ on the trigger, since some place the uprising behind it in 445.",
        rare = true,
    ),

    entry(
        databaseId = 160,
        kind = Kind.SYSTEM,
        section = "Federates",
        title = "The Burgundians settled in Sapaudia by the Roman general Aetius in 443",
        originalTitle = "Sapaudia",
        author = "Aetius",
        year = 443,
        summary = "In 443 the Roman general Aetius settled a Burgundian people in Sapaudia, the region of Savoy, as allies of the western empire.",
        details = "The settlement made the Burgundians federates inside the empire, and it gave them the base from which their kingdom later spread along the Rhone. The numbers settled and the exact limits of their land are not recorded in the usual accounts.",
        rare = true,
    ),

)
