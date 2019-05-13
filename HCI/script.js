
var svg = d3.select("svg"),
  width = +svg.node().getBoundingClientRect().width
height = +svg.node().getBoundingClientRect().height

svg = svg.call(d3.zoom().on("zoom", function () {
  svg.attr("transform", d3.event.transform)
}))
  .append("g")

//Load Data
d3.json("./formattedData_07.json", function (error, _graph) {
  if (error) throw error;

  graph = _graph

  //Sort for correct rendering order
  graph.nodes.sort(function (a, b) {
    return a.nrArticles - b.nrArticles;
  });

  //Init elements to display
  initDisplay();
  //Init simulation elements
  initSimulation();
});


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
  }
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
  var color = d3.scaleOrdinal(d3.schemeCategory20);

  link = svg.append("g")
    .attr("class", "links")
    .selectAll("line")
    .data(graph.links)
    .enter().append("line")
    .attr("stroke-width", function (d) { return Math.sqrt(d.value); });

  node = svg.append("g")
    .attr("class", "nodes")
    .selectAll("g")
    .data(graph.nodes)
    .enter().append("g")

  circles = node.append("circle")
    .attr("r", function (d) { return Math.sqrt(d.nrArticles * 3) + 2 })
    .attr("fill", function (d) { return color(d.group); })

    .call(d3.drag()
      .on("start", dragstarted)
      .on("drag", dragged)
      .on("end", dragended));

  node.append("title")
    .text(function (d) { return d.id; });

  lables = node.append("text")
    .text(function (d) {
      return d.id;
    })
    .attr("text-anchor", "middle")
    .attr("pointer-events", "none")
    .attr("dy", ".35em")
    .style('font-size', function (d) { return Math.sqrt(d.nrArticles); });

  updateDisplay();
}

function updateDisplay() {
  node
    .attr("r", forceProperties.collide.radius)

  link
    .attr("opacity", forceProperties.link.enabled ? 1 : 0);

}
function ticked() {
  link
    .attr("x1", function (d) { return d.source.x; })
    .attr("y1", function (d) { return d.source.y; })
    .attr("x2", function (d) { return d.target.x; })
    .attr("y2", function (d) { return d.target.y; });

  node
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



