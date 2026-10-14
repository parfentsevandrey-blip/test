package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of the 1990s: how the mechanisms of the decade worked, from CGI to the Doom renderer. */
val db2Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 2,
        kind = Kind.GUIDE,
        section = "Web",
        title = "How CGI works: from a web form to a script",
        originalTitle = "Common Gateway Interface",
        author = "NCSA httpd developers and the CGI drafts",
        year = 1993,
        summary = "The browser sends a request, the server starts a program and sends back whatever the program prints.",
        details = "A browser sends an HTTP request. The server finds the script in the cgi-bin directory, puts the request details into environment variables " +
            "such as REQUEST_METHOD and QUERY_STRING, and starts the program. The program writes its headers, an empty line and the body to standard output, " +
            "and the server passes that back to the browser. For a GET form the data arrives in QUERY_STRING; for a POST form it arrives on standard input.",
        instructions = """
            1. Save the example as hello.cgi and make it executable on a Unix server.
            2. Test it from a shell without a web server: REQUEST_METHOD=GET QUERY_STRING="name=Ann" perl hello.cgi
            3. Compare the output with what a browser would receive: the headers, an empty line, then the HTML.
        """,
        example = """
            #!/usr/bin/perl
            # Minimal CGI program: reads the form field "name" from QUERY_STRING.
            my <DOLLAR>query = <DOLLAR>ENV{'QUERY_STRING'} || '';
            my (<DOLLAR>name) = <DOLLAR>query =~ /name=([^&]*)/;
            <DOLLAR>name = 'stranger' unless defined <DOLLAR>name;
            print "Content-type: text/html\n\n";
            print "<html><body><h1>Hello, <DOLLAR>name!</h1></body></html>\n";
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.GUIDE,
        section = "Languages and runtimes",
        title = "How Java bytecode runs: from javac to the JVM",
        originalTitle = "The Java Virtual Machine",
        author = "Tim Lindholm and Frank Yellin (JVM specification)",
        year = 1996,
        summary = "The compiler writes stack-based bytecode, and the virtual machine checks it and runs it on any host.",
        details = "javac turns source code into class files that contain bytecode: a compact, stack-based instruction set of about 200 opcodes. " +
            "The virtual machine loads a class, checks it with the bytecode verifier, and then interprets it or compiles it to native code. " +
            "Applets in browsers ran the same way, with a security manager limiting what they could reach.",
        instructions = """
            1. Save the example as Adder.java and compile it: javac Adder.java
            2. Show the bytecode of the add method: javap -c Adder
            3. Find iload_0, iload_1, iadd and ireturn: these are the stack operations that add two numbers.
        """,
        example = """
            public class Adder {
                static int add(int a, int b) {
                    return a + b;
                }
                public static void main(String[] args) {
                    System.out.println(add(2, 3));
                }
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.GUIDE,
        section = "Graphics and games",
        title = "How a BSP tree draws a Doom level",
        originalTitle = "Binary space partitioning",
        author = "The Doom engine (id Software); the BSP idea from Fuchs, Kedem and Naylor (1980)",
        year = 1993,
        summary = "A map is split along its walls again and again, so the renderer can draw the walls in a safe order.",
        details = "A binary space partition splits the map along the line of a wall, and each half is split again until every piece is a convex region. " +
            "The tree stores these splits. From the player's position the renderer walks the tree and visits the far side first, " +
            "so nearer walls are drawn over farther ones. The method was described in 1980 by Fuchs, Kedem and Naylor. " +
            "Doom (1993) used a two-dimensional version of it, which saved sorting every wall on every frame.",
    ),

    entry(
        databaseId = 2,
        kind = Kind.GUIDE,
        section = "Graphics and games",
        title = "How the mode 13h screen works: 320x200 with 256 colours",
        originalTitle = "VGA mode 13h",
        author = "IBM VGA standard; the favourite mode of DOS games",
        year = 1990,
        summary = "A 320 by 200 picture with one byte per pixel, written straight into memory at segment A000h.",
        details = "VGA (1987) offered a mode with 320 by 200 pixels and 256 colours chosen from 262,144. " +
            "The frame buffer starts at segment A000h, and pixel (x, y) is at offset y times 320 plus x. " +
            "DOS games drew into this memory directly, which made them fast, and many copied a whole frame during the vertical blank to avoid flicker.",
        instructions = """
            1. Save the example as vram.c and compile it: cc -o vram vram.c
            2. Run ./vram: it fills the buffer with a pattern and prints one pixel.
            3. On DOS hardware the array would be a pointer to segment A000h; the rest of the code stays the same.
        """,
        example = """
            #include <stdio.h>

            #define W 320
            #define H 200

            /* stands in for the VGA segment A000h, one byte per pixel */
            static unsigned char vram[W * H];

            int main(void)
            {
                int x, y;
                for (y = 0; y < H; y++)
                    for (x = 0; x < W; x++)
                        vram[y * W + x] = (unsigned char)(x ^ y);
                printf("pixel(10,20) = %d\n", vram[20 * W + 10]);
                return 0;
            }
        """,
    ),

    entry(
        databaseId = 2,
        kind = Kind.GUIDE,
        section = "Networking",
        title = "How Winsock connects a Windows program to the Internet",
        originalTitle = "Windows Sockets (Winsock) 1.1",
        author = "The Windows Sockets working group",
        year = 1993,
        summary = "A Berkeley-style socket API for Windows, so that TCP/IP programs could be ported from Unix.",
        details = "Winsock 1.1 (1993) gave Windows programs the Berkeley socket calls: socket, connect, send and recv. " +
            "A program first starts the library with WSAStartup, then opens a TCP socket, connects to a host and exchanges bytes. " +
            "Trumpet Winsock and other stacks supplied the TCP/IP layer under the same interface.",
        example = """
            WSADATA wsa;
            SOCKET s;
            struct sockaddr_in addr;

            WSAStartup(MAKEWORD(1, 1), &wsa);
            s = socket(AF_INET, SOCK_STREAM, 0);
            addr.sin_family = AF_INET;
            addr.sin_port = htons(80);
            addr.sin_addr.s_addr = inet_addr("192.0.2.10");
            connect(s, (struct sockaddr *)&addr, sizeof(addr));
            send(s, "GET / HTTP/1.0\r\n\r\n", 18, 0);
            closesocket(s);
            WSACleanup();
        """,
    ),
)
