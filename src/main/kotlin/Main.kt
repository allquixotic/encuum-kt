import io.github.cdimascio.dotenv.dotenv

fun main() {
    val dotenv = dotenv()
    val opts = ScrapeOpts(
        headless = dotenv["headless", "true"].toBoolean(),
        baseUrl = dotenv["baseurl"],
        username = dotenv["username"],
        password = dotenv["password"],
        forumBase = dotenv["forumbase"]
    )
    //val rslt = scrape(opts)
    mkTables()
    scrape(opts)
    //writeForumsToFiles(rslt)
}