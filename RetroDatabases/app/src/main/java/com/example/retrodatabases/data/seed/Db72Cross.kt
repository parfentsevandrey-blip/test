package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1320 to 1330: Florentine lire, soldi and denari added in Python and in C, and the interior angles of the Ely octagon in both languages. */
val db72Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.CROSS,
        section = "Money",
        title = "Florentine lire, soldi and denari added in Python and C",
        originalTitle = "Lire, soldi, denari",
        author = "Florentine merchants",
        year = 1321,
        summary = "Florentine accounts reckoned money in lire, soldi and denari, and a script checks that the Python and C programs add three pairs of sums the same way.",
        details = "A lira was twenty soldi and a soldo was twelve denari, so each sum carries at twelve and at twenty. The three sums are illustrative, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lire.py
            def add(first, second):
                denari = first[2] + second[2]
                soldi = first[1] + second[1] + denari // 12
                denari = denari % 12
                lire = first[0] + second[0] + soldi // 20
                soldi = soldi % 20
                return lire, soldi, denari

            PAIRS = [((3, 15, 7), (2, 9, 9)), ((0, 19, 11), (0, 0, 1)), ((10, 0, 0), (0, 12, 6))]
            for first, second in PAIRS:
                lire, soldi, denari = add(first, second)
                print(f"{lire} lire {soldi} soldi {denari} denari")
            ### lire.c
            #include <stdio.h>

            int main(void) {
                int pairs[3][2][3] = {
                    {{3, 15, 7}, {2, 9, 9}},
                    {{0, 19, 11}, {0, 0, 1}},
                    {{10, 0, 0}, {0, 12, 6}}
                };
                for (int i = 0; i < 3; i++) {
                    int denari = pairs[i][0][2] + pairs[i][1][2];
                    int soldi = pairs[i][0][1] + pairs[i][1][1] + denari / 12;
                    denari = denari % 12;
                    int lire = pairs[i][0][0] + pairs[i][1][0] + soldi / 20;
                    soldi = soldi % 20;
                    printf("%d lire %d soldi %d denari\n", lire, soldi, denari);
                }
                return 0;
            }
            ### run.sh
            python3 lire.py > python.txt
            cc -o lire_c lire.c
            ./lire_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.CROSS,
        section = "Geometry",
        title = "The interior angles of the Ely octagon in Python and C",
        originalTitle = "Octagon",
        author = "The masons of Ely",
        year = 1322,
        summary = "The interior angles of an octagon sum to 1080 degrees, and a script checks that the Python and C programs print the same sum and the same angle for each corner of a regular octagon.",
        details = "The octagon of Ely was built after the central tower fell in 1322, and the example applies the usual rule that a polygon of n sides has an angle sum of n minus two times 180 degrees. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### octagon.py
            sides = 8
            angle_sum = (sides - 2) * 180
            print("sides", sides)
            print("angle sum", angle_sum)
            print("each angle", angle_sum // sides)
            ### octagon.c
            #include <stdio.h>

            int main(void) {
                int sides = 8;
                int angle_sum = (sides - 2) * 180;
                printf("sides %d\n", sides);
                printf("angle sum %d\n", angle_sum);
                printf("each angle %d\n", angle_sum / sides);
                return 0;
            }
            ### run.sh
            python3 octagon.py > python.txt
            cc -o octagon_c octagon.c
            ./octagon_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
