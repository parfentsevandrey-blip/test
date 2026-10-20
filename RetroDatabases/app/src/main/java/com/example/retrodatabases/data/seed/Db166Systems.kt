package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 380 to 390: the Edict of Thessalonica, the councils of Constantinople and Aquileia, Gratian's removal of the Altar of Victory and the execution of Priscillian. */
val db166Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 166,
        kind = Kind.SYSTEM,
        section = "Law",
        title = "The Edict of Thessalonica, which made the Nicene faith the state religion",
        originalTitle = "Cunctos populos",
        author = "Gratian, Valentinian II and Theodosius I",
        year = 380,
        summary = "The Edict of Thessalonica, issued in the names of three emperors, made the Nicene Christian faith the only lawful religion of the Roman state.",
        details = "The edict is dated 27 February 380 in the usual reckoning, though one account gives 28 February, and it was issued at Thessalonica in the names of Gratian, Valentinian II and Theodosius I. It named the faith of Pope Damasus and Peter of Alexandria, which historians call the Nicene faith, as the Catholic faith, and it called other Christians heretics who could not use the name of church for their meetings. Its opening words give the decree its usual Latin name.",
    ),

    entry(
        databaseId = 166,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The First Council of Constantinople, which met in 381 and reaffirmed the Nicene faith",
        originalTitle = "Concilium Constantinopolitanum I",
        author = "Theodosius I and the bishops of the East",
        year = 381,
        summary = "The First Council of Constantinople, summoned by Theodosius in 381, reaffirmed the Nicene faith and issued seven canons on the order of the church.",
        details = "By tradition about 150 bishops attended, and the seven canons include one that gave Constantinople the honour next after Rome. The creed that bears the council's name is usually linked with it, but its usual wording is known from the proceedings of the council of Chalcedon in 451, so accounts differ on how much of it belongs to 381.",
    ),

    entry(
        databaseId = 166,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The council of Aquileia of 381, which Ambrose held against two Arian bishops",
        originalTitle = "Concilium Aquileiense",
        author = "Ambrose of Milan and the western bishops",
        year = 381,
        summary = "The council of Aquileia, held in 381 under Ambrose of Milan, deposed two Arian bishops from the Danube provinces.",
        details = "Ambrose presided over the western bishops who met at Aquileia, and their sentence removed Palladius of Ratiaria and Secundianus of Singidunum from their sees. The council is dated to 381, the same year as the council of Constantinople in the east, and it shows how the western church dealt with Arian bishops within its own provinces.",
        rare = true,
    ),

    entry(
        databaseId = 166,
        kind = Kind.SYSTEM,
        section = "Religion",
        title = "Gratian's removal of the Altar of Victory from the Senate House at Rome",
        originalTitle = "Ara Victoriae",
        author = "Gratian",
        year = 382,
        summary = "In 382 Gratian had the Altar of Victory taken out of the Senate House at Rome, and the state took over the revenues that supported it.",
        details = "A senatorial delegation led by Symmachus asked for the altar's return, and the emperor declined to hear it. A second appeal of 384, aimed at Valentinian II, also failed, and Ambrose of Milan opposed the pagan case in his letters. The removal is usually dated to 382.",
    ),

    entry(
        databaseId = 166,
        kind = Kind.SYSTEM,
        section = "Religion",
        title = "The execution of Priscillian at Trier, often called the first execution for heresy by a Roman court",
        originalTitle = "Supplicium Priscilliani",
        author = "Magnus Maximus and the bishop Ithacius",
        year = 385,
        summary = "Priscillian, a bishop of Avila, and some of his followers were executed at Trier in 385 after a court under Magnus Maximus convicted them of heresy.",
        details = "Priscillian taught an ascetic form of Christianity in Spain, and his execution is usually dated to 385 at Trier, where Magnus Maximus then ruled the western provinces. Accounts differ on the charges and on how far the bishops who denounced him were responsible for the sentence.",
        rare = true,
    ),

)
