package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1920 to 1930: the same tables and the same spanning tree, computed in Python and in C and compared by a run script. */
val db12Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.CROSS,
        section = "Python and C",
        title = "Lukasiewicz three-valued tables in Python and C",
        originalTitle = "Three-valued logic (Lukasiewicz, 1920)",
        author = "Written as a teaching example",
        year = 1920,
        summary = "The same three-valued truth tables are computed in Python and in C, and the two outputs are compared, as a conformance test does.",
        details = "Lukasiewicz's three-valued logic gives a sentence the value false, possible or true, and the implication is the smaller of 1 and 1 minus a plus b. The two programs share no code. They print the negation, conjunction, disjunction and implication tables with the values 0, 1 and 2, meaning false, possible and true, and the run script checks that the two outputs are identical.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### lukasiewicz.py
            # Lukasiewicz three-valued logic: 0 is false, 1 is possible (one half) and 2 is true, written as integers.
            def neg(a):
                return 2 - a

            def conj(a, b):
                return min(a, b)

            def disj(a, b):
                return max(a, b)

            def impl(a, b):
                return min(2, 2 - a + b)

            print("not:", " ".join(str(neg(a)) for a in range(3)))
            for name, f in [("and", conj), ("or", disj), ("implies", impl)]:
                print(name + ":")
                for a in range(3):
                    print(" ".join(str(f(a, b)) for b in range(3)))
            ### lukasiewicz.c
            #include <stdio.h>

            /* Lukasiewicz three-valued logic: 0 is false, 1 is possible and 2 is true, written as integers. */
            static int neg(int a) { return 2 - a; }
            static int conj(int a, int b) { return a < b ? a : b; }
            static int disj(int a, int b) { return a > b ? a : b; }
            static int impl(int a, int b) { int v = 2 - a + b; return v < 2 ? v : 2; }

            static void table(const char *name, int (*f)(int, int)) {
                printf("%s:\n", name);
                for (int a = 0; a < 3; a++) {
                    for (int b = 0; b < 3; b++) {
                        printf(b ? " %d" : "%d", f(a, b));
                    }
                    printf("\n");
                }
            }

            int main(void) {
                printf("not: %d %d %d\n", neg(0), neg(1), neg(2));
                table("and", conj);
                table("or", disj);
                table("implies", impl);
                return 0;
            }
            ### run.sh
            python3 lukasiewicz.py > python.txt
            cc -o lukasiewicz lukasiewicz.c
            ./lukasiewicz > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 12,
        kind = Kind.CROSS,
        section = "Python and C",
        title = "Boruvka's spanning tree in Python and C",
        originalTitle = "Minimum spanning trees by cheapest edges (Boruvka, 1926)",
        author = "Written as a teaching example",
        year = 1926,
        summary = "Boruvka's method grows a minimum spanning tree in rounds, each component taking its cheapest edge out, and the Python and C programs must print the same rounds.",
        details = "In each round every component picks the cheapest edge that leaves it, and the picked edges join the components until one component remains. The graph has five vertices and seven edges with distinct weights, so its spanning tree is unique. Both programs print each round and the total weight, 11, and the run script checks that the two outputs are identical.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### boruvka.py
            # Edges as (u, v, weight) on vertices 0 to 4. All weights differ, so the minimum spanning tree is unique.
            EDGES = [(0, 1, 4), (0, 2, 1), (1, 2, 2), (1, 3, 5), (2, 3, 8), (2, 4, 6), (3, 4, 3)]
            N = 5

            def run():
                parent = list(range(N))

                def find(x):
                    while parent[x] != x:
                        x = parent[x]
                    return x

                total = 0
                count = 0
                rnd = 0
                while True:
                    # Every component picks its cheapest edge that leaves it.
                    cheapest = {}
                    for idx, (u, v, w) in enumerate(EDGES):
                        cu, cv = find(u), find(v)
                        if cu == cv:
                            continue
                        for c in (cu, cv):
                            if c not in cheapest or w < EDGES[cheapest[c]][2]:
                                cheapest[c] = idx
                    if not cheapest:
                        break
                    rnd += 1
                    picked = []
                    for idx in sorted(set(cheapest.values())):
                        u, v, w = EDGES[idx]
                        if find(u) != find(v):
                            parent[find(u)] = find(v)
                            picked.append(f"{u}-{v} {w}")
                            total += w
                            count += 1
                    print(f"round {rnd}: " + ", ".join(picked))
                print(f"tree edges: {count}, total weight {total}")

            run()
            ### boruvka.c
            #include <stdio.h>

            /* Edges as (u, v, weight) on vertices 0 to 4. All weights differ, so the minimum spanning tree is unique. */
            static const int EU[7] = {0, 0, 1, 1, 2, 2, 3};
            static const int EV[7] = {1, 2, 2, 3, 3, 4, 4};
            static const int EW[7] = {4, 1, 2, 5, 8, 6, 3};
            #define NE 7
            #define N 5

            static int parent[N];

            static int find(int x) {
                while (parent[x] != x) {
                    x = parent[x];
                }
                return x;
            }

            int main(void) {
                int total = 0, count = 0, rnd = 0;
                for (int i = 0; i < N; i++) parent[i] = i;
                for (;;) {
                    int cheapest[N];
                    int picked_flag[NE] = {0};
                    int any = 0;
                    for (int i = 0; i < N; i++) cheapest[i] = -1;
                    for (int idx = 0; idx < NE; idx++) {
                        int cu = find(EU[idx]), cv = find(EV[idx]);
                        if (cu == cv) continue;
                        int cs[2] = {cu, cv};
                        for (int k = 0; k < 2; k++) {
                            int c = cs[k];
                            if (cheapest[c] < 0 || EW[idx] < EW[cheapest[c]]) cheapest[c] = idx;
                        }
                    }
                    for (int c = 0; c < N; c++) {
                        if (cheapest[c] >= 0) {
                            picked_flag[cheapest[c]] = 1;
                            any = 1;
                        }
                    }
                    if (!any) break;
                    rnd++;
                    printf("round %d: ", rnd);
                    int first = 1;
                    for (int idx = 0; idx < NE; idx++) {
                        if (!picked_flag[idx]) continue;
                        int ru = find(EU[idx]), rv = find(EV[idx]);
                        if (ru != rv) {
                            parent[ru] = rv;
                            printf(first ? "%d-%d %d" : ", %d-%d %d", EU[idx], EV[idx], EW[idx]);
                            first = 0;
                            total += EW[idx];
                            count++;
                        }
                    }
                    printf("\n");
                }
                printf("tree edges: %d, total weight %d\n", count, total);
                return 0;
            }
            ### run.sh
            python3 boruvka.py > python.txt
            cc -o boruvka boruvka.c
            ./boruvka > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),
)
