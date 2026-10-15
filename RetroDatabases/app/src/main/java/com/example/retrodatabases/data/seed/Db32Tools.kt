package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1720 to 1730: a cyclopaedia, a type foundry, a textbook of Newtonian physics, a dental book, a book on sap pressure and a history of medicine. */
val db32Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Reference",
        title = "Chambers's Cyclopaedia of arts and sciences",
        originalTitle = "Cyclopaedia, or an Universal Dictionary of Arts and Sciences",
        author = "Ephraim Chambers",
        year = 1728,
        summary = "A two-volume reference work that arranged articles in alphabetical order and linked them with cross-references, and became the model for later encyclopedias.",
        details = "The first edition appeared in London in 1728 in two volumes. Diderot's French Encyclopedie was based on this work, which is why Chambers is named as its model.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Printing",
        title = "Caslon's types for English books",
        originalTitle = "Caslon's types",
        author = "William Caslon",
        year = 1720,
        summary = "A family of roman and italic types cut in London, which was widely used in English books for much of the century.",
        details = "William Caslon began his type foundry in London around 1720, and his faces replaced many imported types in English printing. The year given is the start of the foundry as it is usually dated.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Physics",
        title = "Gravesande's Newtonian physics with experiments",
        originalTitle = "Physices elementa mathematica, experimentis confirmata, sive introductio ad philosophiam Newtonianam",
        author = "Willem Jacob 's Gravesande",
        year = 1720,
        summary = "A textbook of Newtonian physics in which the laws are taught through demonstrations that a reader could repeat.",
        details = "Gravesande's book was published at Leiden in 1720. Its experiments, including a falling-ball apparatus, were used to teach Newtonian mechanics, and the book went through several editions.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Fauchard's dental surgery book",
        originalTitle = "Le Chirurgien Dentiste, ou, Traite des Dents",
        author = "Pierre Fauchard",
        year = 1728,
        summary = "A book on dental surgery that described fillings, dentures and the braces used to straighten crooked teeth.",
        details = "Fauchard's book of 1728 is one of the first full treatments of dentistry as a trade, and it is often cited for its early description of dental braces. Accounts of its exact contents differ between sources, so the record gives only its main subject.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Plant physiology",
        title = "Hales's Vegetable Staticks",
        originalTitle = "Vegetable Staticks",
        author = "Stephen Hales",
        year = 1727,
        summary = "A book that measured the pressure with which sap rises in plants, using tubes fixed to the stems of living plants.",
        details = "Hales published the book in 1727. His tube measurements of sap pressure were among the first quantitative studies of plant physiology.",
    ),

    entry(
        databaseId = 32,
        kind = Kind.TOOL,
        section = "Medicine",
        title = "Freind's History of Physick",
        originalTitle = "The History of Physick",
        author = "John Freind",
        year = 1725,
        summary = "A history of medicine from the ancient writers onward, begun in 1725 and often called one of the first full English histories of the subject.",
        details = "Freind's history is dated here to 1725, when the work begins. It was written in English, which is one reason it is often cited in histories of English medicine.",
    ),

)
