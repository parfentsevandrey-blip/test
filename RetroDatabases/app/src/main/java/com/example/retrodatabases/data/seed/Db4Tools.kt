package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 2010 to 2020: editors, package managers, containers, automation services and other programs that developers used every day. */
val db4Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Virtual machines",
        title = "Vagrant",
        originalTitle = "Vagrant 0.1",
        author = "Mitchell Hashimoto, John Bender and HashiCorp",
        year = 2010,
        summary = "A tool that creates and starts virtual machines from a text file, so a whole team shares one development setup.",
        details = "Hashimoto began Vagrant as a side project in January 2010, and version 0.1 came out in early March 2010 (sources give 7 or 8 March). " +
            "Version 1.0 landed in March 2012, and HashiCorp was founded in November 2012 to work on the tool full time.",
        instructions = """
            1. Install Vagrant and VirtualBox.
            2. Save the file below as Vagrantfile in an empty folder.
            3. Run vagrant up to build and start the machine, then vagrant ssh to log in.
        """,
        example = """
            Vagrant.configure("2") do |config|
              config.vm.box = "ubuntu/xenial64"
              config.vm.network "forwarded_port", guest: 80, host: 8080
            end
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Configuration",
        title = "Ansible",
        originalTitle = "Ansible",
        author = "Michael DeHaan and Red Hat",
        year = 2012,
        summary = "A tool that configures machines over SSH from YAML playbooks, with no agent to install on the managed hosts.",
        details = "Michael DeHaan started Ansible, and most references give 20 February 2012 for its first release (one documentation source gives March 2012 for version 0.0.1). " +
            "Red Hat bought the company behind it in October 2015 for about 150 million dollars.",
        example = """
            - hosts: webservers
              tasks:
                - name: Install nginx
                  apt:
                    name: nginx
                    state: present
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Containers",
        title = "Docker",
        originalTitle = "Docker",
        author = "Solomon Hykes and dotCloud",
        year = 2013,
        summary = "A tool that packs an app and its libraries into an image, and runs the image as an isolated container.",
        details = "Solomon Hykes showed Docker at PyCon in March 2013, and the project was released as open source that spring. " +
            "Images are built in layers from a text file, and containers share the host's Linux kernel, so they start in a fraction of a second.",
        example = """
            FROM python:3.6-slim
            WORKDIR /app
            COPY hello.py .
            CMD ["python", "hello.py"]
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Editors",
        title = "Visual Studio Code",
        originalTitle = "Visual Studio Code",
        author = "Microsoft",
        year = 2015,
        summary = "A free code editor built on Electron, whose extensions are written in JavaScript and TypeScript.",
        details = "Microsoft announced Visual Studio Code in April 2015 and released its source under the MIT license later that year. " +
            "Extensions run in a separate process and use the same TypeScript-based API, and within a few years it was one of the most widely used code editors.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Editors",
        title = "Android Studio",
        originalTitle = "Android Studio (preview)",
        author = "Google and JetBrains",
        year = 2013,
        summary = "The official IDE for Android, built on IntelliJ IDEA, with a Gradle build and an emulator for testing.",
        details = "Google introduced Android Studio in May 2013 as a replacement for the Eclipse plugin. It is built on IntelliJ IDEA, " +
            "and it uses Gradle as its build system, so each project declares its dependencies in build scripts.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Security",
        title = "Let's Encrypt",
        originalTitle = "Let's Encrypt (public beta)",
        author = "Internet Security Research Group, EFF and Mozilla",
        year = 2015,
        summary = "A free certificate authority whose clients request, install and renew HTTPS certificates on their own.",
        details = "The public beta opened on 3 December 2015, and any site owner with a domain and a web server could get a free certificate. " +
            "Certificates lasted 90 days, and the client renewed them automatically, which made HTTPS cheap enough to turn on by default.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Automation",
        title = "Jenkins",
        originalTitle = "Jenkins (renamed from Hudson)",
        author = "Kohsuke Kawaguchi and the Jenkins community",
        year = 2011,
        summary = "An automation server that runs build and test jobs, defined in a web interface or in a Jenkinsfile.",
        details = "The project took the name Jenkins in 2011, after a dispute with Oracle over the Hudson name, and the community carried on the code. " +
            "Pipelines written in a Jenkinsfile became the usual way to describe jobs, and the file lives in the project's repository.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Package managers",
        title = "Yarn",
        originalTitle = "Yarn",
        author = "Facebook, Google, Exponent and Tilde engineers",
        year = 2016,
        summary = "A JavaScript package manager that installs dependencies in parallel and locks their exact versions in a lock file.",
        details = "Engineers from Facebook, Google, Exponent and Tilde released Yarn in October 2016 to fix slow and inconsistent installs from npm. " +
            "Its lock file recorded the exact version of every dependency, so every machine installed the same packages.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Mobile frameworks",
        title = "React Native",
        originalTitle = "React Native",
        author = "Facebook",
        year = 2015,
        summary = "A framework that builds iOS and Android apps from JavaScript and React components, which map onto native views.",
        details = "Facebook announced React Native at its F8 conference in March 2015 and released it as open source that year. " +
            "Components draw native views instead of web views, so the app looks and behaves like a native one while most of the code is shared.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Automation",
        title = "GitHub Actions",
        originalTitle = "GitHub Actions",
        author = "GitHub",
        year = 2019,
        summary = "Workflow automation built into GitHub, in which YAML files run jobs on hosted machines for each push or pull request.",
        details = "GitHub made Actions generally available in November 2019, after a beta that began in 2018. " +
            "A workflow file in the repository names the events that start it and the steps that run on the hosted runner.",
        example = """
            name: tests
            on: [push]
            jobs:
              test:
                runs-on: ubuntu-latest
                steps:
                  - uses: actions/checkout@v2
                  - run: python3 -m unittest
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Infrastructure",
        title = "Terraform",
        originalTitle = "Terraform",
        author = "HashiCorp and Mitchell Hashimoto",
        year = 2014,
        summary = "A tool that describes cloud resources in declarative files, and creates, changes or removes them to match.",
        details = "Terraform's first release is dated 28 July 2014 in most references, and it started with providers for AWS and DigitalOcean. " +
            "Its plan step shows what would change before anything is applied, and infrastructure as code became a common practice through the decade.",
        example = """
            resource "aws_instance" "web" {
              ami           = "ami-0123456789abcdef0"
              instance_type = "t2.micro"
            }
        """,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Version control",
        title = "Git LFS",
        originalTitle = "Git Large File Storage",
        author = "GitHub",
        year = 2015,
        summary = "An extension to Git that keeps large files on a server and leaves small text pointers in the repository.",
        details = "GitHub made Git LFS public in April 2015, after early access for a small group. Each large file in the repository is replaced by a text pointer, " +
            "and its contents sit on a server. Version 1.0 opened the feature to all GitHub.com repositories in October 2015.",
        rare = true,
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "State management",
        title = "Redux",
        originalTitle = "Redux",
        author = "Dan Abramov and Andrew Clark",
        year = 2015,
        summary = "A JavaScript state container in which one store holds the app state and pure reducers compute each change.",
        details = "Redux had its first public release on 2 June 2015. It took its ideas from Flux, and its reducers are pure functions, " +
            "so every state change can be recorded and replayed, which made time-travel debugging practical.",
    ),

    entry(
        databaseId = 4,
        kind = Kind.TOOL,
        section = "Design",
        title = "Figma",
        originalTitle = "Figma",
        author = "Figma, Inc.",
        year = 2016,
        summary = "A browser-based design tool in which several people edit the same file at the same time.",
        details = "Figma's first public release came in late September 2016, after a closed beta that had started in late 2015. " +
            "Real-time collaborative editing set it apart from design tools that ran as desktop applications.",
    ),
)
