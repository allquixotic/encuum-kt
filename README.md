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
