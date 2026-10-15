package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1710 to 1720: Sunzi's remainder problem and a perspective line's vanishing point, each in Python and C. */
val db33Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 33,
        kind = Kind.CROSS,
        section = "Remainders",
        title = "Sunzi's remainder problem in Python and C",
        originalTitle = "Shuli jingyun",
        author = "Compiled at the Kangxi court",
        year = 1713,
        summary = "Sunzi's puzzle of three remainders has the answer 23, which a search in Python and a search in C both find, and a script checks that they agree.",
        details = "The Shuli jingyun was compiled in the Kangxi reign, and the dates given for it vary. The Python program searches for the smallest number with remainders 2, 3 and 2 on division by 3, 5 and 7, the C program does the same search, and the run script compares their printed results.",
        instructions = """
            1. Save the three parts of the example as sunzi.py, sunzi.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### sunzi.py
            # Sunzi's problem: a number that leaves remainders 2, 3 and 2 when divided by 3, 5 and 7.
            moduli = (3, 5, 7)
            remainders = (2, 3, 2)
            found = next(x for x in range(1, 106) if all(x % m == r for m, r in zip(moduli, remainders)))
            print(f"smallest number with remainders 2, 3, 2: {found}")
            ### sunzi.c
            #include <stdio.h>

            int main(void) {
                /* The least x with x mod 3 = 2, x mod 5 = 3 and x mod 7 = 2. */
                for (int x = 1; x <= 105; x++) {
                    if (x % 3 == 2 && x % 5 == 3 && x % 7 == 2) {
                        printf("smallest number with remainders 2, 3, 2: %d\n", x);
                        break;
                    }
                }
                return 0;
            }
            ### run.sh
            python3 sunzi.py > python.txt
            cc -o sunzi_c sunzi.c
            ./sunzi_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 33,
        kind = Kind.CROSS,
        section = "Geometry",
        title = "A perspective line's vanishing point in Python and C",
        originalTitle = "New Principles of Linear Perspective",
        author = "Brook Taylor",
        year = 1719,
        summary = "The projections of points along a line approach the line's vanishing point, which the program prints in Python and in C with a script checking that they agree.",
        details = "Taylor's treatise on linear perspective uses vanishing points, where parallel lines appear to meet on the picture. The Python program projects three points along a line and then the vanishing point, the C program does the same calculation, and the run script compares their printed results.",
        instructions = """
            1. Save the three parts of the example as perspective.py, perspective.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### perspective.py
            # Eye at the origin looking along +z, picture plane at distance d = 2; the line is A + t DIR.
            D = 2
            A = (1, 1, 2)
            DIR = (1, 2, 4)

            def project(p):
                x, y, z = p
                return (D * x / z, D * y / z)

            for t in (1, 10, 100):
                p = tuple(a + t * d for a, d in zip(A, DIR))
                u, v = project(p)
                print(f"t = {t}: ({u:.4f}, {v:.4f})")

            vx, vy = D * DIR[0] / DIR[2], D * DIR[1] / DIR[2]
            print(f"vanishing point: ({vx:.4f}, {vy:.4f})")
            ### perspective.c
            #include <stdio.h>

            int main(void) {
                /* Eye at the origin looking along +z, picture plane at distance d = 2; the line is A + t DIR. */
                double d = 2.0;
                double A[3] = {1, 1, 2};
                double dir[3] = {1, 2, 4};
                int ts[3] = {1, 10, 100};
                for (int k = 0; k < 3; k++) {
                    double x = A[0] + ts[k] * dir[0];
                    double y = A[1] + ts[k] * dir[1];
                    double z = A[2] + ts[k] * dir[2];
                    printf("t = %d: (%.4f, %.4f)\n", ts[k], d * x / z, d * y / z);
                }
                printf("vanishing point: (%.4f, %.4f)\n", d * dir[0] / dir[2], d * dir[1] / dir[2]);
                return 0;
            }
            ### run.sh
            python3 perspective.py > python.txt
            cc -o perspective_c perspective.c
            ./perspective_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
