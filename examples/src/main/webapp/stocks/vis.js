/*
 * Copyright (c) 2014 streamdrill UG (haftungsbeschraenkt). All rights reserved.
 */

(function ($) {
  $(function () {
    var vis = $("#vis");
    var width = vis.width(), height = vis.height();

    var force = d3.layout.force()
        .charge(-300)
        .linkDistance(function (d) { return d.source.name.length * 30 ; })
        .size([width, height]);
    d3.timer(force.resume);

    var svg = d3.select("#vis").append("svg")
        .attr("width", width)
        .attr("height", height);

    var link = svg.selectAll(".link");
    var node = svg.selectAll(".node");

    var updating;

    function update() {
      if (!updating) setTimeout(reload, 1);
    }

    var heat = d3.scale.log().range([100, 0]).domain([0.09, 0.03]);
    force.on("tick", function (d) {
      if(d.alpha < 0.01) update();
      d3.select("#friction").select("div").style("width", heat(d.alpha) + "%");
      link.attr("x1", function (d) { return d.source.x; })
          .attr("y1", function (d) { return d.source.y; })
          .attr("x2", function (d) { return d.target.x; })
          .attr("y2", function (d) { return d.target.y; });

      node.attr("transform", function (d) { return "translate(" + d.x + "," + d.y + ")"; });
    });
    var colors = d3.scale.log().range(['#ddd', '#2f94cd']);
    var lineWidth = d3.scale.linear().range([0.5, 15]);
    //var circleWidth = d3.scale.linear().range([5, 10]);

    $("#symbol").change(function (e) { update(); }).keypress(function (d) {
      if (event.which == 13) {
        event.preventDefault();
        update();
      }
    });
    $("#keyword").change(function (e) { update(); }).keypress(function (d) {
      if (event.which == 13) {
        event.preventDefault();
        update();
      }
    });
    $("#cleark").click(function () {
      $("#keyword").val("");
      update();
    });
    $("#clears").click(function () {
      $("#symbol").val("");
      update();
    });

    var sizeEl = $("#size");
    $("#sizeminus").click(function (d) {
      if (+sizeEl.val() > 1) sizeEl.val(+sizeEl.val() - 1);
      update();
    });
    $("#sizeplus").click(function (d) {
      sizeEl.val(+sizeEl.val() + 1);
      update();
    });
    sizeEl.change(function (e) { update(); }).keypress(function (d) {
      if (event.which == 13) {
        event.preventDefault();
        update();
      }
    });

    var nodeIndex = {};

    function keywords(count, symbol, keyword) {
      return $.ajax({
        url: "/1/query/symbol-keywords?count=" + count +
            (symbol != "" ? ("&symbol=$" + symbol) : "") +
            (keyword != "" ? ("&keyword=" + keyword) : ""),
        beforeSend: function (xhr) {
          xhr.setRequestHeader('Authorization', "APITOKEN 1c182c7f-40f0-45ca-8d55-7c5fad930173");
        }
      })
    }

    function symbolTrend(count) {
      $.ajax({
        url: "/1/query/symbol-trend?count=" + count,
        beforeSend: function (xhr) {
          xhr.setRequestHeader('Authorization', "APITOKEN 1c182c7f-40f0-45ca-8d55-7c5fad930173");
        }
      })
    }

    function reload() {
      var symbol = $("#symbol").val();
      var keyword = $("#keyword").val();
      var count = $("#size").val();
      keywords(count, symbol, keyword).done(function (data) {
        var _nodes = [];
        var _links = [];

        data.trend.forEach(function (d) {
          var source = nodeIndex[d.keys[0]] || (nodeIndex[d.keys[0]] = {name: d.keys[0]});
          var target = nodeIndex[d.keys[1]] || (nodeIndex[d.keys[1]] = {name: d.keys[1]});
          if (_nodes.indexOf(source) == -1) _nodes.push(source);
          if (_nodes.indexOf(target) == -1) _nodes.push(target);

          _links.push({source: source, target: target, score: d.score});
        });

        d3.select("#nodesize").text(d3.values(_nodes).length);
        var min = d3.min(_links, function (link) { return link.score; });
        var max = d3.max(_links, function (link) { return link.score; });
        lineWidth.domain([ min, max ]);
        colors.domain([min, max]);

        force.nodes(_nodes).links(_links);
        force.start();

        link = link.data(force.links(), function (d) { return d.source.name + ":" + d.target.name; });
        link.enter()
            .append("line")
            .attr("class", "link")
            .style("stroke-width", function (d) { return lineWidth(d.score); });
        link.exit().remove();

        node = node.data(force.nodes(), function(d) { return d.name; });

        var g = node.enter()
            .append("svg:g")
            .attr("class", "node");

        g.append("svg:circle")
            .attr("r", function (d) {
              if (_links[d.index]) return lineWidth(_links[d.index].score) * 2; else return 3;
            })
            .attr("stroke-width", function (d) { if (d.name[0] == "$") return 3; else return 1; })
            .attr("stroke", function (d) { if (d.name[0] == "$") return "#d36"; else return "#ddd"; })
            .style("fill", "#ddd")
            .on("click", function(d) {
              if(d.name[0] == "$") $("#symbol").val(d.name.substr(1));
              else $("#keyword").val(d.name);
              update();
            })
            .call(force.drag);

        g.append("svg:text")
            .attr("text-anchor", "start")
            .attr("font-family", "Arial, Helvetica, sans-serif")
            .style("font-weight", function (d) { if (d.name[0] == "$") return "bold"; else return "normal"; })
            .style("font-size", function (d) {
              if (_links[d.index]) return (lineWidth(_links[d.index].score) * 5) + "px"; else return "20px"
            })
            .attr("fill", function (d) {
              if (d.name[0] == "$") return "#d36"; else {
                if (_links[d.index])   return colors(_links[d.index].score); else return "#ddd"
              }
            })
            .attr("dx", "1em")
            .attr("dy", "0.35em")
            .text(function (d) { /*console.log("adding: "+ d.name);*/
              return d.name;
            });

        node.transition()
            .selectAll("text")
        node.exit().remove();

        d3.select("#vis").selectAll(".node").order();

        updating = undefined;
      });
    }

    reload();

  });
})(jQuery);
