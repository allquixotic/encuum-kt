import com.microsoft.playwright.*
import com.microsoft.playwright.Browser.NewContextOptions
import com.microsoft.playwright.BrowserType.LaunchOptions
import com.microsoft.playwright.options.ScreenSize
import com.microsoft.playwright.options.ViewportSize
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

data class PlaywrightInstance(
    val pw: Playwright,
    val lo: LaunchOptions,
    val nco: NewContextOptions,
    val bt: BrowserType,
    val ctxt: BrowserContext,
    val brow: Browser,
    val pg: Page
)

fun scrape(opts: ScrapeOpts) {
    val rand = SecureRandom()
    val newPlaywright = {
        val npw = Playwright.create()
        val nbt = npw.chromium()
        val nlo = LaunchOptions().apply { headless = opts.headless; slowMo = 150.0 }
        val nnco = NewContextOptions().apply {
            ignoreHTTPSErrors = true
            screenSize = ScreenSize(1920, 1080)
            viewportSize = Optional.of(ViewportSize(1920, 1080))
        }
        val nbrowser = nbt.launch(nlo)
        val nctxt = nbrowser.newContext(nnco)
        val npg = nctxt.newPage()
        PlaywrightInstance(npw, nlo, nnco, nbt, nctxt, nbrowser, npg)
    }
    val threadLocal: ThreadLocal<PlaywrightInstance?> = ThreadLocal.withInitial { null }
    val threadFactory = ThreadFactory { r: Runnable ->
        val thr = Thread(r)
        //Close browsers if a thread dies
        thr.setUncaughtExceptionHandler { _, _ ->
            threadLocal.get()?.brow?.close()
        }
        thr
    }
    val workerPool = Executors.newFixedThreadPool(1, threadFactory)
    val pi = newPlaywright()
    threadLocal.set(pi) //For the main thread only, via the magic of ThreadLocal
    val page = pi.pg

    val skipUnique = su@{ exec: () -> Unit ->
        try {
            return@su transaction {
                exec()
            }
        } catch (e: Exception) {
            if (e.toString().contains("UNIQUE")) {
                println("Ignoring attempt to create duplicate entity")
            } else {
                println("Throwing $e /// ${e.message}")
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

    //Steal the cookies from the logged-in page
    val loggedInCookies = pi.ctxt.cookies()

    //Go through each forum and extract all the thread links
    val postContentsRx = """^\s*\[quote=@[0-9]+]\s*(.*)\s*\[/quote]\s*$""".toRegex(RegexOption.DOT_MATCHES_ALL)
    var forums: List<Forum>? = null
    transaction {
        forums = Forum.all().notForUpdate().toList()
    }

    for (forum in forums!!) {
        //Go to the forum's first page of the list of threads
        page.navigate(forum.url)

        val pagedLoop = { thePage: Page, extractor: (Int) -> Unit ->
            var nextArrow: ElementHandle? = null
            var pageNum = 1
            val nextArrowGetter = nag@{
                try {
                    nextArrow = thePage.locator("//input[@class='right']").elementHandles().first()
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
            println("We're done in a pagedLoop after ${pageNum-1}")
        }

        pagedLoop(page) { pageNum: Int ->
            try {
                skipUnique {
                    val threadLinkElements = page.locator("//a[contains(@class,'thread-subject')]").elementHandles()
                    for (threadLinkElement in threadLinkElements) {
                        ForumThread.new {
                            this.url = opts.baseUrl + threadLinkElement.getAttribute("href")
                            this.title = threadLinkElement.textContent().trim()
                            this.posterName = ""
                            this.forum = forum.id
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            println("Processed page $pageNum of forum titled ${forum.title}")
            page.waitForTimeout((1000 + rand.nextLong(6000, 15000)).toDouble())
        }

        //Depth first -- extract all forum posts on the current forum
        var threads: List<ForumThread>? = null
        transaction {
            threads = forum.threads.notForUpdate().toList()
        }
        for (thread in threads!!) {
            workerPool.submit {
                //We're now in the context of a thread other than main thread
                //We only create a new playwright browser if the thread doesn't already have one
                val threadPi = threadLocal.get() ?: newPlaywright()
                threadLocal.set(threadPi)
                val threadPage = threadPi.pg
                val threadCtxt = threadPi.ctxt
                threadCtxt.addCookies(loggedInCookies)

                threadPage.waitForTimeout((1000 + rand.nextLong(1000, 27000)).toDouble())
                threadPage.navigate(thread.url, Page.NavigateOptions().setTimeout(600000.0))
                val origUsernames =
                    threadPage.locator("//tr[@post_id]//a[contains(@class, 'element_username')]").elementHandles()
                transaction {
                    if (origUsernames.size == 0) {
                        thread.posterName = "UNKNOWN POSTER"
                    } else {
                        thread.posterName = origUsernames.first().textContent()
                    }
                }

                pagedLoop(threadPage) { pageNum: Int ->
                    threadPage.waitForTimeout((1000 + rand.nextLong(1000, 5000)).toDouble())
                    val postLocator = threadPage.locator("//tr[@post_id]")
                    val userNameLocator = threadPage.locator("//tr[@post_id]//a[contains(@class, 'element_username')]")
                    val quotes = threadPage.locator("//div[contains(@class,'iconf-quote-right')]").elementHandles()
                    val posteds = threadPage.locator("div.posted").elementHandles()

                    quotes.forEachIndexed { pos, quote ->
                        val textArea = threadPage.locator("//textarea[@id='content']")
                        textArea.clear()
                        quote.click()
                        holup@ for (i in 0..30) {
                            if (textArea.inputValue().trim().isNotEmpty())
                                break@holup
                            threadPage.waitForTimeout((1000 + rand.nextLong(1000, 2000)).toDouble())
                        }
                        try {
                            skipUnique {
                                Post.new {
                                    this.posterName = userNameLocator.elementHandles()[pos].textContent()
                                    this.seq = pos + 1
                                    this.url = threadPage.url()
                                    this.bbcode =
                                        postContentsRx.matchEntire(textArea.inputValue())?.groupValues?.get(1) ?: ""
                                    this.forumThread = thread.id
                                    this.posted = posteds[pos].innerText()
                                    this.enjinPostId =
                                        postLocator.elementHandles()[pos].getAttribute("post_id").toInt()
                                }
                            }
                            textArea.clear()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    println("Processed page $pageNum of forum thread titled ${thread.title}")
                    threadPage.waitForTimeout((1000 + rand.nextLong(6000, 15000)).toDouble())
                }
            }
        }
    }
    workerPool.shutdown()
    workerPool.awaitTermination(365, TimeUnit.DAYS)
}