package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 610 to 620: Heraclius's accession in 610, the battle of Salsu in 612 and the Sasanian hold on Alexandria by 619. */
val db143Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.SYSTEM,
        section = "Byzantine",
        title = "Heraclius overthrows the emperor Phocas in October 610 and founds the Heraclian dynasty of Byzantium",
        originalTitle = "Herakleios",
        author = "Heraclius",
        year = 610,
        summary = "Heraclius, son of the exarch of Africa, overthrew Phocas in October 610 and began the Heraclian dynasty, which ruled Byzantium for about a century.",
        details = "He sailed from Carthage with a fleet and was acclaimed at Constantinople, where Phocas was killed, and 610 is the usual date of his accession. His long reign was taken up first with the war against the Sasanian Persians, which ended in 628, and then with the Arab conquests that followed.",
    ),

    entry(
        databaseId = 143,
        kind = Kind.SYSTEM,
        section = "Persian",
        title = "The Sasanian conquest of Egypt, with Persian forces holding Alexandria by 619",
        originalTitle = "Sasanian conquest of Egypt",
        author = "Khosrow II and Shahrbaraz",
        year = 619,
        summary = "Persian forces under Khosrow II and his general Shahrbaraz held Alexandria by 619 and took control of Egypt, whose grain had fed Constantinople.",
        details = "Khosrow sent Shahrbaraz toward Egypt in 618, and accounts differ on the span of the conquest: one dates it from 618 to 621, while another gives 619 to 629 as the years of Persian rule. The loss of Egypt cut the grain shipments to Constantinople in the early years of Heraclius.",
    ),

    entry(
        databaseId = 143,
        kind = Kind.SYSTEM,
        section = "Korean",
        title = "The battle of Salsu in 612, when the Goguryeo army broke the first great Sui invasion",
        originalTitle = "Salsu",
        author = "Eulji Mundeok",
        year = 612,
        summary = "The Goguryeo army under Eulji Mundeok defeated the Sui army at the Salsu River in 612, ending the first great Sui invasion of Goguryeo.",
        details = "The Sui army of Emperor Yang advanced toward Pyongyang, and Eulji Mundeok drew it into a long retreat before breaking it as it crossed the Salsu River. Accounts differ on the size of the Sui army.",
        rare = true,
    ),

)
