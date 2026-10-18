package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1140 to 1150: a council against a teacher, a Song and Jin peace, a Portuguese recognition, a German town refounded and an Almohad conquest. */
val db90Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 90,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Sens, which condemned Abelard's teaching in 1141",
        originalTitle = "Concilium Senonense",
        author = "Bernard of Clairvaux and the bishops of Sens",
        year = 1141,
        summary = "The Council of Sens met in 1141 and condemned Peter Abelard's theses at the urging of Bernard of Clairvaux, and the sentence was then confirmed from Rome.",
        details = "Abelard appealed to Rome after the council, and Pope Innocent II later confirmed the condemnation. Accounts differ on the exact days of the proceedings, so 1141 is the year given for the council.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.SYSTEM,
        section = "Diplomacy",
        title = "The Treaty of Shaoxing, the peace between the Song and the Jin",
        originalTitle = "Shaoxing He yi",
        author = "Emperor Gaozong of Song and the Jin court",
        year = 1142,
        summary = "The Treaty of Shaoxing made a peace in 1142 between the Southern Song and the Jin, fixing a northern border on the Huai River and a yearly payment to the Jin court.",
        details = "The negotiations began in 1141 and the peace was concluded in 1142, and accounts differ on which of the two years should stand for the treaty. The border and the tribute were the terms that lasted, and the Song never recovered the north.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.SYSTEM,
        section = "Kingdoms",
        title = "The Treaty of Zamora, which recognised the Portuguese king",
        originalTitle = "Tratado de Zamora",
        author = "Alfonso VII of Leon and Castile and Afonso Henriques",
        year = 1143,
        summary = "The Treaty of Zamora in 1143 made Afonso Henriques king of Portugal under Alfonso VII of Leon and Castile, a settlement that set the frontier between the two Iberian kingdoms.",
        details = "Afonso Henriques had taken the royal title in the 1130s, and the treaty ended the claim of his Leonese cousin to overlordship. The year 1143 is the usual date given for the agreement at Zamora.",
        rare = true,
    ),

    entry(
        databaseId = 90,
        kind = Kind.SYSTEM,
        section = "Towns",
        title = "The refounding of Lubeck and its grant of settlers' rights",
        originalTitle = "Lubecker Stadtgrundung",
        author = "Count Adolf II of Holstein",
        year = 1143,
        summary = "Count Adolf II of Holstein refounded the trading town of Lubeck in 1143, granting its settlers rights that became the basis of a town law reaching far along the Baltic coast.",
        details = "The refounding is usually dated to 1143, and the settlers were merchants and craftsmen drawn to the site by the count. The year is standard in the town's history, and the town law that grew from the settlers' rights was copied far beyond the town itself.",
    ),

    entry(
        databaseId = 90,
        kind = Kind.SYSTEM,
        section = "Empires",
        title = "The Almohad taking of Marrakesh and the rise of the Almohad caliphate",
        originalTitle = "Al-Muwahhidun",
        author = "Abd al-Mu'min",
        year = 1147,
        summary = "The Almohads took Marrakesh in 1147, ending Almoravid rule in Morocco, and their caliphate then governed North Africa and al-Andalus from the city.",
        details = "Abd al-Mu'min led the Almohad armies after the death of Ibn Tumart, and the taking of the Almoravid capital is dated 1147. The movement's doctrine of the unity of God gave the new state its religious law, and the caliphate kept its court at Marrakesh for most of the century.",
    ),

)
