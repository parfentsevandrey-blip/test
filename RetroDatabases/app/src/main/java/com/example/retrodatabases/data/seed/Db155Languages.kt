package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 490 to 500: an Indian astronomical treatise in verse, a papal letter on the two powers, and Georgian inscriptions dated by a king's regnal years. */
val db155Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 155,
        kind = Kind.LANGUAGE,
        section = "Astronomy",
        title = "Aryabhata's Aryabhatiya, an astronomical treatise in verse of 499",
        originalTitle = "Aryabhatiya",
        author = "Aryabhata",
        year = 499,
        summary = "The Aryabhatiya is a short Sanskrit treatise in verse on arithmetic, algebra, time and astronomy, dated to 499 by its own statement about the Kali age.",
        details = "The text gives its date as 3600 years of the Kali age, which Kali-age arithmetic places at the end of 21 March 499 CE. Accounts differ, and one historian of astronomy argues for a date nearer 510 from historical references in the text.",
    ),

    entry(
        databaseId = 155,
        kind = Kind.LANGUAGE,
        section = "Letters",
        title = "Gelasius I's letter to the emperor Anastasius on the two powers, of 494",
        originalTitle = "Famuli vestrae pietatis, known as Duo sunt",
        author = "Pope Gelasius I",
        year = 494,
        summary = "In 494 Pope Gelasius I wrote to the emperor Anastasius that the sacred authority of bishops and the royal power are the two powers that rule the world.",
        details = "The letter is known by its opening words Duo sunt, and its standard date is 494. Accounts differ on whether it claims supremacy for the clergy or two autonomous spheres of power, since it also grants that the emperor governs people legitimately.",
        rare = true,
    ),

    entry(
        databaseId = 155,
        kind = Kind.LANGUAGE,
        section = "Writing",
        title = "The Bolnisi Sioni inscriptions, Georgian writing dated 493 or 494",
        originalTitle = "Bolnisi Sioni inscriptions",
        author = "Unnamed stonecutters of Bolnisi Sioni",
        year = 494,
        summary = "The Bolnisi Sioni inscriptions are Georgian texts in asomtavruli capitals cut on the facade of a church, dated by their own year count to 494.",
        details = "Modern accounts convert the date to 493 or 494, because the reckoning rests on the regnal years of King Peroz. One account calls these the oldest dated inscriptions on Georgian soil.",
        rare = true,
    ),

)
