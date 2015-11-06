document.addEventListener("DOMContentLoaded", function(event) {
    //graphData = {"type":"xy","series":[{"points":[{"y":"2","x":"2"},{"y":"3","x":"3"},{"y":"1","x":"1"}]}]}
    var width = document.body.offsetWidth;
    var height = document.body.offsetHeight;
    var x = d3.scale.linear().range([0, width]);
    var y = d3.scale.linear().range([0, height]);

    var chart = d3.select(".chart")
        .attr("width", width)
        .attr("height", height);

    // TODO: d3.min and d3.max, across all series
    x.domain([0, 10]);
    y.domain([10, 0]);

    for (var s = 0; s < graphData.series.length; s++) {
        if (graphData.type === "xy") {
            var lineFunction = d3.svg.line()
                                    .x(function(d) { return x(+d.x); })
                                    .y(function(d) { return y(+d.y); })
                                    .interpolate("linear");

            chart.append("path")
                    .attr("d", lineFunction(graphData.series[s].points));
        }
    }
});