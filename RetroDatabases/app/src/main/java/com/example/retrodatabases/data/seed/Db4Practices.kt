package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 2010 to 2020: working habits, release rules, versioning and community rules that programmers adopted in the decade. */
val db4Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Web design",
        title = "Responsive web design",
        originalTitle = "Responsive Web Design",
        author = "Ethan Marcotte",
        year = 2010,
        summary = "One page layout with fluid grids and media queries, so the same site fits phones, tablets and desktop screens.",
        details = "Marcotte named the approach in an article for A List Apart in May 2010. Fluid grids scale with the window, images resize inside their boxes, " +
            "and media queries change the layout at set widths. Many teams stopped building a separate mobile site after that.",
        instructions = """
            1. Add a viewport meta tag to the page head.
            2. Give the layout a width in percent, not in fixed pixels.
            3. Add a media query where the layout needs to change, then test with a narrow window.
        """,
        example = """
            .gallery {
              display: grid;
              grid-template-columns: 1fr;
              gap: 1rem;
            }

            @media (min-width: 40em) {
              .gallery {
                grid-template-columns: 1fr 1fr;
              }
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Web design",
        title = "Mobile first",
        originalTitle = "Mobile First",
        author = "Luke Wroblewski",
        year = 2011,
        summary = "A way to write CSS: start with the smallest screen, then add rules for wider screens with min-width queries.",
        details = "Wroblewski's book Mobile First, published in 2011, argued that designers should start from the smallest screen and add features as space grows. " +
            "Teams that did this often ended with simpler stylesheets, because the base styles were already small.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Version control",
        title = "Git flow",
        originalTitle = "A successful Git branching model",
        author = "Vincent Driessen",
        year = 2010,
        summary = "A branching model with long-lived develop and master branches, and short branches for features, releases and fixes.",
        details = "Driessen described the model in a blog post in January 2010, and it spread quickly among teams that wanted release branches. " +
            "Its many branch types became a burden for teams that deploy every day, which led some of them to trunk-based workflows.",
        instructions = """
            1. Save the commands below as flow.sh.
            2. Run it: sh flow.sh
            3. Look at the graph at the end: the feature branch is merged into develop with a merge commit.
        """,
        example = """
            git init -q flow
            cd flow
            git config user.name "Ann"
            git config user.email "ann@example.com"
            echo start > app.txt
            git add app.txt
            git commit -qm "start"
            git checkout -q -b develop
            git checkout -q -b feature/login
            echo login >> app.txt
            git commit -qam "add login"
            git checkout -q develop
            git merge -q --no-ff feature/login -m "Merge feature/login"
            git log --oneline --graph
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Version control",
        title = "GitHub flow",
        originalTitle = "GitHub Flow",
        author = "Scott Chacon",
        year = 2011,
        summary = "One main branch, short-lived feature branches, and a pull request to review each change before it is merged.",
        details = "Chacon described GitHub flow in a 2011 blog post, as a lighter alternative to heavier branching models. " +
            "The main branch was deployed after each merge, and the pull request was where review and discussion happened. Many small teams and open-source projects adopted it.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Documentation",
        title = "README-driven development",
        originalTitle = "Readme Driven Development",
        author = "Tom Preston-Werner",
        year = 2010,
        summary = "Write the README of a project before its code, so the design is checked against the words that describe it.",
        details = "Preston-Werner, a co-founder of GitHub, described the approach on his blog in 2010. " +
            "The developer writes the README as a user would read it, then builds the code to match. The method suits libraries and command-line tools, where the usage text is the interface.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Cloud apps",
        title = "The Twelve-Factor App",
        originalTitle = "The Twelve-Factor App",
        author = "Adam Wiggins and the Heroku team",
        year = 2011,
        summary = "Twelve rules for web apps on a cloud platform, such as config in the environment and logs written as streams.",
        details = "Wiggins published the twelve factors at Heroku in 2011, based on what worked for apps on its platform. " +
            "The rules cover a single codebase, explicit dependencies, config in the environment, backing services as attached resources and logs written to standard output.",
        instructions = """
            1. Save the code below as server.py.
            2. Run it with a port in the environment: PORT=9000 python3 server.py
            3. The port is read from the environment, not from a value written into the code.
        """,
        example = """
            import os

            port = int(os.environ.get("PORT", "8000"))
            print("listening on", port)
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Release",
        title = "Continuous Delivery",
        originalTitle = "Continuous Delivery (book)",
        author = "Jez Humble and David Farley",
        year = 2010,
        summary = "A practice in which every change goes through an automated pipeline that ends in a build ready to release.",
        details = "Humble and Farley published Continuous Delivery in 2010. The book argued for automated build, test and deployment pipelines, " +
            "and for trunk builds that always stay releasable. Many teams adopted the pipeline idea in the years that followed.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Architecture",
        title = "Microservices",
        originalTitle = "Microservices (article)",
        author = "James Lewis and Martin Fowler",
        year = 2014,
        summary = "An architecture in which an application is split into small services that run and deploy on their own.",
        details = "Lewis and Fowler published a widely read article on microservices in March 2014. Each service owns its data and talks to the others over the network, " +
            "so teams can deploy separately. The style brought new problems of tracing and failure handling, which later tools tried to solve.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Versioning",
        title = "Semantic Versioning 2.0",
        originalTitle = "Semantic Versioning 2.0.0",
        author = "Tom Preston-Werner",
        year = 2013,
        summary = "Version numbers in the form MAJOR.MINOR.PATCH, where a major change is one that breaks compatibility.",
        details = "Preston-Werner published Semantic Versioning 2.0 in 2013 as a short spec. A patch fixes bugs, a minor version adds compatible features, " +
            "and a major version breaks compatibility. Sources differ on the origin of the idea, and one places early versions in 2011, but the 2.0 spec dates from 2013.",
        example = """
            "dependencies": {
              "left-pad": "^1.3.0"
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Community",
        title = "Hacktoberfest",
        originalTitle = "Hacktoberfest",
        author = "DigitalOcean",
        year = 2014,
        summary = "A yearly October event that invites people to make open-source contributions, with prizes for those who take part.",
        details = "DigitalOcean started Hacktoberfest in 2014. The first-year counts disagree: one report gives 676 participants and another 768, so the number should be checked before it is quoted. " +
            "The event made October a month for first pull requests and beginner-friendly issues.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Community",
        title = "Contributor Covenant",
        originalTitle = "Contributor Covenant",
        author = "Coraline Ada Ehmke",
        year = 2014,
        summary = "A code of conduct for open-source projects, written so that a community can adopt it with little editing.",
        details = "Ehmke began the Contributor Covenant in 2014. Projects adopt it by copying its text into a CODE_OF_CONDUCT file. " +
            "Stewardship later moved to the Organization for Ethical Source, which Ehmke co-founded and leads.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Dependencies",
        title = "Lessons from left-pad",
        originalTitle = "left-pad incident",
        author = "The npm community",
        year = 2016,
        summary = "The 2016 episode in which one unpublished npm package broke many builds, and which taught teams to pin their dependencies.",
        details = "On 22 March 2016 the author removed the tiny left-pad package from npm, and builds that depended on it, many of them through other packages, failed across the ecosystem. " +
            "npm restored the package within a few hours. Teams afterwards pinned versions, committed lock files and reviewed their dependency trees more closely.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Containers",
        title = "Multi-stage Docker builds",
        originalTitle = "Multi-stage builds",
        author = "Docker, Inc.",
        year = 2017,
        summary = "A Dockerfile with several FROM stages, so the build tools stay in an early stage and only the result is copied forward.",
        details = "Docker 17.05 added multi-stage builds in May 2017. A build stage compiles the program with all its tools, and a later stage copies only the finished binary into a small image. " +
            "The final image stays small, and the compilers never reach production.",
        example = """
            FROM golang:1.12 AS build
            WORKDIR /src
            COPY . .
            RUN go build -o /out/app .

            FROM debian:stable-slim
            COPY --from=build /out/app /usr/local/bin/app
            CMD ["app"]
        """,
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.PRACTICE,
        section = "Web design",
        title = "Progressive web apps",
        originalTitle = "Progressive Web Apps",
        author = "Frances Berriman and Alex Russell",
        year = 2015,
        summary = "Websites that use service workers and a web app manifest, so they can work offline and be installed like apps.",
        details = "Berriman and Russell gave the approach its name in 2015. Their web apps used newer browser features to work offline and to be installed on the home screen. " +
            "Wikipedia dates the wider rollout to 2016, once browsers supported service workers well.",
    ),
)
