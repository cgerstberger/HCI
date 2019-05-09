import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.PrintStream
import kotlin.streams.toList

fun main(){

    val reader: BufferedReader = File("/home/christoph/Desktop/HCI Data/tags.json").bufferedReader()
//    val readNodes: BufferedReader = File("/home/christoph/Desktop/HCI Data/nodes.json").bufferedReader()
//    val writeNodes: PrintStream = PrintStream("/home/christoph/Desktop/HCI Data/nodes.json");
//    val readLinks: BufferedReader = File("/home/christoph/Desktop/HCI Data/links.json").bufferedReader()
//    val writeLinks: PrintStream = PrintStream("/home/christoph/Desktop/HCI Data/links.json");

    val inputString: String = reader.use { it.readText() }
    val gson = Gson()
    val articles: List<Article> = gson.fromJson(inputString, object: TypeToken<List<Article>>() {}.type)

//    println("calcBasicTags: " + measureTimeMillis { calcBasicTags(articles) })
    val baseTags: Set<String> = calcBaseTags()
//    println("calcUniqueTags: " + measureTimeMillis { calcUniqueTags(articles) })
    val uniqueTags = calcUniqueTags(articles)

//    saving nodes and links in files for less operations
//    val nodesString: String = readNodes.use { it.readText() }
//    val nodes: List<Node> = gson.fromJson(nodesString, object: TypeToken<List<Node>>() {}.type)
//    val linksString: String = readLinks.use { it.readText() }
//    val links: List<Link> = gson.fromJson(linksString, object: TypeToken<List<Link>>() {}.type)
//    println(nodes)
//    println(links)

//    println("calcNodes: " + measureTimeMillis { calcNodes(articles, uniqueTags) })
    val nodes: List<Node> = calcNodes(articles, uniqueTags).sortedBy { node -> node.id }
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
    val cleanedNodes: List<Node> = cleanNodes(nodes, twentyPercentLinks)
//    println("cleanLinks: " + measureTimeMillis { cleanLinks(twentyPercentLinks) })
    val cleanedLinks: List<Link> = cleanLinks(twentyPercentLinks, cleanedNodes)

    cleanedLinks.forEach { link ->
        var nodeFound: Boolean = false;
        for(node in cleanedNodes){
            if (node.id == link.source)
                nodeFound = true
        }
        if(!nodeFound)
            println("missing node for $link")
    }

    cleanedNodes.forEach { node ->
        var linkFound: Boolean = false;
        for(link in cleanedLinks){
            if(link.source == node.id || link.target == node.id)
                linkFound = true
        }
        if(!linkFound)
            println("missing link for $node")
    }

    val s: String = gson.toJson(NodeLink(cleanedNodes, cleanedLinks))
    val printStream = PrintStream("/home/christoph/Desktop/HCI Data/formattedData.json")
    printStream.print(s)
}


/**
 * Calculates the basic tags that were used to find the articles.
 */
fun calcBaseTags(): Set<String> {
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
    return uniqueTags
        .filter { tag -> tag.isNotBlank() }
        .map{ tag ->
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

private fun cleanNodes(
    nodes: List<Node>,
    twentyPercentLinks: List<Link>
): List<Node> {
    return nodes
//        .filter { node -> node.group > 0 }
        .filter { node ->
            twentyPercentLinks.any { link -> link.source == node.id || link.target == node.id }
        }
}

private fun cleanLinks(twentyPercentLinks: List<Link>,
                       cleanNodes: List<Node>): List<Link> {
    return twentyPercentLinks.
        filter { link ->
            link.source.isNotBlank() && link.target.isNotBlank()
        }
        .filter { link -> cleanNodes.any { node -> node.id == link.source } }
}


data class Article (var title: String, var tags: String)

data class Link (var source: String = "", var target: String = "", var value: Int = 0)

data class LinkGroup(var groupName: String, var links: List<Link>)

data class Node (var id: String, var group: Int = 0, var nrArticles: Int = 0)

data class NodeLink (var nodes: List<Node>, var links: List<Link>)

data class Group (var id: Int = 0, var name: String = "")