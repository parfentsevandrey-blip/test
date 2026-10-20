package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 391 to 397: Theodosius I's decrees against pagan sacrifice, the division of the empire in 395 and the Council of Carthage of 397. */
val db165Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 165,
        kind = Kind.SYSTEM,
        section = "Religion",
        title = "Theodosius I's decrees against pagan sacrifice, from 391 to 392",
        originalTitle = "Codex Theodosianus 16.10",
        author = "Theodosius I",
        year = 391,
        summary = "Theodosius I issued decrees in 391 and 392 that banned blood sacrifice and entry to temples, and the decrees of 392 widened the ban to private worship and to incense and wine offerings.",
        details = "The first rescript is dated 24 February 391 and went to the prefect of Rome, though one account dates the Roman measure to June 391. A decree of 392 forbade pagan worship in general, reaching private rites, and a November measure of the same year, addressed to the praetorian prefect of the East, Rufinus, is said to have added incense and wine offerings with property confiscation as the penalty.",
        rare = true,
    ),

    entry(
        databaseId = 165,
        kind = Kind.SYSTEM,
        section = "Empire",
        title = "The division of the empire between Arcadius and Honorius at Theodosius's death",
        originalTitle = "The division of the empire",
        author = "Theodosius I, Arcadius and Honorius",
        year = 395,
        summary = "When Theodosius I died at Milan in January 395, his two sons received the two halves of the empire, Arcadius in the east and Honorius in the west, and the halves were afterwards ruled apart.",
        details = "Theodosius died on 17 January 395, leaving a youth in the east and a boy in the west, and the western court was then placed under the guardianship of Stilicho. The arrangement is dated to 395 and is usually counted as the start of separate eastern and western courts, though the empire had been divided between rulers before.",
    ),

    entry(
        databaseId = 165,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Council of Carthage of 397, which gave a list of the books of scripture",
        originalTitle = "Concilium Carthaginense III",
        author = "North African bishops at Carthage",
        year = 397,
        summary = "A council of North African bishops at Carthage in 397 gave a list of the books to be read as scripture, and the list survives among its canons.",
        details = "The council is usually dated to 397, and its canons include a list of the books of the Old and New Testament. The record gives only the year of the council.",
    ),

)
