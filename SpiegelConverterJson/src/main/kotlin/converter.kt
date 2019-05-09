import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.PrintStream
import kotlin.streams.toList

fun main(){

    val reader: BufferedReader = File("/home/christoph/Desktop/HCI Data/tags-small.json").bufferedReader()
//    val readNodes: BufferedReader = File("/home/christoph/Desktop/HCI Data/nodes.json").bufferedReader()
//    val writeNodes: PrintStream = PrintStream("/home/christoph/Desktop/HCI Data/nodes.json");
//    val readLinks: BufferedReader = File("/home/christoph/Desktop/HCI Data/links.json").bufferedReader()
//    val writeLinks: PrintStream = PrintStream("/home/christoph/Desktop/HCI Data/links.json");

    val inputString: String = reader.use { it.readText() }
    val gson = Gson()
    val articles: List<Article> = gson.fromJson(inputString, object: TypeToken<List<Article>>() {}.type)

//    println("calcBasicTags: " + measureTimeMillis { calcBasicTags(articles) })
    val baseTags: Set<String> = calcBasicTags()
//    println("calcUniqueTags: " + measureTimeMillis { calcUniqueTags(articles) })
    val uniqueTags = calcUniqueTags(articles)


//    val nodesString: String = readNodes.use { it.readText() }
//    val nodes: List<Node> = gson.fromJson(nodesString, object: TypeToken<List<Node>>() {}.type)
//    val linksString: String = readLinks.use { it.readText() }
//    val links: List<Link> = gson.fromJson(linksString, object: TypeToken<List<Link>>() {}.type)
//    println(nodes)
//    println(links)

//    println("calcNodes: " + measureTimeMillis { calcNodes(articles, uniqueTags) })
    val nodes: List<Node> = calcNodes(articles, uniqueTags)
//    writeNodes.print(gson.toJson(nodes))
//    println("calcLinks: " + measureTimeMillis { calcLinks(articles, uniqueTags) })
    val links: List<Link> = calcLinks(articles, uniqueTags)
//    writeLinks.print(gson.toJson(links))

//    println("calcPercentageLinks: " + measureTimeMillis { calcPercentageLinks(nodes, links) })
    val twentyPercentLinks: List<Link> = calcPercentageLinks(nodes, links)

//    println("calcGroups: " + measureTimeMillis { calcGroups(baseTags) })
    val groups: List<Group> = calcGroups(baseTags)

//    println("setGroupsToNodes: " + measureTimeMillis { setGroupsToNodes(nodes, baseTags, groups, twentyPercentLinks) })
    setGroupsToNodes(nodes, baseTags, groups, twentyPercentLinks)
//    println("cleanNodes: " + measureTimeMillis { cleanNodes(nodes, twentyPercentLinks) })
    val cleanedNodes = cleanNodes(nodes, twentyPercentLinks)
//    println("cleanLinks: " + measureTimeMillis { cleanLinks(twentyPercentLinks) })
    val cleanedLinks = cleanLinks(twentyPercentLinks)

    val s: String = gson.toJson(NodeLink(cleanedNodes, cleanedLinks))
    val printStream = PrintStream("/home/christoph/Desktop/HCI Data/formattedData.json")
    printStream.print(s)

//    var links: List<Link> = calcLinks(uniqueTags, articles)
//    var nodes: List<Node> = calcNodes(articles, uniqueTags)
//
//    var groupedLinks: Map<String, List<Link>> = links.stream()
//        .collect(Collectors.groupingBy { l -> l.source })
//
//    var nodes: MutableList<Node> = ArrayList()
//    uniqueTags
//        .filter { t -> links.any { l -> l.source == t || l.target == t } }
//        .forEach { t -> nodes.add(Node(t, if (groupedLinks[t] == null) 0 else groupedLinks[t]!!.size)) }
//
//    var s: String = gson.toJson(NodeLink(nodes, links))
//    val printStream: PrintStream = PrintStream("/home/christoph/Desktop/formattedData.json")
//    printStream.print(s)
}


/**
 * Calculates the basic tags that were used to find the articles.
 */
fun calcBasicTags(): Set<String> {
    return "Politik, Meinung, Wirtschaft, Panorama, Sport, Kultur, Netzwelt, Wissenschaft, Gesundheit, Reise"
            .split(",")
            .map { t -> t.trim() }
            .toCollection(hashSetOf())
}

fun calcUniqueTags(articles: List<Article>): Set<String> {
    val uniqueTags: MutableSet<String> = mutableSetOf()
    articles.forEach { art ->
        art.tags.split(",").forEach{ tag ->
            uniqueTags.add(tag.trim())
        }
    }
    return uniqueTags
}

