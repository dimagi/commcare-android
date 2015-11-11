document.addEventListener("DOMContentLoaded", function(event) {
    var width = document.body.offsetWidth;
    var height = document.body.offsetHeight;
    var x = d3.scale.linear().range([0, width]);
    var y = d3.scale.linear().range([0, height]);

    var chart = d3.select(".chart")
        .attr("width", width)
        .attr("height", height);

    var allPoints = _.flatten(_.pluck(graphData.series, 'points'))
    x.domain([getXMin(), getXMax()]);
    y.domain([getYMax(), getYMin()]);

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

function getBoundary(axis, direction, fallback, lambda) {
    var bound = graphData.configuration[axis + "-" + direction];
    if (!bound) {
        var points = _.flatten(_.pluck(graphData.series, 'points'));
        if (points.length) {
            bound = lambda.call(null, _.map(points, function(p) { return parseInt(p[axis]); }));
        }
    }
    if (bound) {
        return parseInt(bound);
    }
    return fallback;
}

function getXMax(axis) {
    return getBoundary("x", "max", 10, _.max);
}

function getXMin(axis) {
    return getBoundary("x", "min", 0, _.min)
}

function getYMax(axis) {
    return getBoundary("y", "max", 10, _.max);
}

function getYMin(axis) {
    return getBoundary("y", "min", 0, _.min)
}
