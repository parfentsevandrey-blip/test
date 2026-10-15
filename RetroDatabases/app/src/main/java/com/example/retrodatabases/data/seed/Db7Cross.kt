package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1960 to 1970: a C program checked by Python, and a Bloom filter in C driven by awk. */
val db7Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.CROSS,
        section = "C and Python",
        title = "C and Python compute the same shortest paths",
        originalTitle = "Floyd–Warshall shortest paths (1962)",
        author = "Robert Floyd and Stephen Warshall, with a C program and a Python driver",
        year = 1962,
        summary = "A C program runs the Floyd–Warshall method for all shortest paths, and a Python script feeds it a graph, runs the same method itself and compares the answers.",
        details = "Floyd and Warshall published the method in 1962. The two implementations share nothing but the idea, so agreement is a useful check. Python passes the matrix to the C program through standard input and reads the answer back.",
        instructions = """
            1. Save the files below in one folder.
            2. Compile the C program: cc -o fw fw.c
            3. Run the comparison: python3 compare.py
        """,
        example = """
            ### fw.c
            #include <stdio.h>

            #define N 4
            #define INF 1000000

            /* Reads an N x N matrix of edge weights from stdin and prints all shortest distances. */
            int main(void) {
                int d[N][N];
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        if (scanf("%d", &d[i][j]) != 1) {
                            return 1;
                        }
                    }
                }
                for (int k = 0; k < N; k++) {
                    for (int i = 0; i < N; i++) {
                        for (int j = 0; j < N; j++) {
                            if (d[i][k] + d[k][j] < d[i][j]) {
                                d[i][j] = d[i][k] + d[k][j];
                            }
                        }
                    }
                }
                for (int i = 0; i < N; i++) {
                    for (int j = 0; j < N; j++) {
                        printf("%d ", d[i][j]);
                    }
                    printf("\n");
                }
                return 0;
            }
            ### compare.py
            import subprocess

            INF = 1000000
            graph = [
                [0, 3, INF, 7],
                [8, 0, 2, INF],
                [5, INF, 0, 1],
                [2, INF, INF, 0],
            ]

            def floyd_warshall(d):
                n = len(d)
                d = [row[:] for row in d]
                for k in range(n):
                    for i in range(n):
                        for j in range(n):
                            if d[i][k] + d[k][j] < d[i][j]:
                                d[i][j] = d[i][k] + d[k][j]
                return d

            text = "\n".join(" ".join(str(x) for x in row) for row in graph) + "\n"
            result = subprocess.run(["./fw"], input=text, capture_output=True, text=True, check=True)
            c_rows = [list(map(int, line.split())) for line in result.stdout.strip().split("\n")]
            py_rows = floyd_warshall(graph)
            print("C result:", c_rows)
            print("same:", c_rows == py_rows)
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.CROSS,
        section = "awk and C",
        title = "awk and C: a Bloom filter for word lists",
        originalTitle = "Space/time trade-offs in hash coding with allowable errors (Bloom, 1970)",
        author = "Burton Bloom; the awk pipeline is written for this database",
        year = 1970,
        summary = "A Bloom filter written in C answers maybe or no for each word, and awk counts the answers. A filter can say maybe for a word that is not in the set, but it never says no for one that is.",
        details = "Burton Bloom described the filter in 1970. It trades a small chance of false positives for much less memory than a full table of words. The shell script builds two small word lists, runs the C program and lets awk count the answers.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
            3. The output shows how many probes said maybe and how many said no.
        """,
        example = """
            ### bloom.c
            #include <stdio.h>

            #define BITS 4096
            static unsigned char filter[BITS / 8];

            static unsigned long hash(const char *s, unsigned long seed) {
                unsigned long h = seed;
                while (*s) {
                    h = h * 31 + (unsigned char)*s++;
                }
                return h % BITS;
            }

            static void set_bit(unsigned long i) {
                filter[i / 8] |= (unsigned char)(1u << (i % 8));
            }

            static int get_bit(unsigned long i) {
                return (filter[i / 8] >> (i % 8)) & 1;
            }

            int main(int argc, char **argv) {
                char word[256];
                if (argc != 3) {
                    return 1;
                }
                FILE *members = fopen(argv[1], "r");
                FILE *probes = fopen(argv[2], "r");
                if (members == NULL || probes == NULL) {
                    return 1;
                }
                while (fscanf(members, "%255s", word) == 1) {
                    set_bit(hash(word, 7));
                    set_bit(hash(word, 1000003));
                }
                while (fscanf(probes, "%255s", word) == 1) {
                    int maybe = get_bit(hash(word, 7)) && get_bit(hash(word, 1000003));
                    printf("%s %s\n", maybe ? "maybe" : "no", word);
                }
                fclose(members);
                fclose(probes);
                return 0;
            }
            ### run.sh
            printf 'apple\nbanana\ncherry\n' > members.txt
            printf 'apple\ngrape\nlemon\ncherry\nmango\nkiwi\n' > probes.txt
            cc -o bloom bloom.c
            ./bloom members.txt probes.txt | awk '{ count[$1]++ } END { for (word in count) print word, count[word] }' | sort
        """,
        rare = true,
    ),
)
