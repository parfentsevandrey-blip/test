package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1550 to 1560: Roman numerals for the decade and a darkened room's image, each in Python and C. */
val db49Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 49,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Cardano's grille in Python and C",
        originalTitle = "De subtilitate",
        author = "Girolamo Cardano",
        year = 1550,
        summary = "A Python program and a C program turn Cardano's grille through four positions over the same sixteen letters, and a script checks that both give the same ciphertext.",
        details = "Cardano's grille of 1550 reads a square of text through a card of holes turned to four positions. The two programs use the same four holes and the same message, and the run script compares their outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### grille.py
            message = "SECRETMESSAGEXYZ"
            grid = [list(message[row * 4:row * 4 + 4]) for row in range(4)]
            holes = [(0, 0), (0, 1), (0, 2), (1, 1)]
            cipher = ""
            for turn in range(4):
                cells = sorted(holes)
                text = "".join(grid[r][c] for r, c in cells)
                print(f"turn {turn}: {text}")
                cipher += text
                holes = [(c, 3 - r) for r, c in holes]
            print("cipher:", cipher)
            ### grille.c
            #include <stdio.h>

            int main(void) {
                const char *message = "SECRETMESSAGEXYZ";
                int hr[4] = {0, 0, 0, 1};
                int hc[4] = {0, 1, 2, 1};
                char cipher[17];
                int used = 0;
                for (int turn = 0; turn < 4; turn++) {
                    int order[4] = {0, 1, 2, 3};
                    for (int i = 0; i < 4; i++) {
                        for (int j = i + 1; j < 4; j++) {
                            if (hr[order[j]] * 4 + hc[order[j]] < hr[order[i]] * 4 + hc[order[i]]) {
                                int t = order[i];
                                order[i] = order[j];
                                order[j] = t;
                            }
                        }
                    }
                    printf("turn %d: ", turn);
                    for (int k = 0; k < 4; k++) {
                        int idx = hr[order[k]] * 4 + hc[order[k]];
                        putchar(message[idx]);
                        cipher[used++] = message[idx];
                    }
                    putchar('\n');
                    for (int k = 0; k < 4; k++) {
                        int r = hr[k], c = hc[k];
                        hr[k] = c;
                        hc[k] = 3 - r;
                    }
                }
                cipher[used] = '\0';
                printf("cipher: %s\n", cipher);
                return 0;
            }
            ### run.sh
            python3 grille.py > python.txt
            cc -o grille_c grille.c
            ./grille_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 49,
        kind = Kind.CROSS,
        section = "Citation",
        title = "Chapter and verse addresses in Python and C",
        originalTitle = "Novum Testamentum graece",
        author = "Robert Estienne",
        year = 1551,
        summary = "A Python program and a C program turn chapter and verse addresses into running positions in an illustrative book, and a script confirms that both print the same positions.",
        details = "Estienne's numbered verses of 1551 gave each verse a chapter and verse address. Both programs count the verses of the chapters before the address and add the verse number, using the same illustrative book of three chapters with five, three and four verses.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### verse.py
            verses = [5, 3, 4]   # verses in chapters 1, 2 and 3 of an illustrative book
            for chapter, verse in [(1, 1), (2, 1), (2, 3), (3, 4)]:
                before = sum(verses[:chapter - 1])
                print(f"chapter {chapter} verse {verse} is verse number {before + verse}")
            ### verse.c
            #include <stdio.h>

            int main(void) {
                int verses[] = {5, 3, 4};
                int chapters[] = {1, 2, 2, 3};
                int numbers[] = {1, 1, 3, 4};
                for (int i = 0; i < 4; i++) {
                    int before = 0;
                    for (int c = 0; c < chapters[i] - 1; c++) {
                        before += verses[c];
                    }
                    printf("chapter %d verse %d is verse number %d\n", chapters[i], numbers[i], before + numbers[i]);
                }
                return 0;
            }
            ### run.sh
            python3 verse.py > python.txt
            cc -o verse_c verse.c
            ./verse_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
