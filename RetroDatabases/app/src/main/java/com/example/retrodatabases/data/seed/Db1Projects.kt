package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Built products: finished programs with an explanation of how they work and instructions for running them. */
val db1Projects: List<RecordEntity> = listOf(

    entry(
        kind = Kind.PROJECT,
        section = "Games and simulations",
        title = "Game of Life (Conway) on awk",
        originalTitle = "Conway's Game of Life in awk",
        author = "John Conway (rules), implementation in awk",
        year = 1970,
        summary = "A cellular automaton in the terminal: generations are born, die and move according to Conway's rules.",
        details = "A cell survives if 2 or 3 neighbours are alive, and is born if exactly 3 neighbours are alive. " +
            "The field wraps around into a torus: the edge leads to the opposite edge. " +
            "Live cells are stored in the associative array cell, keyed by “row, column”. " +
            "At each step the program builds a new field nxt and copies it back. The glider, five cells, moves diagonally.",
        instructions = """
            1. Save the code as life.awk.
            2. Run: awk -f life.awk
            3. To see more generations, increase GENS in the BEGIN block. Add new shapes in the same block: cell[row, column] = 1.
        """,
        example = """
            # Conway's Game of Life. Run: awk -f life.awk
            BEGIN {
                W = 20; H = 10; GENS = 5
                # starting shape: a glider
                cell[1, 2] = 1; cell[2, 3] = 1
                cell[3, 1] = 1; cell[3, 2] = 1; cell[3, 3] = 1
                for (g = 0; g < GENS; g++) {
                    show()
                    step()
                }
            }

            function show(   y, x, line, ch) {
                for (y = 0; y < H; y++) {
                    line = ""
                    for (x = 0; x < W; x++) {
                        ch = "."
                        if ((y, x) in cell) ch = "#"
                        line = line ch
                    }
                    print line
                }
                print "----"
            }

            function step(   y, x, dy, dx, n, ny, nx, alive, nxt, k) {
                delete nxt
                for (y = 0; y < H; y++)
                    for (x = 0; x < W; x++) {
                        n = 0
                        for (dy = -1; dy <= 1; dy++)
                            for (dx = -1; dx <= 1; dx++) {
                                if (dy == 0 && dx == 0) continue
                                ny = (y + dy + H) % H
                                nx = (x + dx + W) % W
                                if ((ny, nx) in cell) n++
                            }
                        alive = ((y, x) in cell)
                        if ((alive && (n == 2 || n == 3)) || (!alive && n == 3))
                            nxt[y, x] = 1
                    }
                delete cell
                for (k in nxt) cell[k] = 1
            }
        """,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Games and simulations",
        title = "Random maze in the console",
        originalTitle = "Maze generator with depth-first backtracking",
        author = "80s practice, depth-first search with backtracking",
        year = 1980,
        summary = "The program carves passages through a grid of walls: the result is a tree without cycles, where there is exactly one path between any two points.",
        details = "Depth-first search with backtracking: from the current cell we choose a random neighbour that has not been visited yet, " +
            "knock down the wall between them and continue. If there are no neighbours left, we backtrack. " +
            "Cells sit at odd positions of the grid and walls at even positions, so the structure is always valid.",
        instructions = """
            1. Save the code as maze.c and build it: cc -o maze maze.c
            2. Run it several times: each run gives a new maze, because the generator depends on the time.
            3. To make the maze bigger, increase N (the number of cells along one side). N must be odd.
        """,
        example = """
            #include <stdio.h>
            #include <stdlib.h>
            #include <time.h>

            #define N 11                     /* number of cells along one side */
            #define W (2 * N + 1)            /* full grid: cells and the walls between them */

            static char grid[W][W];
            static int visited[N][N];

            static void carve(int cy, int cx)
            {
                static const int dy[4] = { -1, 1, 0, 0 };
                static const int dx[4] = { 0, 0, -1, 1 };
                int order[4] = { 0, 1, 2, 3 };
                int i, j, tmp;

                visited[cy][cx] = 1;
                grid[2 * cy + 1][2 * cx + 1] = ' ';

                for (i = 3; i > 0; i--) {    /* shuffle the directions */
                    j = rand() % (i + 1);
                    tmp = order[i];
                    order[i] = order[j];
                    order[j] = tmp;
                }

                for (i = 0; i < 4; i++) {
                    int d = order[i];
                    int ny = cy + dy[d];
                    int nx = cx + dx[d];

                    if (ny < 0 || ny >= N || nx < 0 || nx >= N || visited[ny][nx])
                        continue;
                    grid[2 * cy + 1 + dy[d]][2 * cx + 1 + dx[d]] = ' ';   /* knock down the wall */
                    carve(ny, nx);
                }
            }

            int main(void)
            {
                int y, x;

                srand((unsigned)time(NULL));
                for (y = 0; y < W; y++)
                    for (x = 0; x < W; x++)
                        grid[y][x] = '#';

                carve(0, 0);

                for (y = 0; y < W; y++) {
                    for (x = 0; x < W; x++)
                        putchar(grid[y][x]);
                    putchar('\n');
                }
                return 0;
            }
        """,
        rare = true,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Utilities",
        title = "Address book in sh",
        originalTitle = "Address book in plain sh and awk",
        author = "Shell script practice, 1980s",
        year = 1979,
        summary = "A phone book in one script: add a record, find one, show everything. No database is needed.",
        details = "The records live in a text file, book.txt: one record per line, fields separated by tabs. " +
            "The add and find commands are branches of the shell that call printf, grep and awk. " +
            "The project shows that much of what a database does can be done with a text file in a clear format and a couple of Unix tools.",
        instructions = """
            1. Save the script as book.sh and make it executable: chmod +x book.sh
            2. Add records: ./book.sh add Ivan 555-1234 and ./book.sh add Maria 555-9876
            3. Find a record: ./book.sh find Ivan
            4. Show all records: ./book.sh list
        """,
        example = """
            #!/bin/sh
            # Address book: records are stored in book.txt, fields are separated by tabs
            case "$1" in
              add)  printf '%s\t%s\n' "$2" "$3" >> book.txt ;;
              find) grep -i "$2" book.txt ;;
              list) awk -F '\t' '{ printf "%-15s %s\n", $1, $2 }' book.txt ;;
              *)    echo "usage: book.sh add|find|list" ;;
            esac
        """,
    ),

    entry(
        kind = Kind.PROJECT,
        section = "Development tools",
        title = "Calculator with yacc",
        originalTitle = "Calculator with yacc grammar and a hand-written lexer",
        author = "Stephen Johnson (yacc, 1975), Unix practice",
        year = 1975,
        summary = "A grammar instead of hand-written parsing: yacc builds a parser from rules, and precedence is set by the declaration lines.",
        details = "The file has three parts: declarations (tokens and operator precedence), grammar rules with actions written in C, and the lexer code. " +
            "You do not code the precedence of * over + by hand: it is set by the order of the %left lines. " +
            "The action $$ = $1 + $3 computes the value of the expression. " +
            "This is how calculators, configurators and compilers were written in the 80s.",
        instructions = """
            1. Save the grammar as calc.y. The lexer is in the third part of the file; you do not need to change it.
            2. Generate the parser: bison -y -d calc.y (this creates y.tab.c and y.tab.h).
            3. Build: cc -o calc y.tab.c
            4. Run: echo "2 + 3 * (4 - 1)" | ./calc — the answer is 11.
        """,
        example = """
            %{
            #include <stdio.h>
            #include <ctype.h>
            int yylex(void);
            void yyerror(const char *s);
            %}

            %union {
                double val;
            }

            %token <val> NUMBER
            %type <val> expr
            %left '+' '-'
            %left '*' '/'
            %right UMINUS

            %%
            line : /* empty */
                 | line expr '\n'         { printf("%g\n", $2); }
                 ;
            expr : expr '+' expr          { $$ = $1 + $3; }
                 | expr '-' expr          { $$ = $1 - $3; }
                 | expr '*' expr          { $$ = $1 * $3; }
                 | expr '/' expr          { $$ = $1 / $3; }
                 | '-' expr %prec UMINUS  { $$ = -$2; }
                 | '(' expr ')'           { $$ = $2; }
                 | NUMBER                 { $$ = $1; }
                 ;
            %%

            int yylex(void)
            {
                int c;

                while ((c = getchar()) == ' ' || c == '\t')
                    ;
                if (isdigit(c) || c == '.') {
                    ungetc(c, stdin);
                    scanf("%lf", &yylval.val);
                    return NUMBER;
                }
                return c;
            }

            void yyerror(const char *s)
            {
                fprintf(stderr, "%s\n", s);
            }

            int main(void)
            {
                return yyparse();
            }
        """,
        rare = true,
    ),
)
