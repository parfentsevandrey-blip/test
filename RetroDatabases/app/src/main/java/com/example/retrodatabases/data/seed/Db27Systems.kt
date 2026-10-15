package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1770 to 1780: a library of four treasuries, standard artillery, a canal, a continental post and a royal society of medicine. */
val db27Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 27,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Siku Quanshu, the imperial library of the four treasuries",
        originalTitle = "Siku quanshu",
        author = "Imperial editorial commission under the Qianlong emperor",
        year = 1773,
        summary = "A state project to copy the books of China into one library, sorted by a four-part classification that shaped later catalogues.",
        details = "The project began in 1773 under the Qianlong emperor, and the copying and cataloguing ran for about a decade, with the manuscripts completed in 1782. Its four sections were the classics, history, philosophy and collected works, and that classification shaped Chinese library catalogues for a long time.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.SYSTEM,
        section = "Artillery",
        title = "Gribeauval's standard guns and carriages",
        originalTitle = "Systeme Gribeauval",
        author = "Jean-Baptiste de Gribeauval",
        year = 1776,
        summary = "A French artillery system that reduced the guns to a few standard calibres and carriages, so that parts could be shared between them.",
        details = "Gribeauval's reforms were introduced from the 1760s, and the regulations of 1776 are the usual date for the standard set of calibres and carriages. Accounts differ on how far interchangeable parts were achieved in practice, so the system is often described as a step toward standard parts rather than a finished one.",
        rare = true,
    ),

    entry(
        databaseId = 27,
        kind = Kind.SYSTEM,
        section = "Canals",
        title = "The Trent and Mersey canal",
        originalTitle = "Grand Trunk Canal",
        author = "James Brindley and Hugh Henshall",
        year = 1777,
        summary = "A canal across the English Midlands that joined the Trent to the Mersey and gave a water route from the Humber to Liverpool.",
        details = "Brindley began the canal in 1766, and Henshall carried on the work after Brindley died in 1772. The whole line was open by 1777, and the canal carried pottery and other goods between the Midlands and the ports. Accounts differ on the exact opening date of the full route.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.SYSTEM,
        section = "Postal service",
        title = "The continental post under Franklin",
        originalTitle = "Continental post",
        author = "Continental Congress",
        year = 1775,
        summary = "A postal service for the American colonies, set up by Congress in 1775 with Benjamin Franklin as its first postmaster general.",
        details = "Congress set up the service in July 1775, after the fighting at Lexington and Concord in April, and named Franklin its first postmaster general. Franklin had earlier served as joint postmaster for the British colonies in North America. The service linked the towns of the colonies by post roads and carried letters and newspapers between them.",
    ),

    entry(
        databaseId = 27,
        kind = Kind.SYSTEM,
        section = "Public health",
        title = "The Societe royale de medecine in Paris",
        originalTitle = "Societe royale de medecine",
        author = "Royal government of France",
        year = 1776,
        summary = "A royal society in Paris that gathered medical reports from across France and advised the government on public health.",
        details = "The society was founded by royal decree in 1776 and collected reports on diseases and epidemics from doctors in the provinces. The crown sought its advice on public health, and the Revolution suppressed the society in 1793.",
    ),

)
