package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1350 to 1360: an English wage statute after the plague, the Golden Bull of the emperors, a Bohemian law code, the English wool staple and a French order of knights. */
val db69Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 69,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Statute of Labourers, wage limits after the Black Death",
        originalTitle = "Statute of Labourers",
        author = "Parliament of Edward III",
        year = 1351,
        summary = "The Statute of Labourers of 1351 held wages to their levels before the plague and punished workers who left their jobs to earn more.",
        details = "It followed an ordinance of 1349 and was passed at Westminster after the Black Death, when many labourers were scarce and asked for higher pay. The statute was written in French, the language of English law at the time.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.SYSTEM,
        section = "Holy Roman Empire",
        title = "The Golden Bull of Charles IV, the imperial electoral law",
        originalTitle = "Bulla Aurea",
        author = "Charles IV",
        year = 1356,
        summary = "The Golden Bull of 1356 set out how the German electors chose the Holy Roman emperor, naming seven electors and their rights.",
        details = "Charles IV issued it at the Diet of Nuremberg in January 1356 and promulgated it again at Metz in December 1356. It named the seven electors, required the election to be held at Frankfurt, and fixed a majority of the electors as the rule for choosing a king.",
    ),

    entry(
        databaseId = 69,
        kind = Kind.SYSTEM,
        section = "Bohemia",
        title = "The Majestas Carolina, Charles IV's law code for Bohemia",
        originalTitle = "Majestas Carolina",
        author = "Charles IV",
        year = 1355,
        summary = "The Majestas Carolina, a law code for the Bohemian lands issued by Charles IV in 1355, set out royal rights and rules for the courts.",
        details = "Historians differ on how far its provisions were put into force. The year 1355 is the date usually given for the code itself.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.SYSTEM,
        section = "England",
        title = "The Ordinance of the Staple, the fixed wool markets of England",
        originalTitle = "Ordinance of the Staple",
        author = "Edward III and Parliament",
        year = 1353,
        summary = "The Ordinance of the Staple of 1353 required English wool exports to pass through designated staple towns, where the customs on wool were collected.",
        details = "The staple system let the Crown collect the wool customs at known places, and the ordinance is usually dated to 1353. Calais became the chief staple later, and the system grew out of earlier regulations of the fourteenth century.",
        rare = true,
    ),

    entry(
        databaseId = 69,
        kind = Kind.SYSTEM,
        section = "France",
        title = "The Order of the Star, King John II's knightly fellowship",
        originalTitle = "Ordre de l'Etoile",
        author = "John II of France",
        year = 1351,
        summary = "The Order of the Star, a fellowship of knights founded by King John II of France, is usually dated to 1351.",
        details = "It was one of the first royal orders of chivalry in France, and it came a few years after the English Order of the Garter of the late 1340s. The order's statutes are known only in part, and its founding date is usually given as 1351.",
        rare = true,
    ),

)
