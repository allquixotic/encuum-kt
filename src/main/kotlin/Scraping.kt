import com.microsoft.playwright.Browser
import com.microsoft.playwright.BrowserType
import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.ScreenSize
import com.microsoft.playwright.options.ViewportSize
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.SizedIterable
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*

fun scrape(opts: ScrapeOpts) {
    val lo = BrowserType.LaunchOptions().apply { headless = opts.headless; slowMo = 150.0}
    val npo = Browser.NewPageOptions ().apply { ignoreHTTPSErrors = true; screenSize = ScreenSize(1920, 1080); viewportSize = Optional.of(ViewportSize(1920, 1080))}
    val playwright = Playwright.create()
    val browserType = playwright.chromium()
    val browser = browserType.launch(lo)
    val page = browser.newPage(npo)
    val skipUnique = su@{ exec: () -> Unit ->
        try {
            return@su transaction {
                exec()
            }
        }
        catch(e: Exception) {
            if(e.toString().contains("UNIQUE")) {
                println("Ignoring attempt to create duplicate entity")
            }
            else {
                println("Throwing ${e.toString()} /// ${e.message}")
                throw e
            }
        }
    }

    //Login
    page.navigate("${opts.baseUrl}/login")
    page.type("css=[name=username]", opts.username)
    page.type("//input[@type='password']", opts.password)
    page.click("//input[@type='submit' and @value='Login']")

    //Get the list of forums
    page.navigate("${opts.baseUrl}${opts.forumBase}")
    skipUnique {
        for (forumLink in page.locator("//a[contains(@class,'forum-name') or contains(@class,'subforum-')]")
            .elementHandles()) {
            Forum.new {
                url = opts.baseUrl + forumLink.getAttribute("href")
                title = forumLink.innerText()
            }
        }
    }

    //Go through each forum and extract all the thread links
    val postContentsRx = """^\s*\[quote=@[0-9]+]\s*(.*)\s*\[/quote]\s*$""".toRegex(RegexOption.DOT_MATCHES_ALL)
    var forums: List<Forum>? = null
    transaction {
        forums = Forum.all().notForUpdate().toList()
    }

    for (forum in forums!!) {
        //Go to the forum's first page of the list of threads
        page.navigate(forum.url)

        val pagedLoop = { extractor: (Int) -> Unit ->
            var nextArrow: ElementHandle? = null
            var pageNum = 1
            val nextArrowGetter = nag@{
                try {
                    nextArrow = page.locator("//input[@class='right']").elementHandles().first()
                    return@nag true
                } catch (e: Exception) {
                    return@nag false
                }
            }

            do {
                nextArrow?.click()
                extractor(pageNum)
                pageNum++
            } while (nextArrowGetter())
        }

        pagedLoop { _: Int ->
            try {
                skipUnique {
                    val threadLinkElements = page.locator("//a[contains(@class,'thread-subject')]").elementHandles()
                    for (threadLinkElement in threadLinkElements) {
                        ForumThread.new {
                            this.url = opts.baseUrl + threadLinkElement.getAttribute("href")
                            this.title = threadLinkElement.textContent()
                            this.posterName = ""
                            this.forum = forum.id
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        //Depth first -- extract all forum posts on the current forum
        var threads: List<ForumThread>? = null
        transaction {
            threads = forum.threads.notForUpdate().toList()
        }
        for (thread in threads!!) {
            page.navigate(thread.url)
            val origUsernames = page.locator("//a[contains(@class, 'element_username')]").elementHandles()
            transaction {
                if (origUsernames.size == 0) {
                    thread.posterName = "UNKNOWN POSTER"
                } else {
                    thread.posterName = origUsernames.first().textContent()
                }
            }

            pagedLoop { _: Int ->
                Thread.sleep(1000)
                val postLocator = page.locator("//tr[@post_id]")
                val userNameLocator = page.locator("//tr[@post_id]//a[contains(@class, 'element_username')]")
                val quotes = page.locator("//div[contains(@class,'iconf-quote-right')]").elementHandles()
                val posteds = page.locator("div.posted").elementHandles()

                quotes.forEachIndexed { pos, quote ->
                    val textArea = page.locator("//textarea[@id='content']")
                    textArea.clear()
                    quote.click()
                    holup@for(i in 0..30) {
                        if (textArea.inputValue().trim().isNotEmpty())
                            break@holup;
                        Thread.sleep(1000)
                    }
                    try {
                        skipUnique {
                            Post.new {
                                this.posterName = userNameLocator.elementHandles()[pos].textContent()
                                this.seq = pos + 1
                                this.url = page.url()
                                this.bbcode =
                                    postContentsRx.matchEntire(textArea.inputValue())?.groupValues?.get(1) ?: ""
                                this.forumThread = thread.id
                                this.posted = posteds[pos].innerText()
                                this.enjinPostId = postLocator.elementHandles()[pos].getAttribute("post_id").toInt()
                            }
                        }
                        textArea.clear()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}