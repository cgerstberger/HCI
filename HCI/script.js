
var svg = d3.select("svg"),
  width = +svg.node().getBoundingClientRect().width
height = +svg.node().getBoundingClientRect().height

svg = svg.call(d3.zoom().on("zoom", function () {
  svg.attr("transform", d3.event.transform)
})).on("dblclick.zoom", null)
  .append("g")

//Load Data
d3.json("./formattedData_08.json", function (error, _graph) {
  if (error) throw error;

  //Sort for correct rendering order
  _graph.nodes.sort(function (a, b) {
    return a.nrArticles - b.nrArticles;
  });

  graph = _graph

  groupTags = ["", "Kultur", "Netzwelt", "Sport", "Panorama", "Politik", "Meinung", "Wirtschaft", "Gesundheit", "Reise", "Wissenschaft"]
  nodeById = {};
  groupNodes = {};
  groupedNodes = {};
  linkNodeSources = {};
  linkNodeTargets = {};

  graph.nodes.forEach(n => {
    nodeById[n.id] = n;
    if (!(n.group in groupNodes))
      groupNodes[n.group] = [];

    groupNodes[n.group].push(n);
  });

  graph.links.forEach(l => {
    sourceNode = nodeById[l.source]
    l.prevSource = sourceNode;
    if (!(sourceNode.id in linkNodeSources))
      linkNodeSources[sourceNode.id] = [];

    linkNodeSources[sourceNode.id].push(l);

    targetNode = nodeById[l.target]
    l.prevTarget = targetNode;
    if (!(targetNode.id in linkNodeTargets))
      linkNodeTargets[targetNode.id] = [];

    linkNodeTargets[targetNode.id].push(l);
  })

  console.log(nodeById)

  //Init fuzy search
  initFuzy();
  //Init elements to display
  initDisplay();
  //Init simulation elements
  initSimulation();
});

function testCopy(src) {
  return Object.assign({}, src);
}

var color = d3.scaleOrdinal(d3.schemeCategory20);
var currentHighlight = undefined;



////////// FORCE SIMULATION ////////// 
var simulation = d3.forceSimulation();

function initSimulation() {
  simulation.nodes(graph.nodes)
  initForces();
  simulation.on("tick", ticked)
}

// values for all forces
forceProperties = {
  center: {
    x: 0.5,
    y: 0.5
  },
  charge: {
    enabled: true,
    strength: -30,
    distanceMin: 1,
    distanceMax: 2000
  },
  collide: {
    enabled: true,
    circleRadius: true,
    strength: .7,
    iterations: 1,
    radius: 5
  },
  forceX: {
    enabled: false,
    strength: .1,
    x: .5
  },
  forceY: {
    enabled: false,
    strength: .1,
    y: .5
  },
  link: {
    enabled: true,
    dynamicDistance: false,
    distance: 30,
    iterations: 1
  },
  highlight: {
    nodeOpacity: 0.3,
    linkOpacity: 0.1
  },
}

function initForces() {
  simulation
    .force("link", d3.forceLink())
    .force("charge", d3.forceManyBody())
    .force("center", d3.forceCenter())
    .force('collision', d3.forceCollide());
  updateForces();
}

function updateForces() {
  simulation.force("center")
    .x(width * forceProperties.center.x)
    .y(height * forceProperties.center.y);
  simulation.force("charge")
    .strength(forceProperties.charge.strength * forceProperties.charge.enabled)
    .distanceMin(forceProperties.charge.distanceMin)
    .distanceMax(forceProperties.charge.distanceMax);
  simulation.force("collision")
    .strength(forceProperties.collide.strength * forceProperties.collide.enabled)
    .radius(forceProperties.collide.circleRadius ? function (d) { return Math.sqrt(d.nrArticles * 3) + 3 } : forceProperties.collide.radius)
    .iterations(forceProperties.collide.iterations)
  simulation.force("link")
    .id(function (d) { return d.id })
    .distance(forceProperties.link.dynamicDistance ? function (l) { return l.value } : forceProperties.link.distance)
    .iterations(forceProperties.link.iterations)
    .links(forceProperties.link.enabled ? graph.links : [])

  simulation.alpha(1).restart();
}

////////// DISPLAY //////////
function initDisplay() {
  link = svg.append("g")
    .attr("class", "links")
    .selectAll(".link")

  node = svg.append("g")
    .attr("class", "nodes")
    .selectAll(".node")


  update();
  updateDisplay();
}

function update() {
  link = link.data(graph.links, function (d) { return d.source.id + "-" + d.target.id; });
  link.exit().remove();
  link = link.enter().append("line")
    .attr("stroke-width", function (d) { return Math.sqrt(d.value * 2); })
    .merge(link);

  node = svg.select(".nodes").selectAll("g").data(graph.nodes, function (d) { return d.id; });

  var nodeExit = node.exit().remove();

  nodeEnter = node.enter().append("g").merge(node);

  circles = nodeEnter.append("circle")
    .attr("r", function (d) { return Math.sqrt(d.nrArticles * 3) + 2 })
    .attr("fill", function (d) { return color(d.group); })

    .call(d3.drag()
      .on("start", dragstarted)
      .on("drag", dragged)
      .on("end", dragended))
    .on("click", function (d) {
      // if double click timer is active, this click is the double click
      if (dblclick_timer) {
        clearTimeout(dblclick_timer)
        dblclick_timer = false
        removeGroup(d)
      }
      // otherwise, what to do after single click (double click has timed out)
      else dblclick_timer = setTimeout(function () {
        dblclick_timer = false
        highlight(d)
      }, 250)
    })

  nodeEnter.append("title")
    .text(function (d) { return d.id; });

  lables = nodeEnter.append("text")
    .text(function (d) {
      return d.id;
    })
    .attr("text-anchor", "middle")
    .attr("pointer-events", "none")
    .attr("dy", ".35em")
    .style('font-size', function (d) { return Math.sqrt(d.nrArticles); });

  simulation.nodes(graph.nodes)
  simulation.alpha(1).restart();
}

