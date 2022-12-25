import com.fasterxml.jackson.databind.ObjectMapper
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.DatabaseConfig
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.name
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File
import java.io.IOException

object DB {
    val db by lazy {
        Database.connect(url = "jdbc:sqlite:forum.db", databaseConfig = DatabaseConfig{ useNestedTransactions = true })
    }
}

fun mkTables() {
    println("Connected to ${DB.db.name}")
    transaction {
        SchemaUtils.create(Forums, ForumThreads, Posts)
    }
}

fun writeForumsToFiles(forums: List<Forum>) {
    for (f in forums) {
        writeForum(f)
    }
}

fun writeForum(forum: Forum) {
    val filename = forum.title.replace(Regex("[^A-Za-z0-9 ]"), "")
    val fo = File(filename)
    try {
        val objectMapper = ObjectMapper()
        objectMapper.writeValue(fo, forum)
    } catch (e: IOException) {
        println("ERROR writing file $filename")
    }
}