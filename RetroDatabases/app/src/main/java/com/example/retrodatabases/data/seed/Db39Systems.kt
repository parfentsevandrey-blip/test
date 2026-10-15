package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1650 to 1660: a Florentine academy of trials, a German society of naturalists, a Swedish bank, a Paris salon academy and the Protectorate's post. */
val db39Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Accademia del Cimento in Florence",
        originalTitle = "Accademia del Cimento",
        author = "Ferdinando II and Leopoldo de' Medici",
        year = 1657,
        summary = "The Florentine academy of trials was founded in 1657 to test claims by repeated experiment.",
        details = "The academy was set up by the Medici brothers Ferdinando II and Leopoldo, and its members built and tested instruments, including the sealed alcohol thermometer. Its one book, the Saggi of its experiments, is dated 1666 by one bibliography and 1667 by another.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "The Leopoldina, the German society of natural researchers",
        originalTitle = "Academia Naturae Curiosorum",
        author = "Four physicians of Schweinfurt",
        year = 1652,
        summary = "A group of physicians at Schweinfurt founded a society of natural researchers in 1652, and it later took its name from an emperor.",
        details = "The society was first called the Academia Naturae Curiosorum and was renamed the Leopoldina after Emperor Leopold I, who took it under his protection. It is one of the oldest academies of science still active, and it still bears the emperor's name.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "Stockholms Banco, the Swedish bank of 1656",
        originalTitle = "Stockholms Banco",
        author = "Johan Palmstruch",
        year = 1656,
        summary = "Palmstruch founded Stockholms Banco in 1656 to take deposits and make payments, a bank that later issued paper notes.",
        details = "The bank was a private venture with royal backing, and its deposits and payments were handled through written accounts rather than by coin alone. Its paper notes came later, and they are the part of its history that is best remembered.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.SYSTEM,
        section = "Academies",
        title = "Montmor's private academy of learned men in Paris",
        originalTitle = "Academie Montmor",
        author = "Habert de Montmor",
        year = 1657,
        summary = "Habert de Montmor gathered learned men at his Paris house from 1657, a private academy that met to discuss experiments and natural philosophy.",
        details = "The meetings were a forerunner of the Paris academy that the king set up in 1666, and several of their members later joined that body. Montmor's house, not a royal charter, gave the group its place of meeting.",
        rare = true,
    ),

    entry(
        databaseId = 39,
        kind = Kind.SYSTEM,
        section = "Post",
        title = "The Protectorate's state post of 1657",
        originalTitle = "The Protectorate's state post of 1657",
        author = "The Protectorate government",
        year = 1657,
        summary = "An act of 1657 made the post a state service, so that letters and orders moved across England by one public system.",
        details = "The service was run for the government and carried letters on set routes between the main towns. Its rates and routes are a useful record of how the state took charge of correspondence.",
    ),

)
