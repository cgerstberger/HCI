import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.File
import java.io.PrintStream
import java.util.concurrent.ConcurrentHashMap
import kotlin.streams.toList

val keywordMap: ConcurrentHashMap<String, List<Article>> = ConcurrentHashMap()
const val percentage = 0.09
const val inputPath = "/home/christoph/Desktop/HCI Data/tags.json"
const val outputPath = "/home/christoph/IdeaProjects/HCI/HCI/formattedData_09.json"

fun main(){

    val reader: BufferedReader = File(inputPath).bufferedReader()

    val inputString: String = reader.use { it.readText() }
    val gson = Gson()
    val articles: List<Article> = gson.fromJson(inputString, object: TypeToken<List<Article>>() {}.type)

//    println("calcBasicTags: " + measureTimeMillis { calcBaseTags() })
    val baseTags: Set<String> = calcBaseTags()
//    println("calcUniqueTags: " + measureTimeMillis { calcUniqueTags(articles) })
    val uniqueTags = calcUniqueTags(articles)

//    println("calcNodes: " + measureTimeMillis { calcNodes(articles, uniqueTags) })
    val nodes: List<Node> = calcNodes(articles, uniqueTags).sortedBy { node -> node.id }
//    println("calcLinks: " + measureTimeMillis { calcLinks(articles, uniqueTags) })
    var links: List<Link> = calcLinks(articles, uniqueTags)

//    println("calcPercentageLinksParallel: " + measureTimeMillis { calcPercentageLinksParallel(nodes, links) })
    val reducedLinks: List<Link> = calcPercentageLinksParallel(nodes, links)

//    println("calcGroups: " + measureTimeMillis { calcGroups(baseTags) })
    val groups: List<Group> = calcGroups(baseTags)

//    println("setGroupsToNodes: " + measureTimeMillis { setGroupsToNodes(nodes, baseTags, groups, reducedLinks) })
    setGroupsToNodes(nodes, baseTags, groups, reducedLinks)
//    println("cleanNodes: " + measureTimeMillis { cleanNodes(nodes, reducedLinks) })
    val cleanedNodes: List<Node> = cleanNodes(nodes, reducedLinks)
//    println("cleanLinks: " + measureTimeMillis { cleanLinks(reducedLinks, cleanedNodes) })
    val cleanedLinks: List<Link> = cleanLinks(reducedLinks, cleanedNodes)
//    println("cleanedNodes2: " + measureTimeMillis { cleanNodes(cleanedNodes, cleanedLinks) })
    val noDuplicateLinks: List<Link> = removeDuplicates(cleanedLinks)


    val s: String = gson.toJson(NodeLink(cleanedNodes, noDuplicateLinks))
//    val printStream = PrintStream("/home/christoph/Desktop/HCI Data/formattedData_05_1.json")
    val printStream = PrintStream(outputPath)
    printStream.print(s)
}


/**
 * Calculates the basic tags that were used to find the articles.
 */
fun calcBaseTags(): Set<String> {
    println("calcBaseTags(...) started")
    return "Politik, Meinung, Wirtschaft, Panorama, Sport, Kultur, Netzwelt, Wissenschaft, Gesundheit, Reise"
            .split(",")
            .map { t -> t.trim() }
            .toCollection(hashSetOf())
}

fun calcUniqueTags(articles: List<Article>): Set<String> {
    println("calcUniqueTags(...) started")
    val uniqueTags: MutableSet<String> = mutableSetOf()
    articles.forEach { art ->
        art.tags.split(",").forEach{ tag ->
            val trimmedStr = tag.trim()
            if(trimmedStr.isNotBlank())
                uniqueTags.add(trimmedStr)
        }
    }
    return uniqueTags
}

fun calcNodes(articles: List<Article>, uniqueTags: Set<String>): List<Node> {
    println("calcNodes(...) started")
    var time = System.currentTimeMillis();
    return uniqueTags
        .parallelStream()
        .map{ tag ->
            val tagArticles: List<Article> = articles.filter { a -> a.tags.contains(tag) }
            keywordMap[tag] = tagArticles

            val node = Node(tag)
            node.nrArticles = tagArticles.size
            node
        }.toList()
}

