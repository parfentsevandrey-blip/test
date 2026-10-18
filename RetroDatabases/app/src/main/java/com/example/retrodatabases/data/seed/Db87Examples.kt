package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1176: the judges that the Assize of Northampton sent out, counted circuit by circuit. */
val db87Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 87,
        kind = Kind.EXAMPLE,
        section = "Counts",
        title = "Judges sent out by the Assize of Northampton, six circuits of three judges",
        originalTitle = "Judges of the Assize",
        author = "Henry II's justices",
        year = 1176,
        summary = "The Assize of Northampton sent six groups of three judges to the country's six circuits, and a short program adds the judges up circuit by circuit.",
        details = "The sources give six circuits with three judges in each, so the program totals eighteen judges. The count is a plain multiplication, and the example checks it by adding one circuit at a time.",
        instructions = """
            1. Save the example as judges.py.
            2. Run it: python3 judges.py
        """,
        example = """
            circuits = 6
            judges_per_circuit = 3
            total = 0
            for circuit in range(1, circuits + 1):
                total += judges_per_circuit
                print("circuit", circuit, "judges so far", total)
            print("judges in all", total)
        """,
    ),

)