dblclick_timer = false

function updateDisplay() {
  link
    .style("opacity", forceProperties.link.enabled ? 1 : 0);

}
function ticked() {
  link
    .attr("x1", function (d) { return d.source.x; })
    .attr("y1", function (d) { return d.source.y; })
    .attr("x2", function (d) { return d.target.x; })
    .attr("y2", function (d) { return d.target.y; });

  nodeEnter
    .attr("transform", function (d) {
      return "translate(" + d.x + "," + d.y + ")";
    });
}

////////// UI EVENTS ////////// 
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

// update size-related forces
d3.select(window).on("resize", function () {
  width = +svg.node().getBoundingClientRect().width;
  height = +svg.node().getBoundingClientRect().height;
  updateForces();
});

function updateAll() {
  updateForces();
  updateDisplay();
}

function isConnected(d, a) {
  return d.id === a.id || d.id in linkNodeSources && linkNodeSources[d.id].some(l => l.target.id === a.id) || d.id in linkNodeTargets && linkNodeTargets[d.id].some(l => l.source.id === a.id);
}

function updateHighlight() {
  nodeEnter.style('opacity', (n) => {
    return isConnected(currentHighlight, n) ? 1 : forceProperties.highlight.nodeOpacity;
  });
  link.style('opacity', l => (l.source === currentHighlight || l.target === currentHighlight) ? 1 : forceProperties.highlight.linkOpacity);
}

function search(input) {
  input = document.getElementById("searchField").value;
  currentHighlight = null;
  if(input) {
    var searchResult = fuse.search(input);
    console.log(searchResult);
    nodeEnter.style('opacity', (n) => {
      return searchResult.includes(n) ? 1 : forceProperties.highlight.nodeOpacity;
    });
    link.style('opacity', 0.1);
  } else {
    nodeEnter.style('opacity', 1);
    link.style('opacity', 1);
  }
}

function highlight(d) {
  console.log(linkNodeSources[d.id])
  if (d === currentHighlight) {
    nodeEnter.style('opacity', 1);
    link.style('opacity', 1);
    currentHighlight = null;
  } else {
    nodeEnter.style('opacity', (n) => {
      return isConnected(d, n) ? 1 : forceProperties.highlight.nodeOpacity;
    });
    link.style('opacity', l => (l.source === d || l.target === d) ? 1 : forceProperties.highlight.linkOpacity);
    currentHighlight = d;
  }
}

function removeGroup(d) {
  nodeEnter.style('opacity', 1);
  link.style('opacity', 1);
  currentHighlight = null;
  
  if (d.group in groupedNodes) {
    groupedGraphNode = groupedNodes[d.group]
    groupNodes[d.group].forEach(n => {
      n.x = groupedGraphNode.x + Math.abs(n.x - groupedGraphNode.oldX);
      n.y = groupedGraphNode.y + Math.abs(n.y - groupedGraphNode.oldY);
    });

    graph.nodes.push(...groupNodes[d.group])

    graph.links.filter(l => l.source == groupedGraphNode).forEach(l => {
      l.source = l.prevSource;
    })
    graph.links.filter(l => l.target == groupedGraphNode).forEach(l => {
      l.target = l.prevTarget;
    })

    delete linkNodeSources[groupedGraphNode.id]
    delete linkNodeTargets[groupedGraphNode.id] 

    graph.nodes.splice(graph.nodes.indexOf(groupedGraphNode), 1);
    delete groupedNodes[d.group]
  } else {
    console.log(d.group)
    let newGroupGraph = { id: "." + groupTags[d.group], group: d.group, nrArticles: 0, vx: 0, xy: 0, x: 500, y: 500 };
    let sumArticles = 0;
    let centerX = 0;
    let centerY = 0;

    linkNodeSources[newGroupGraph.id] = []
    linkNodeTargets[newGroupGraph.id] = []
    groupNodes[d.group].forEach(n => {
      centerX += n.x;
      centerY += n.y;
      sumArticles += n.nrArticles;

      if (n.id in linkNodeSources)
        linkNodeSources[n.id].forEach(l => {
          l.source = newGroupGraph;
          linkNodeSources[newGroupGraph.id].push(l);
        });

      if (n.id in linkNodeTargets)
        linkNodeTargets[n.id].forEach(l => {
          l.target = newGroupGraph;
          linkNodeTargets[newGroupGraph.id].push(l);
        });
    });

    console.log(linkNodeTargets[newGroupGraph.id])

    newGroupGraph.x = centerX / groupNodes[d.group].length;
    newGroupGraph.y = centerY / groupNodes[d.group].length;
    newGroupGraph.nrArticles = sumArticles;
    newGroupGraph.oldX = newGroupGraph.x
    newGroupGraph.oldY = newGroupGraph.y

    let group = graph.nodes.filter(n => n.group !== d.group);

    group.push(newGroupGraph);

    graph.nodes = group.sort(function (a, b) {
      return a.nrArticles - b.nrArticles;
    });

    groupedNodes[d.group] = newGroupGraph;
  }

  update();
}

///////// FUZY SEARCH ////////// 
function initFuzy() {
  var options = {
    shouldSort: true,
    threshold: 0.2,
    location: 0,
    distance: 100,
    maxPatternLength: 32,
    minMatchCharLength: 2,
    keys: [
      "id"
    ]
  };
  fuse = new Fuse(graph.nodes, options); 
}





