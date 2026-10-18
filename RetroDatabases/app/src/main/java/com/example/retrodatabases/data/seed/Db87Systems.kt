package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1170 to 1176: an English circuit justice, an Ayyubid sultanate, a Korean military regime, a Spanish order and a Paris cathedral school. */
val db87Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.SYSTEM,
        section = "Royal justice",
        title = "The Assize of Northampton of 1176, which sent judges out on six circuits",
        originalTitle = "Assize of Northampton",
        author = "Henry II of England",
        year = 1176,
        summary = "The Assize of Northampton was a royal ordinance of January 1176 that sent six groups of three judges round the country's six circuits.",
        details = "The ordinance is usually dated to January 1176, when the king's council met at Northampton. It also made sheriffs and bailiffs account for the gains they made from office and gave them power to detain thieves.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.SYSTEM,
        section = "Islamic states",
        title = "The Ayyubid sultanate of Egypt, founded when Saladin ended the Fatimid caliphate in 1171",
        originalTitle = "Ayyubid sultanate",
        author = "Saladin",
        year = 1171,
        summary = "The Ayyubid sultanate of Egypt began in 1171, when Saladin ended the Fatimid caliphate and ruled Egypt on behalf of Nur al-Din.",
        details = "Saladin governed Egypt in the name of Nur al-Din until that ruler died in 1174, after which Saladin ruled in his own right. The name Ayyubid comes from Saladin's father, Ayyub.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.SYSTEM,
        section = "Military rule",
        title = "The military regime of Goryeo, begun by the coup of 1170",
        originalTitle = "Goryeo military regime",
        author = "Generals of the Goryeo court",
        year = 1170,
        summary = "The coup of 1170 gave Goryeo's generals power over the court, and their military regime lasted until 1270.",
        details = "The generals removed King Uijong from the throne and put his brother Myeongjong in his place, and the regime that followed governed Goryeo for about a century. Accounts differ on how much real power the kings kept, so the name military regime describes the generals' control rather than the form of the court.",
        rare = true,
    ),

    entry(
        databaseId = 87,
        kind = Kind.SYSTEM,
        section = "Religious orders",
        title = "The Military Order of Santiago, founded in Leon about 1170 to fight on the frontier",
        originalTitle = "Orden de Santiago",
        author = "Knights of the kingdom of Leon",
        year = 1170,
        summary = "The Military Order of Santiago was a religious order of knights founded about 1170 to fight on the frontier of Christian Spain.",
        details = "It took its name from the apostle James, whose shrine at Santiago de Compostela drew pilgrims from across Europe. Pope Alexander III approved the order in 1175, and its knights later held castles across the Iberian frontier.",
    ),

    entry(
        databaseId = 87,
        kind = Kind.SYSTEM,
        section = "Schools",
        title = "The cathedral school of Notre-Dame in Paris, a teaching centre of the 1170s",
        originalTitle = "Ecole de Notre-Dame",
        author = "Masters of the Paris cathedral",
        year = 1170,
        summary = "The cathedral school of Notre-Dame in Paris was a teaching centre whose masters taught theology and the arts, and it came before the university of Paris.",
        details = "The Historia scholastica is tied to this school in the sources, and Leonin, the composer of two-part organum, worked in its circle in the later twelfth century. Its records of the 1170s are few, so most of what is known comes from the work of its masters.",
        rare = true,
    ),

)
