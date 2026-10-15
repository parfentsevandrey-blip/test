package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1530 to 1540: an Antwerp exchange, royal lecturers in Paris, an English court of monastic lands, a French law ordinance and an English act on the headship of the church. */
val db51Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 51,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Antwerp bourse, the exchange of 1531",
        originalTitle = "Beurs van Antwerpen",
        author = "The city of Antwerp",
        year = 1531,
        summary = "The Antwerp bourse, a hall for the exchange opened in 1531, gave merchants from many countries one place to deal in bills, money and goods.",
        details = "Traders from Italy, Spain, Portugal, England and the German towns met in the hall to settle bills of exchange and commodity deals. The opening in 1531 is the usual date for the building.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.SYSTEM,
        section = "Learning",
        title = "The royal lecturers of Paris, founded in 1530",
        originalTitle = "College des lecteurs royaux",
        author = "Francis I of France",
        year = 1530,
        summary = "Francis I founded the royal lecturers in Paris in 1530, a body of teachers in Hebrew, Greek and mathematics that grew into the College de France.",
        details = "The lecturers were paid by the crown and taught subjects that the older university had neglected, such as Hebrew and Greek. The body is the origin of the College de France.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.SYSTEM,
        section = "Administration",
        title = "The Court of Augmentations, the English court of monastic lands",
        originalTitle = "Court of the Augmentations of the Revenues of the King's Crown",
        author = "The English Parliament",
        year = 1536,
        summary = "The Court of Augmentations, set up in England in 1536, took charge of the lands and revenues of the dissolved monasteries for the crown.",
        details = "Parliament created the court in 1536, and it kept accounts of the crown's new income from the monastic estates. Its records are a source for the economic history of the period.",
        rare = true,
    ),

    entry(
        databaseId = 51,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Villers-Cotterets ordinance of 1539, French for the law",
        originalTitle = "Ordonnance de Villers-Cotterets",
        author = "Francis I of France",
        year = 1539,
        summary = "The ordinance of Villers-Cotterets, signed in August 1539, required French rather than Latin in the law courts and in official records of France.",
        details = "Francis I signed the ordinance at Villers-Cotterets in August 1539, and the date of 25 August is the one usually given. Its rule on French in legal acts is still cited as the start of French as the language of French law.",
    ),

    entry(
        databaseId = 51,
        kind = Kind.SYSTEM,
        section = "Church and crown",
        title = "The Act of Supremacy of 1534, the king as head of the church",
        originalTitle = "Act of Supremacy",
        author = "The English Parliament",
        year = 1534,
        summary = "Parliament's Act of Supremacy of 1534 made Henry VIII the supreme head of the church in England and ended the English church's obedience to the pope.",
        details = "The act was passed by Parliament in 1534, and its royal assent is usually dated to November of that year. Later Tudor statutes confirmed its principle.",
    ),

)
