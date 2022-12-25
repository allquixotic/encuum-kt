import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable

data class ScrapeOpts(val headless: Boolean, val baseUrl: String, val username: String, val password: String, val forumBase: String)

object Forums: IntIdTable() {
    val url = text("url").default("")
    val title = text("title").default("")
    val idx = uniqueIndex(url, title)
}
class Forum(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Forum>(Forums)
    var url by Forums.url
    var title by Forums.title
    val threads by ForumThread referrersOn ForumThreads.forum
}

object ForumThreads: IntIdTable() {
    val url = text("url").default("")
    val title = text("title").default("")
    val posterName = text("posterName")
    val forum = reference("forum", Forums)
    val idx = uniqueIndex(url, title, posterName, forum)
}

class ForumThread(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<ForumThread>(ForumThreads)
    var url by ForumThreads.url
    var title by ForumThreads.title
    var posterName by ForumThreads.posterName
    var forum by ForumThreads.forum
    val posts by Post referrersOn Posts.forumThread
}

object Posts: IntIdTable() {
    val url = text("url").default("")
    val seq = integer("seq").default(1)
    val posterName = text("posterName").default("")
    val bbcode = text("bbcode").default("")
    val posted = text("posted").default("")
    val enjinPostId = integer("enjinPostId").default(0)
    val forumThread = reference("forumThread", ForumThreads)
    val idx = uniqueIndex(enjinPostId, url, seq, posterName, bbcode, forumThread)
}

class Post(id: EntityID<Int>): IntEntity(id) {
    companion object : IntEntityClass<Post>(Posts)
    var url by Posts.url
    var seq by Posts.seq
    var posterName by Posts.posterName
    var bbcode by Posts.bbcode
    var posted by Posts.posted
    var enjinPostId by Posts.enjinPostId
    var forumThread by Posts.forumThread
}