fun calcLinks(articles: List<Article>, uniqueTags: Set<String>): List<Link> {
    println("calcLinks(...) started")
    return uniqueTags.mapIndexed { index, source ->
        val sourceArticles: List<Article>? = keywordMap[source]
        val subLinks: List<Link> = uniqueTags
            .parallelStream()
            .filter { target -> target != source }
            .map { target ->
                val link = Link(source = source, target = target)
                link.value = sourceArticles!!.count { a -> a.tags.contains(target) }
                link
            }
            .filter { link ->
                link.value > 0
            }
            .toList()
//        println("links for $source done (${index+1}/${uniqueTags.size})")
        subLinks
    }.flatten()
}

fun calcLinksParallel(articles: List<Article>, uniqueTags: Set<String>): List<Link> {
    println("calcLinksParallel(...) started")
    return uniqueTags
        .parallelStream()
        .map { source ->
        val sourceArticles: List<Article>? = keywordMap[source]
        val subLinks: List<Link> = uniqueTags
            .filter { target -> target != source }
            .map { target ->
                val link = Link(source = source, target = target)
                link.value = sourceArticles!!.count { a -> a.tags.contains(target) }
                link
            }
            .filter { link ->
                link.value > 0
            }
//        println("links for $source done")
        subLinks
    }.toList().flatten()
}

private fun calcPercentageLinksParallel(nodes: List<Node>, links: List<Link>): List<Link> {
    println("calcPercentageLinks(...) started")
    val linksSubset: MutableList<Link> = arrayListOf()
    nodes
        .parallelStream()
        .forEach { node ->
            val nodeLinks: List<Link> = links
                .filter { l -> l.source == node.id }
                .sortedByDescending { l -> l.value }
            val reducedLinks = nodeLinks
                .subList(0, (nodeLinks.size * percentage).toInt())
            linksSubset.addAll(reducedLinks)
        }
    return linksSubset
}

private fun calcGroups(baseTags: Set<String>): List<Group> {
    println("calcGroups(...) started")
    return baseTags
        .mapIndexed { index, tag ->
            Group(index + 1, tag)
        }

}

private fun setGroupsToNodes(
    nodes: List<Node>,
    baseTags: Set<String>,
    groups: List<Group>,
    reducedLinks: List<Link>
) {
    println("setGroupsToNodes(...) started")
    nodes
        .parallelStream()
        .forEach { node ->
            if (baseTags.contains(node.id)) {
                node.group = groups.single { g -> g.name == node.id }.id
            } else {
                var orderedLinks = reducedLinks
                    .filter { l -> l.source == node.id && baseTags.contains(l.target) }

                val sortedOrderedLinks = orderedLinks
                    .sortedByDescending { l -> l.value }
                if (sortedOrderedLinks.isNotEmpty())
                    node.group = groups.single { g -> g.name == sortedOrderedLinks[0].target }.id

            }
        }
}

private fun cleanNodes(
    nodes: List<Node>,
    reducedLinks: List<Link>
): List<Node> {
    println("cleanNodes(...) started")
    return nodes
        .parallelStream()
        .filter { node -> node.group > 0 }
        .filter { node ->
            reducedLinks.any { link -> link.source == node.id || link.target == node.id }
        }
        .toList()
}

private fun cleanLinks(reducedLinks: List<Link>,
                       cleanNodes: List<Node>): List<Link> {
    println("cleanLinks(...) started")
    return reducedLinks
        .parallelStream()
//        .filter { link -> link.source.isNotBlank() && link.target.isNotBlank() }
        .filter { link -> cleanNodes.any { node -> node.id == link.source } && cleanNodes.any { node -> node.id == link.target } }
//        .filter { link -> !reducedLinks.any { secLink -> link.source == secLink. target && link.target == secLink.source } }
        .toList()
}

private fun removeDuplicates(cleanLinks: List<Link>): List<Link> {
    // (source -> target && source <- target)
    val newList: MutableList<Link> = mutableListOf()
    for (link in cleanLinks){
        val hasDuplicate = newList
            .any { link2 ->
                (link.source == link2.source && link.target == link2.target) ||
                        (link.source == link2.target && link.target == link2.source) }
        if (!hasDuplicate)
            newList.add(link)
    }
    return newList
}


data class Article (var title: String, var tags: String)

data class Link (var source: String = "", var target: String = "", var value: Int = 0)

data class LinkGroup(var groupName: String, var links: List<Link>)

data class Node (var id: String, var group: Int = 0, var nrArticles: Int = 0)

data class NodeLink (var nodes: List<Node>, var links: List<Link>)

data class Group (var id: Int = 0, var name: String = "")