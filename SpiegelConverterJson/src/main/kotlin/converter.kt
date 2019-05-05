import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.PrintStream
import java.util.stream.Collectors

fun main(){

    val reader: BufferedReader = File("/home/christoph/Desktop/tags.json").bufferedReader()
    val inputString: String = reader.use { it.readText() }
    val gson: Gson = Gson()
    val articles: List<Article> = gson.fromJson(inputString, object: TypeToken<List<Article>>() {}.type)

    val uniqueTags: MutableSet<String> = mutableSetOf()
    articles.forEach {
        it.tags.split(",").forEach({
            uniqueTags.add(it.trim())
        })
    }

    var links: ArrayList<Link> = ArrayList()
    uniqueTags
        .filter { tag -> tag != "" } // remove empty entries
        .forEach { tag ->
            val filteredArticles = articles.filter { art -> art.tags.contains(tag) } // only articles containing the current tag
            val map: MutableMap<String, Int> = mutableMapOf()
            for(art in filteredArticles){
                val remainingTags: List<String> = art.tags.split(",").filter { t -> t.trim() != tag }.map { t -> t.trim() }
                remainingTags.forEach { t ->
                    // count the amount of articles containing the current tag
                    if (map[t] != null) { // increment counter
                        map[t] = map[t]!! + 1
                    } else { // amount = 1 if no map is existing yet
                        map[t] = 1
                    }
                }
            }
            map
                .filter { (target, value) -> value > 10 }
                .forEach { (target, value) -> links.add(Link(tag, target, value))}
    }

    var groupedLinks: Map<String, List<Link>> = links.stream()
        .collect(Collectors.groupingBy { l -> l.source })

    var nodes: MutableList<Node> = ArrayList()
    uniqueTags
        .filter { t -> links.any { l -> l.source == t || l.target == t } }
        .forEach { t -> nodes.add(Node(t, if (groupedLinks[t] == null) 0 else groupedLinks[t]!!.size)) }

    var s: String = gson.toJson(NodeLink(nodes, links))
    val printStream: PrintStream = PrintStream("/home/christoph/Desktop/formattedData.json")
    printStream.print(s)
}

data class Article (var title: String, var tags: String)

data class Link (var source: String, var target: String, var value: Int)

data class LinkGroup(var groupName: String, var links: List<Link>)

data class Node (var id: String, var group: Int = 0)

data class NodeLink (var nodes: List<Node>, var links: List<Link>)