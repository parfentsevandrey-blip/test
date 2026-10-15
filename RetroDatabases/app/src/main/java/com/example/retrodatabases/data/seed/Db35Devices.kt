package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1690 to 1700: Papin's piston engine for raising water and Winstanley's first lighthouse on the Eddystone rock. */
val db35Devices: List<RecordEntity> = listOf(


    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Lighthouses",
        title = "Winstanley's first Eddystone lighthouse",
        originalTitle = "The first Eddystone lighthouse",
        author = "Henry Winstanley",
        year = 1698,
        summary = "A lighthouse built by Henry Winstanley on the Eddystone rock off Plymouth, lit in 1698 and destroyed in the storm of 1703.",
        details = "Winstanley built the tower between 1696 and 1698. The Great Storm of November 1703 destroyed it, and six people died that night, Winstanley among them.",
        rare = true,
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Fire engines",
        title = "Van der Heyden's fire hose in his printed description of 1690",
        originalTitle = "Beschryving der nieuwlyks uitgevonden en geoctrojeerde slang-brand-spuiten",
        author = "Jan van der Heyden",
        year = 1690,
        summary = "Jan van der Heyden's Amsterdam book of 1690 described his leather fire hose and the hose engine that pumped water through it.",
        details = "The book was printed in Amsterdam with about nineteen plates, and his son Jan the younger is named as joint author. A trial of the hose pump took place in 1672, and the first deployment at a fire in Amsterdam followed in January 1673.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Engines",
        title = "Papin's gunpowder piston engine of 1690",
        originalTitle = "Nova methodus ad vires motrices validissimas parvo sumptu comparandas",
        author = "Denis Papin",
        year = 1690,
        summary = "In 1690 Papin described a cylinder with a piston in which gunpowder drove out the air, so that the outside air pushed the piston back down.",
        details = "The design was printed in the Leipzig journal Acta Eruditorum in 1690. The air left in the cylinder after each gunpowder charge pushed Papin toward steam, and the same paper goes on to his steam design.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Heat engines",
        title = "Amontons's fire-driven air wheel of 1699",
        originalTitle = "Memoires de l'Academie royale des sciences, 1699",
        author = "Guillaume Amontons",
        year = 1699,
        summary = "Amontons's 1699 machine was a wheel with water cells near its hub and air cells on its rim, which turned when a fire warmed the air and pushed water across.",
        details = "The memoir is dated 20 June 1699 and sits in the Academy's Memoires on pages 112 to 126. Accounts differ on the plumbing: one describes tubes carrying the water, while another places the air in spokes and a rim with valves.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Acoustics",
        title = "Loulie's sonometer for tuning strings",
        originalTitle = "Nouveau systeme de musique",
        author = "Etienne Loulie",
        year = 1698,
        summary = "The sonometer was a monochord-type tuning instrument, with a piece that pinned the string and a lever that released a plucking mechanism.",
        details = "About 1698, the year the instrument is first described in print in Loulie's Nouveau systeme, which is the reason for the approximate date. The Academy heard it on 4 July 1699, accounts differ on its origin, placing its making about 1694, and a former collaborator offered a rival design in 1701.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Arms",
        title = "The socket bayonet that replaced the plug bayonet",
        originalTitle = "Socket bayonet",
        author = "Attributed to Vauban",
        year = 1697,
        summary = "The socket bayonet fits over the muzzle, so a musket can be fired with its blade fixed, and by the 1690s it had largely replaced the plug type.",
        details = "About 1697, the date at which sockets became fairly common in French infantry after early trials, which is why the year is approximate. Accounts differ on the first socket designs: some give the 1670s and others the design credited to Vauban in the late 1680s.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Optics",
        title = "Hartsoeker's glass-bead microscope lens",
        originalTitle = "Essai de dioptrique",
        author = "Nicolaas Hartsoeker",
        year = 1694,
        summary = "A microscope lens made by softening a glass thread in a flame until it formed a small bead, a method linked to Hartsoeker's work of 1694.",
        details = "About 1694, the year of his Essai de dioptrique, which is the printed form behind the approximate date, since the method is not dated more exactly. Accounts differ on priority: Hartsoeker pressed the method as his own in letters to Huygens, while others credit Johann Hudde and Leeuwenhoek with it.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.DEVICE,
        section = "Astronomy",
        title = "Flamsteed's mural arc at Greenwich",
        originalTitle = "Mural arc",
        author = "John Flamsteed, with Abraham Sharp",
        year = 1690,
        summary = "A seven-foot mural arc fixed to the meridian wall at Greenwich, with which Flamsteed fixed the places of close to 3,000 stars.",
        details = "About 1690: the arc was mounted in 1689 and finished in 1690, and the sources differ in emphasis on which year marks the instrument. The original no longer exists, since Margaret Flamsteed had it removed from the Observatory in 1720.",
    ),

)
