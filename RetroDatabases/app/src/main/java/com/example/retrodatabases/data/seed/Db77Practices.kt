package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1274 to 1279: the 1279 recoinage, the Hundred Rolls, the Lyons tenth, the Paris condemnation of 1277 and the quo warranto inquiries. */
val db77Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.PRACTICE,
        section = "Coinage",
        title = "The 1279 recoinage of English pennies",
        originalTitle = "Edwardian recoinage",
        author = "The English Crown under Edward I",
        year = 1279,
        summary = "In 1279 Edward I called in the clipped silver pennies of earlier reigns and issued new pennies of full weight, with the king's head on one side and a long cross on the other.",
        details = "The pound of account was 240 pence, but accounts differ on the number of new pennies struck from a Tower pound of 5,400 troy grains. The reform also brought in a groat, a halfpenny and a farthing, and the long arms of the cross were meant to deter clipping.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.PRACTICE,
        section = "Royal rights",
        title = "The Hundred Rolls, Edward I's inquiry into royal rights from 1274",
        originalTitle = "Rotuli Hundredorum",
        author = "Edward I and his commissioners",
        year = 1274,
        summary = "In 1274 Edward I ordered an inquiry into royal rights and the abuses of local officials, and the answers, given hundred by hundred, were copied into the Hundred Rolls.",
        details = "The commissioners asked each hundred of the shires about franchises, royal rights and the conduct of officials, and the returns survive in the rolls. The answers were gathered and copied over several years rather than in one act, and accounts differ on how far the inquiry led to change.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.PRACTICE,
        section = "Taxes",
        title = "The Lyons tenth, a six-year crusade levy on church revenue",
        originalTitle = "Lyons tenth",
        author = "Pope Gregory X and the Council of Lyon",
        year = 1274,
        summary = "The Council of Lyon voted in 1274 a tenth of church revenue for six years to pay for a crusade, and the levy was collected in the kingdoms of western Europe during the later 1270s.",
        details = "The levy was meant to pay for a crusade to the Holy Land, and it fell on the clergy as a general tax. Collection varied from kingdom to kingdom.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "The Paris condemnation of 219 propositions in March 1277",
        originalTitle = "Articuli condemnati",
        author = "Stephen Tempier, bishop of Paris",
        year = 1277,
        summary = "In March 1277 Bishop Stephen Tempier of Paris condemned a list of 219 propositions, teachings thought to threaten Christian doctrine.",
        details = "The bishop issued the list to the university, and it struck at teachings linked with Aristotle and the Arabic commentators. Accounts differ on how far the condemnation stopped such teaching in the arts faculty.",
    ),

    entry(
        databaseId = 77,
        kind = Kind.PRACTICE,
        section = "Royal rights",
        title = "Edward I's quo warranto inquiries of 1278 into lordly franchises",
        originalTitle = "Quo warranto",
        author = "Edward I and his justices",
        year = 1278,
        summary = "From 1278 Edward I sent justices round the counties to ask lords by what warrant they held their franchises and liberties.",
        details = "The demand for a warrant led to long disputes with the baronage, and accounts differ on how far the inquiries recovered rights for the crown. The proceedings ran on for many years after the first commissions.",
    ),

)
