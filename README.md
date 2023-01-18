# encuum-kt
Encuum - Enjin forum scraper that saves to SQL

Documentation and the code itself is a work in progress. I will probably be shifting my focus over to using the Enjin API (if I can get it to work), but I will make that a new GitHub repo, so that those who want to continue to use the screen scraper version can continue using it.

This screen scraper only requires that you have a user account on Enjin with access to forums that you want to backup. That's all!

It also requires Java 16 JDK (Development Kit) or newer version. You can get it from Azul Zulu. Get the "Azul Zulu Build of OpenJDK" from https://www.azul.com/downloads/

Before you run `gradlew.bat run` - which is the main command to kick off the script - you need to create a file called `.env` in the directory where you cloned this Git repo.

In this file, you should set variable=value type variables for the following (required) options:

```
baseurl=https://some-enjin-site.com
username=your-enjin-username
password=your-enjin-password
forumbase=/the/link/to/forum
```

The forumbase should be everything past the domain name in the URL to get to the "index" page of your forum (the page displaying all the subforums).

Example config file for screen scraping enjin.com's own forum:

```
baseurl=https://www.enjin.com
username=somebody@gmail.com
password=somepassword12345!
forumbase=/forums/m/10826
```

Once you create this file, save it. It must be named exactly `.env` - you might have to use Powershell or cmd.exe to set the name, or a good text editor like VS Code or Notepad++.

Now on Windows you would run `gradlew.bat run` to execute the tool.

To watch what the tool is doing, add `headless=true` to the config file.

The tool also works on MacOS and Linux, but there you'll run `./gradlew run` instead from a Terminal app.

# Known Issues

 - Java version 19 appears not to work right now. Issue #1
 - The program keeps running even after it's done all its work. Issue #2

# After It's Done

Once the program completes, you have a [SQLite database](https://sqlite.org/index.html) with your forum export in it. Many different programs can parse SQLite databases, and transform the data into various formats. See: 

 - https://github.com/planetopendata/awesome-sqlite for a list of useful SQLite tools
 - https://www.dbvis.com for DBVisualizer (freeware with a paid version with extra features) 

# Importing Into a New Site

This is beyond the scope of what encuum can help you with, but you will need to use a program (or write a script/program) to transform the data format of encuum's sqlite database into a format that your new site can use, if you want the encuum-exported data to become forum posts on a new site.

I can provide general tips if you give me specifics about where you're trying to import, but I probably won't have time to write code for you.