fun calcNodes(articles: List<Article>, uniqueTags: Set<String>): List<Node> {
    return uniqueTags.map{ tag ->
        val node = Node(tag)
        node.nrArticles = articles.count { a -> a.tags.contains(tag) }
        node
    }
}

fun calcLinks(articles: List<Article>, uniqueTags: Set<String>): List<Link> {
    return uniqueTags.map { source ->
        val subLinks: List<Link> = uniqueTags
            .parallelStream()
            .filter { target -> target != source }
            .map { target ->
                val link = Link(source = source, target = target)
                link.value = articles.count { a -> a.tags.contains(source) && a.tags.contains(target) }
                link
            }
            .filter { link ->
                link.value > 0
            }
            .toList()
        subLinks
    }.flatten()
}

private fun calcPercentageLinks(nodes: List<Node>, links: List<Link>): List<Link> {
    val percentage = 0.2
    val linksSubset: MutableList<Link> = arrayListOf()
    nodes.forEach { node ->
        val nodeLinks: List<Link> = links
            .filter { l -> l.source == node.id }
            .sortedByDescending { l -> l.value }
        val twentyPercentList = nodeLinks.subList(0, (nodeLinks.size * percentage).toInt())
        linksSubset.addAll(twentyPercentList)
    }
    return linksSubset
}

private fun calcGroups(baseTags: Set<String>): List<Group> {
    return baseTags.mapIndexed { index, tag ->
        Group(index + 1, tag)
    }
}

private fun setGroupsToNodes(
    nodes: List<Node>,
    baseTags: Set<String>,
    groups: List<Group>,
    twentyPercentLinks: List<Link>
) {
    for (node in nodes) {
        if (baseTags.contains(node.id)) {
            node.group = groups.single { g -> g.name == node.id }.id
            continue
        }

        val orderedLinks = twentyPercentLinks
            .filter { l -> l.source == node.id && baseTags.contains(l.target) }
        val sortedOrderedLinks = orderedLinks
            .sortedByDescending { l -> l.value }
        if (sortedOrderedLinks.isNotEmpty())
            node.group = groups.single { g -> g.name == sortedOrderedLinks[0].target }.id
    }
}

private fun cleanLinks(twentyPercentLinks: List<Link>): List<Link> {
    return twentyPercentLinks.
        filter { link ->
            link.source.isNotBlank() && link.target.isNotBlank()
    }
}

private fun cleanNodes(
    nodes: List<Node>,
    twentyPercentLinks: List<Link>
): List<Node> {
    return nodes
        .filter { node ->
//            node.group != 0 &&
            twentyPercentLinks.any { link -> link.source == node.id || link.target == node.id }
        }
}


data class Article (var title: String, var tags: String)

data class Link (var source: String = "", var target: String = "", var value: Int = 0)

data class LinkGroup(var groupName: String, var links: List<Link>)

data class Node (var id: String, var group: Int = 0, var nrArticles: Int = 0)

data class NodeLink (var nodes: List<Node>, var links: List<Link>)

data class Group (var id: Int = 0, var name: String = "")



//var groupedLinks: Map<String, List<Link>> = links.stream()
//    .collect(Collectors.groupingBy { l -> l.source })
//
//fun calcGroupedLinks

//val calcIt: (List<Article>) -> (Set<String>) = {
//    val uniqueTags: MutableSet<String> = mutableSetOf()
//    it.forEach {
//        it.tags.split(",").forEach({
//            uniqueTags.add(it.trim())
//        })
//    }
//    uniqueTags
//}

//fun calcLinks(uniqueTags: Set<String>, articles: List<Article>): List<Link> {
//    var links: ArrayList<Link> = ArrayList()
//    uniqueTags
//        .filter { tag -> tag != "" } // remove empty entries
//        .forEach { tag ->
//            // iterate over every tag
//            // find all articles, where the tag is included
//            val filteredArticles = articles.filter { art -> art.tags.contains(tag) }
//
//            val map: MutableMap<String, Int> = mutableMapOf()
//            for(art in filteredArticles){
//                val remainingTags: List<String> = art.tags.split(",").filter { t -> t.trim() != tag }.map { t -> t.trim() }
//                remainingTags.forEach { t ->
//                    // count the amount of articles containing the current tag
//                    if (map[t] != null) { // increment counter
//                        map[t] = map[t]!! + 1
//                    } else { // amount = 1 if no map is existing yet
//                        map[t] = 1
//                    }
//                }
//            }
//            map
//                .filter { (target, value) -> value > 10 }
//                .forEach { (target, value) -> links.add(Link(tag, target, value))}
//        }
//    return links
//}
