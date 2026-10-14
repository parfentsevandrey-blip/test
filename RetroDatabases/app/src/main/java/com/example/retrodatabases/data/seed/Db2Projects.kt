package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Built products of the 1990s: finished programs with an explanation of how they work and instructions for running them. */
val db2Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 2,
        kind = Kind.PROJECT,
        section = "Web and servers",
        title = "Tiny web server in Perl",
        originalTitle = "HTTP/1.0 server in about thirty lines",
        author = "Written from scratch in the style of 1996",
        year = 1996,
        summary = "A complete web server in about thirty lines of Perl: it accepts a connection, reads the request and sends a file.",
        details = "The server is a loop around accept(). Each connection gets one request, which is answered with HTTP/1.0 and then closed. " +
            "It reads the request line, skips the headers, maps the path to a file in the current folder, and sends either the file or a 404 page. " +
            "Apache added modules, logs and configuration on top of this same idea.",
        instructions = """
            1. Save the code as server.pl and put an index.html file in the same folder.
            2. Start the server on port 8080: perl server.pl 8080
            3. In another window request the page: curl http://localhost:8080/
        """,
        example = """
            #!/usr/bin/perl
            # A minimal HTTP/1.0 server: serves files from the current directory.
            use strict;
            use IO::Socket::INET;

            my <DOLLAR>port = shift(@ARGV) || 8080;
            my <DOLLAR>server = IO::Socket::INET->new(
                LocalPort => <DOLLAR>port, Listen => 5, Reuse => 1, Proto => 'tcp'
            ) or die "cannot listen on <DOLLAR>port: <DOLLAR>!";
            print "listening on <DOLLAR>port\n";

            while (my <DOLLAR>client = <DOLLAR>server->accept()) {
                my <DOLLAR>request = <<DOLLAR>client>;
                my (<DOLLAR>method, <DOLLAR>path) = split ' ', <DOLLAR>request;
                while (my <DOLLAR>header = <<DOLLAR>client>) {    # skip the headers
                    last if <DOLLAR>header =~ /^\r?\n<DOLLAR>/;
                }
                <DOLLAR>path = '/index.html' if <DOLLAR>path eq '/';
                <DOLLAR>path =~ s{^/}{};
                if (<DOLLAR>method eq 'GET' and open(my <DOLLAR>file, '<', <DOLLAR>path)) {
                    binmode <DOLLAR>file;
                    local <DOLLAR>/;
                    my <DOLLAR>body = <<DOLLAR>file>;
                    print <DOLLAR>client "HTTP/1.0 200 OK\r\nContent-Type: text/html\r\n\r\n<DOLLAR>body";
                } else {
                    print <DOLLAR>client "HTTP/1.0 404 Not Found\r\nContent-Type: text/plain\r\n\r\nnot found\n";
                }
                close <DOLLAR>client;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PROJECT,
        section = "Graphics and games",
        title = "ASCII raycaster in C",
        originalTitle = "Wolfenstein-style 3D view in text",
        author = "Written in the style of the 1992 raycasting games",
        year = 1992,
        summary = "A Wolfenstein-style 3D view drawn in ASCII: each screen column is a ray that walks the map until it meets a wall.",
        details = "The map is a grid of cells. For each of the 60 screen columns, the program casts a ray from the player's position in that column's direction " +
            "and steps along it until the ray enters a wall cell. The distance sets the height of the wall column, so far walls are short. " +
            "Wolfenstein 3D (1992) used the same idea with textures.",
        instructions = """
            1. Save the code as raycast.c.
            2. Compile it: cc -Wall -o raycast raycast.c -lm
            3. Run ./raycast: the walls appear as columns of '#', '=' and '.', where '#' is the nearest wall.
        """,
        example = """
            #include <math.h>
            #include <stdio.h>

            #define MAP_W 8
            #define MAP_H 8
            #define SCREEN_W 60
            #define SCREEN_H 20

            /* '#' is a wall, '.' is floor */
            static const char map[MAP_H][MAP_W + 1] = {
                "########",
                "#......#",
                "#..##...",
                "#..#....",
                "#.....##",
                "#......#",
                "#..#...#",
                "########"
            };

            int main(void)
            {
                double px = 1.5, py = 1.5;          /* player position */
                double facing = 0.3;                /* viewing direction in radians */
                double fov = 1.0;                   /* field of view in radians */
                int top[SCREEN_W], bottom[SCREEN_W];
                char shade[SCREEN_W];
                int x, y;

                for (x = 0; x < SCREEN_W; x++) {
                    double angle = facing - fov / 2 + fov * x / SCREEN_W;
                    double dx = cos(angle), dy = sin(angle);
                    double dist = 0.0;
                    int mx, my, hit = 0;
                    int height;

                    while (!hit && dist < 20.0) {   /* step along the ray until it meets a wall */
                        dist += 0.02;
                        mx = (int)floor(px + dx * dist);
                        my = (int)floor(py + dy * dist);
                        if (mx < 0 || my < 0 || mx >= MAP_W || my >= MAP_H || map[my][mx] == '#')
                            hit = 1;
                    }
                    height = (int)(SCREEN_H / dist);
                    if (height > SCREEN_H)
                        height = SCREEN_H;
                    top[x] = (SCREEN_H - height) / 2;
                    bottom[x] = top[x] + height;
                    shade[x] = dist < 2.0 ? '#' : (dist < 4.0 ? '=' : '.');
                }

                for (y = 0; y < SCREEN_H; y++) {
                    for (x = 0; x < SCREEN_W; x++)
                        putchar(y >= top[x] && y < bottom[x] ? shade[x] : ' ');
                    putchar('\n');
                }
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PROJECT,
        section = "Graphics and games",
        title = "Doom WAD lister in C",
        originalTitle = "wadlist",
        author = "Written in the style of the 1993 modding tools",
        year = 1993,
        summary = "A tool that reads the directory of a Doom WAD file and lists every lump with its size and position.",
        details = "A WAD file is a container: a 12-byte header points to a directory, and each 16-byte directory entry names one lump and gives its position and size. " +
            "Maps, sprites, sounds and music were all lumps. Modders used tools like this one to see what a level was made of before editing it.",
        instructions = """
            1. Save the code as wadlist.c and compile it: cc -Wall -o wadlist wadlist.c
            2. Run it on any WAD file: ./wadlist DOOM1.WAD
            3. The first line gives the type and the number of lumps; the lines below list each lump with its size and offset.
        """,
        example = """
            #include <stdio.h>

            /* WAD header: type ("IWAD" or "PWAD"), number of lumps, offset of the directory */
            struct wad_header {
                char type[4];
                int lumps;
                int directory;
            };

            /* one directory entry: where the lump starts, its size and its name */
            struct wad_entry {
                int position;
                int size;
                char name[8];
            };

            int main(int argc, char *argv[])
            {
                FILE *f;
                struct wad_header header;
                struct wad_entry entry;
                int i;

                if (argc != 2) {
                    fprintf(stderr, "usage: wadlist file.wad\n");
                    return 1;
                }
                f = fopen(argv[1], "rb");
                if (f == NULL) {
                    perror(argv[1]);
                    return 1;
                }
                if (fread(&header, sizeof header, 1, f) != 1) {
                    fprintf(stderr, "file is too short\n");
                    return 1;
                }
                printf("%.4s: %d lumps\n", header.type, header.lumps);
                fseek(f, header.directory, SEEK_SET);
                for (i = 0; i < header.lumps; i++) {
                    if (fread(&entry, sizeof entry, 1, f) != 1)
                        break;
                    printf("%-8.8s %6d bytes at %d\n", entry.name, entry.size, entry.position);
                }
                fclose(f);
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.PROJECT,
        section = "Bulletin boards",
        title = "BBS door game in Turbo Pascal",
        originalTitle = "A guessing door for a bulletin board",
        author = "Written in the style of the BBS door games of 1992",
        year = 1992,
        summary = "A door game for a bulletin board: the board runs the program, and the game talks to the caller through the terminal.",
        details = "In the 90s a bulletin board ran external programs called doors. The board wrote a drop file with the caller's name and the time left, " +
            "and the door read it. This game asks for guesses, answers higher or lower, and counts the tries. " +
            "It was compiled with Turbo Pascal and started from the board's menu.",
        instructions = """
            1. Save the program as DOORGAME.PAS.
            2. Compile it with Turbo Pascal 7: tpc DOORGAME.PAS (or press F9 in the IDE).
            3. Add a line to the board's menu that runs DOORGAME.EXE; callers reach the game from the menu.
        """,
        example = """
            program Doorgame;
            { A guessing game for a BBS door. The BBS starts the program
              and passes the caller's name in the drop file; this version is a plain game. }
            uses crt;
            var
              secret, guess, tries: integer;
            begin
              clrscr;
              randomize;
              secret := random(100) + 1;
              tries := 0;
              writeln('Welcome, caller! I am thinking of a number from 1 to 100.');
              repeat
                write('Your guess: ');
                readln(guess);
                tries := tries + 1;
                if guess < secret then writeln('Higher.')
                else if guess > secret then writeln('Lower.');
              until guess = secret;
              writeln('Correct, after ', tries, ' tries.');
            end.
        """,
    ),
)
