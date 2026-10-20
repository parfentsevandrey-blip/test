package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 430 to 434: a Latin commentary with its zones, Cyril's anathemas, a Pali handbook, Vincent's rule of belief and the Formula of Reunion. */
val db161Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 161,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Macrobius's Commentary on the Dream of Scipio, with its five zones of the Earth",
        originalTitle = "Commentarii in Somnium Scipionis",
        author = "Macrobius",
        year = 430,
        summary = "Macrobius wrote his Latin Commentary on the Dream of Scipio about 430, and in it he describes the five zones of the Earth.",
        details = "The commentary has two books and explains the dream that Cicero gives to Scipio in his Republic, and its second book sets out the torrid zone, the two temperate zones and the two frigid zones. Many medieval copies carry a diagram of the zones. Scholars differ on the exact date of the work, and this record uses 430 as its year.",
    ),

    entry(
        databaseId = 161,
        kind = Kind.LANGUAGE,
        section = "Greek",
        title = "Cyril of Alexandria's twelve anathemas against Nestorius, sent in 430",
        originalTitle = "Anathematismoi",
        author = "Cyril of Alexandria",
        year = 430,
        summary = "In 430 Cyril of Alexandria sent Nestorius a letter with twelve anathemas, statements that pronounce a curse on anyone who denies them.",
        details = "Each of the twelve anathemas states a point of Christology and ends by cursing anyone who holds the opposite view. Nestorius answered them in writing, and the quarrel over the anathemas was part of what led to the Council of Ephesus in 431.",
        rare = true,
    ),

    entry(
        databaseId = 161,
        kind = Kind.LANGUAGE,
        section = "Pali",
        title = "Buddhaghosa's Path of Purification, a Pali handbook of the Buddhist training",
        originalTitle = "Visuddhimagga",
        author = "Buddhaghosa",
        year = 430,
        summary = "Buddhaghosa's Visuddhimagga, the Path of Purification, is a Pali handbook that sets out the Buddhist training in virtue, concentration and wisdom.",
        details = "The handbook is arranged in twenty-three chapters around the seven purities, and it was written in Sri Lanka from older Sinhalese commentaries. Scholars place it in the fifth century, and this record uses 430, a round year that not every account gives, so the exact date is not settled.",
    ),

    entry(
        databaseId = 161,
        kind = Kind.LANGUAGE,
        section = "Latin",
        title = "Vincent of Lerins's Commonitorium, a rule of belief held everywhere, always and by all",
        originalTitle = "Commonitorium",
        author = "Vincent of Lerins",
        year = 434,
        summary = "Vincent of Lerins wrote his Commonitorium in 434, a Latin notebook that gives the test of what has been believed everywhere, always and by all.",
        details = "Vincent wrote under the pseudonym Peregrinus, and only the first of his two notebooks survives. His three tests of universality, antiquity and consent became a standard rule for judging doctrine in the Latin West.",
        rare = true,
    ),

    entry(
        databaseId = 161,
        kind = Kind.LANGUAGE,
        section = "Creed",
        title = "The Formula of Reunion of 433, a creed between Cyril and John of Antioch",
        originalTitle = "Formula of Reunion",
        author = "Cyril of Alexandria and John of Antioch",
        year = 433,
        summary = "The Formula of Reunion of 433 is a creed agreed between Cyril of Alexandria and John of Antioch to end their quarrel after the Council of Ephesus.",
        details = "The formula describes the union of the divine and human natures in Christ in words that both sides could accept, and Cyril accepted it in a letter of 433. It was a compromise, and the dispute over Nestorius did not end with it.",
        rare = true,
    ),

)
