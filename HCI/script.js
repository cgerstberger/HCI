var svg = d3.select("svg"),
    width = +svg.attr("width"),
    height = +svg.attr("height");

svg = svg.call(d3.zoom().on("zoom", function () {
  svg.attr("transform", d3.event.transform)
}))
.append("g")

var color = d3.scaleOrdinal(d3.schemeCategory20);

var simulation = d3.forceSimulation()
    .force("link", d3.forceLink().distance(linkDistance).id(function(d) { return d.id; }))
    .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter(width / 2, height / 2))
    .force('collision', d3.forceCollide().radius(function(d) {
      return Math.sqrt(d.nrArticles*3) + 3
    }));

function linkDistance() {
  return 50;
}

function dragstarted(d) {
  if (!d3.event.active) simulation.alphaTarget(0.3).restart();
  d.fx = d.x;
  d.fy = d.y;
}

function dragged(d) {
  d.fx = d3.event.x;
  d.fy = d3.event.y;
}

function dragended(d) {
  if (!d3.event.active) simulation.alphaTarget(0);
  d.fx = null;
  d.fy = null;
}

d3.json("./formattedData_05.json", function(error, graph){
  if (error) throw error;

  //Sort for correct rendering order
  graph.nodes.sort(function(a, b) {
    return a.nrArticles - b.nrArticles;
  });

  var link = svg.append("g")
      .attr("class", "links")
    .selectAll("line")
    .data(graph.links)
    .enter().append("line")
      .attr("stroke-width", function(d) { return Math.sqrt(d.value); });

  var node = svg.append("g")
      .attr("class", "nodes")
    .selectAll("g")
    .data(graph.nodes)
    .enter().append("g")

  var circles = node.append("circle")
      .attr("r", function(d){ return Math.sqrt(d.nrArticles*3) + 2 })
      .attr("fill", function(d) { return color(d.group); })

      .call(d3.drag()
          .on("start", dragstarted)
          .on("drag", dragged)
          .on("end", dragended));

  node.append("title")
      .text(function(d) { return d.id; });

  var lables = node.append("text")
      .text(function(d) {
        return d.id;
      })
      .attr("text-anchor", "middle")
      .attr("pointer-events", "none")
      .attr("dy", ".35em")
      .style('font-size', function(d) { return Math.sqrt(d.nrArticles); });

  simulation
      .nodes(graph.nodes)
      .on("tick", ticked);

  simulation.force("link")
      .links(graph.links);

  function ticked() {
    link
        .attr("x1", function(d) { return d.source.x; })
        .attr("y1", function(d) { return d.source.y; })
        .attr("x2", function(d) { return d.target.x; })
        .attr("y2", function(d) { return d.target.y; });

    node
        .attr("transform", function(d) {
          return "translate(" + d.x + "," + d.y + ")";
        });

    node.select("text").raise();
  }
});
