package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1780 to 1790: crystal planes counted and earthworks matched, each in Python and C. */
val db26Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 26,
        kind = Kind.CROSS,
        section = "Crystallography",
        title = "Hauy's cube planes counted in Python and C",
        originalTitle = "Essai d'une theorie sur la structure des crystaux",
        author = "Rene-Just Hauy",
        year = 1784,
        summary = "The planes with indices from -1 to 1 are counted by their number of nonzero indices, once in Python and once in C, and a script checks that the results agree.",
        details = "Hauy's essay of 1784 counted crystal planes by their indices, and the same count can be made in two languages. The Python program groups the planes by how many indices are nonzero, the C program counts them with three nested loops, and the run script compares the two outputs.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### hauy.py
            from collections import Counter
            from itertools import product

            counts = Counter()
            for h in product((-1, 0, 1), repeat=3):
                if any(h):
                    counts[sum(1 for v in h if v)] += 1
            for k in sorted(counts):
                print(f"nonzero entries {k}: {counts[k]} planes")
            print("total: " + str(sum(counts.values())))
            ### hauy.c
            #include <stdio.h>

            int main(void) {
                /* count[k] is the number of planes with k nonzero indices, for k = 1, 2, 3. */
                int count[4] = {0, 0, 0, 0};
                for (int a = -1; a <= 1; a++) {
                    for (int b = -1; b <= 1; b++) {
                        for (int c = -1; c <= 1; c++) {
                            int nonzero = (a != 0) + (b != 0) + (c != 0);
                            if (nonzero > 0) {
                                count[nonzero]++;
                            }
                        }
                    }
                }
                int total = 0;
                for (int k = 1; k <= 3; k++) {
                    printf("nonzero entries %d: %d planes\n", k, count[k]);
                    total += count[k];
                }
                printf("total: %d\n", total);
                return 0;
            }
            ### run.sh
            python3 hauy.py > python.txt
            cc -o hauy_c hauy.c
            ./hauy_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 26,
        kind = Kind.CROSS,
        section = "Earthworks",
        title = "Monge's earthwork matching in Python and C",
        originalTitle = "Memoire sur la theorie des deblais et des remblais",
        author = "Gaspard Monge",
        year = 1781,
        summary = "Three piles and three holes on a line are matched by brute force in Python and in C, and a script checks that both find the in-order matching as the cheapest.",
        details = "Monge's memoir of 1781 on earthworks is solved here by brute force over all six matchings, once in Python and once in C. Both programs print the same best matching and the same in-order cost, and the run script compares their output.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### monge.py
            from itertools import permutations

            piles = [0, 2, 5]
            holes = [1, 3, 4]

            def cost(order):
                # The pile at piles[i] goes to the hole at holes[order[i]]; the cost is distance times volume.
                return sum(abs(piles[i] - holes[order[i]]) for i in range(3))

            best = min(permutations(range(3)), key=cost)
            print("best assignment: " + " ".join(str(x) for x in best) + " cost " + str(cost(best)))
            print("in-order matching cost: " + str(cost((0, 1, 2))))
            ### monge.c
            #include <stdio.h>
            #include <stdlib.h>

            static const int piles[3] = {0, 2, 5};
            static const int holes[3] = {1, 3, 4};

            static int cost(const int *order) {
                int total = 0;
                for (int i = 0; i < 3; i++) {
                    total += abs(piles[i] - holes[order[i]]);
                }
                return total;
            }

            int main(void) {
                /* The six permutations of 0, 1, 2 in lexicographic order. */
                static const int perms[6][3] = {
                    {0, 1, 2}, {0, 2, 1}, {1, 0, 2}, {1, 2, 0}, {2, 0, 1}, {2, 1, 0}
                };
                int best = 0;
                for (int p = 1; p < 6; p++) {
                    if (cost(perms[p]) < cost(perms[best])) {
                        best = p;
                    }
                }
                printf("best assignment: %d %d %d cost %d\n", perms[best][0], perms[best][1], perms[best][2], cost(perms[best]));
                printf("in-order matching cost: %d\n", cost(perms[0]));
                return 0;
            }
            ### run.sh
            python3 monge.py > python.txt
            cc -o monge_c monge.c
            ./monge_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
