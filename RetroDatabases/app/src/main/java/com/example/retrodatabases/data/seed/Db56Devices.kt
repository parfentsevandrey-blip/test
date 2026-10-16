package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1480 to 1490: a sea astrolabe, a stone pillar on the Congo, Leonardo's armoured car, a sounding lead, a pulley hoist, a printed pointing hand, counting rods and the composing stick. */
val db56Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The Portuguese sea astrolabe",
        originalTitle = "Astrolabio de mar",
        author = "Portuguese navigators at the court of Joao II",
        year = 1485,
        summary = "The Portuguese sea astrolabe was a metal instrument for taking the sun's altitude at sea, and it was used on the African voyages of the 1480s.",
        details = "Navigators used it to find latitude from the sun's altitude at noon, a reading that a land astrolabe made of wood or brass was hard to take on a moving ship. Accounts differ on the year when the metal sea astrolabe first went to sea, and the date used here is only one of those given.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The padrao stone at the mouth of the Congo",
        originalTitle = "Padrao",
        author = "Diogo Cao",
        year = 1482,
        summary = "Diogo Cao set up a stone pillar with the arms of Portugal at Shark Point in the Congo estuary in about 1482, and such pillars marked Portuguese claims along the African coast.",
        details = "The pillar recorded that the king of Portugal had taken the land, and Cao set up more than one on his southward voyages. Accounts give his arrival at the Congo estuary as about August 1482, while the stone at Cape Cross is dated in different sources to 1482, 1485 or 1486.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "War machines",
        title = "Leonardo's armoured car in his letter to Sforza",
        originalTitle = "Lettera a Ludovico Sforza",
        author = "Leonardo da Vinci",
        year = 1482,
        summary = "Leonardo's letter to Ludovico Sforza, usually dated about 1482, describes a covered armoured car with guns that could break into enemy ranks.",
        details = "The letter also lists bridges, siege engines and bombards, and Leonardo offered these skills to the Milanese court. The letter gives no date of its own, so accounts differ on the year it was sent, and the car was never built in his lifetime.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "The sounding lead and its knotted line",
        originalTitle = "Sounding line",
        author = "Mariners of the Atlantic voyages",
        year = 1480,
        summary = "A lead weight on a marked line was dropped over the side of a ship to find the depth of water, and the knots in the line showed how much was run out.",
        details = "The line was marked at fixed intervals so that the sailor could read the depth from the knots as the line ran out. The date given is approximate, since sounding leads were in use well before the 1480s.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Lifting machines",
        title = "Vitruvius's polyspaston, the pulley hoist",
        originalTitle = "De architectura, book ten",
        author = "Vitruvius",
        year = 1486,
        summary = "Vitruvius describes a polyspaston, a hoist of several pulleys that lifts a heavy weight with a small pull, in the tenth book of his treatise, printed at Rome in 1486.",
        details = "The hoist is one of the machines Vitruvius lists in his book on engines, and the 1486 printed copies carried the text without drawings. Accounts differ on how well the Roman arrangement of the pulleys can be rebuilt from his words alone.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Printed signs",
        title = "The printed pointing hand, the manicule",
        originalTitle = "Manicula",
        author = "Printers of the 1480s",
        year = 1480,
        summary = "A small printed hand with a pointing finger, called a manicule, marked a passage in early printed books, and it copied the hands that readers had drawn in manuscript margins.",
        details = "Manuscript readers drew such hands in the margins for centuries, and printers cut the sign as a block so that a page could point to a passage. Accounts differ on when the printed form first appeared, so the year given is approximate.",
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Calculation",
        title = "Chinese counting rods laid out in rows",
        originalTitle = "Suanchou",
        author = "Chinese mathematicians, anonymous",
        year = 1480,
        summary = "Chinese counting rods were laid out in place-value rows on a board, and by the 1480s they were the usual tool for calculation in China.",
        details = "The rods were set in columns for units, tens and so on, and the digits in alternate columns were written in different forms. The date given is approximate, and accounts differ on how early the place-value layout can be traced.",
        rare = true,
    ),

    entry(
        databaseId = 56,
        kind = Kind.DEVICE,
        section = "Printing",
        title = "The compositor's composing stick",
        originalTitle = "Composing stick",
        author = "Compositors of the printing shops",
        year = 1480,
        summary = "The composing stick, a small hand frame in which a compositor set type line by line, was the working tool of the printing shops of the 1480s.",
        details = "The compositor set each line of type into the stick from left to right and then moved the finished lines to a galley for proof. The stick was in use from the earliest printing shops, so the year given is approximate.",
    ),

)
