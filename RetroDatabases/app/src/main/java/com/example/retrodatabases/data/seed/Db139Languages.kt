package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 650 to 660: the standard Qur'an text of about 650, the Five Classics commentaries of 653 and Sun Simiao's prescriptions of about 652. */
val db139Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 139,
        kind = Kind.LANGUAGE,
        section = "Arabic",
        title = "The standard Qur'an text of Uthman's committee, fixed about 650 from the manuscript of Hafsa",
        originalTitle = "al-Mushaf",
        author = "Uthman ibn Affan and the committee under Zayd ibn Thabit",
        year = 650,
        summary = "Uthman's committee fixed one written text of the Qur'an about 650, working from the manuscript kept by Hafsa, and copies went out to the main cities.",
        details = "The traditional account puts the work at roughly 650, with Zayd ibn Thabit at the head of the committee, but accounts differ on how many copies went out and to which cities. Scholars still debate whether the standard text carried one mode of recitation or all seven ahruf, and early manuscripts show variants, so the year is a broad date and not a sharp one.",
    ),

    entry(
        databaseId = 139,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "The Five Classics with their correct meanings, the standard commentaries promulgated by the Tang court in 653",
        originalTitle = "Wujing zhengyi",
        author = "Kong Yingda and colleagues",
        year = 653,
        summary = "The Wujing zhengyi, the standard commentaries on the Five Classics, was promulgated by the Tang court in 653 and set as the standard for the state examinations.",
        details = "Work on the commentaries began under Taizong, and the sources date their completion differently, so the year used here is the imperial promulgation of 653. The commentaries fixed one reading of each classic for the schools and examinations of the Tang.",
        rare = true,
    ),

    entry(
        databaseId = 139,
        kind = Kind.LANGUAGE,
        section = "Chinese",
        title = "Sun Simiao's Essential Prescriptions Worth a Thousand Gold, a medical compendium completed about 652",
        originalTitle = "Beiji qianjin yaofang",
        author = "Sun Simiao",
        year = 652,
        summary = "Sun Simiao's Beiji qianjin yaofang, a large compendium of prescriptions and remedies, was completed about 652 and became a standard medical reference.",
        details = "Sun Simiao, a physician of the Sui and Tang periods, arranged the remedies by subject, and the work was revised in later years. Its completion is commonly dated about 652, so 652 is the year of the first completed form and not the year of any later edition.",
        rare = true,
    ),

)
