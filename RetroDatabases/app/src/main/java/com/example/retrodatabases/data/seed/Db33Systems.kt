package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1710 to 1720: the longitude reward, Law's bank, the Swedish calendar, Newton's guinea and the South Sea scheme. */
val db33Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.SYSTEM,
        section = "Navigation",
        title = "The Longitude Act of 1714",
        originalTitle = "An Act for providing a Reward for such Person or Persons as shall discover the Longitude at Sea",
        author = "Parliament of Great Britain",
        year = 1714,
        summary = "The Act set up a Board of Longitude and offered cash rewards to anyone who could find longitude at sea by a practical method.",
        details = "Modern accounts give the top reward as 20000 pounds, and the Board went on judging proposals for many decades after it was set up. The Act is the start of the long official search for a usable method of finding longitude at sea.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "Law's Banque generale",
        originalTitle = "Banque generale",
        author = "John Law",
        year = 1716,
        summary = "A Paris bank that issued paper notes payable in coin, and the first step of John Law's paper money scheme in France.",
        details = "Law opened the Banque generale in Paris in 1716, and its notes were payable in coin on demand. In 1718 the bank became a royal bank, and the paper that followed was tied to the Mississippi company, whose collapse in 1720 ended the scheme.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.SYSTEM,
        section = "Calendars",
        title = "Sweden's calendar experiment",
        originalTitle = "Svenska kalendern",
        author = "Swedish government",
        year = 1712,
        summary = "Sweden kept its own calendar from 1700 until 1712, when an extra day, 30 February, brought its dates back into step with the Julian calendar.",
        details = "From 1 March 1700 the Swedish calendar stood one day ahead of the Julian calendar, after its leap day of 1700 was left out, while the leap days of 1704 and 1708 were kept. A royal order of January 1711, issued from Bender, called for a return to Julian dates, and in 1712 an extra day, 30 February, was added to bring the two calendars back into step.",
        rare = true,
    ),

    entry(
        databaseId = 33,
        kind = Kind.SYSTEM,
        section = "Coinage",
        title = "Newton's guinea at twenty-one shillings",
        originalTitle = "Newton's guinea at twenty-one shillings",
        author = "Isaac Newton, Master of the Mint",
        year = 1717,
        summary = "Newton's report to the Treasury priced gold against silver, and a proclamation then fixed the guinea at twenty-one shillings.",
        details = "Newton's report of 21 September 1717 gave 44 and a half guineas from a troy pound of standard gold and 62 shillings from a troy pound of standard silver. A proclamation of 20 December 1717 fixed the guinea at 21 shillings, and Newton's minute blamed the rise of silver on a guinea valued at 21 shillings and sixpence. Accounts differ on how far this made Britain a gold standard country, since the change was made by royal proclamation.",
    ),

    entry(
        databaseId = 33,
        kind = Kind.SYSTEM,
        section = "Finance",
        title = "The South Sea scheme of 1720",
        originalTitle = "The South Sea Company's scheme",
        author = "South Sea Company directors",
        year = 1720,
        summary = "A company proposal to take over the national debt for its own stock, whose share price rose and then crashed in 1720.",
        details = "Parliament accepted the South Sea Company's offer in April 1720, and the shares rose through the summer before falling sharply in the autumn. A parliamentary inquiry of 1721 found bribery and corruption among those involved, and the affair is one of the best known financial bubbles.",
    ),
)
