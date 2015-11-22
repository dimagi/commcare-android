// This file expects a number of variables to be defined globally.
// Use only in conjunction with GraphView.getView
document.addEventListener("DOMContentLoaded", function(event) {
    // Match graph size to view size
    var titleHeight = (document.getElementById('chart-title') || { offsetHeight: 0 }).offsetHeight;
    config.size = {
        height: document.body.offsetHeight - titleHeight,
        width: document.body.offsetWidth,
    };

    // Turn off default hover/click behaviors
    config.interaction = { enabled: false };

    // Set point size for bubble charts, and turn points off altogether
    // for other charts (we'll be using custom point shapes)
    config.point = {
        r: function(d) {
            if (radii[d.id]) {
                // Arbitrary max size of 30
                return 30 * radii[d.id][d.index] / maxRadii[d.id];
            }
            return 0;
        },
    };

    // Add functions for custom tick labels
    if (config.axis.x.tick) {
        config.axis.x.tick.format = function(d) {
            var key = String(d);
            if (type === "time") {
                var time = key.match(/\d+:\d+:\d+/)[0];
                key = d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate() + " " + time;

            }
            return xLabels[key] || d;
        };
    }
    if (config.axis.y.tick) {
        config.axis.y.tick.format = function(d) {
            return yLabels[String(d)] || d;
        };
    }
    if (config.axis.y2.tick) {
        config.axis.y2.tick.format = function(d) {
            return y2Labels[String(d)] || d;
        };
    }
    if (type === "bar") {
        config.axis.x.tick.format = function(d) {
            return barLabels[d];
        };
    }

    // Hide any system-generated series from legend
    var systemSeries = ['boundsY', 'boundsY2'];
    for (var yID in config.data.xs) {
        if (!pointStyles[yID]) {
            systemSeries.push(yID);
        }
    }
    config.legend.hide = systemSeries;

    // Configure data labels, which we use only to display annotations
    config.data.labels = {
        format: function(value, id, index) {
            return annotations[id] || '';
        },
    };

    // Post-processing
    config.onrendered = function() {
        // For annotations series, nudge text so it appears on top of data point
        d3.selectAll("g.c3-texts text").attr("dy", 10);

        // Support point-style
        for (var yID in pointStyles) {
            applyPointStyle(yID, pointStyles[yID]);
        }
    };

    // Generate chart
    c3.generate(config);
});

/**
 * Replace C3's default circle points with user-requested symbols.
 * @param yID String ID of y-values to manipulate
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 */
function applyPointStyle(yID, symbol) {
    // Draw symbol for each point
    var circleSet = d3.selectAll(".c3-circles-" + yID);
    var circles = circleSet.selectAll("circle")[0];
    for (var j = 0; j < circles.length; j++) {
        circles[j].style.opacity = 0;    // hide default circle
        appendSymbol(
            circleSet,
            circles[j].cx.baseVal.value,
            circles[j].cy.baseVal.value,
            symbol,
            circles[j].style.fill
        );
    }

    // Make legend shape match symbol
    if (symbol !== "none") {
        var legendItem = d3.selectAll(".c3-legend-item-" + yID);
        var line = legendItem.selectAll("line")[0][0];    // there will only be one line
        line.style.opacity = 0;    // hide default square
        appendSymbol(
            legendItem,
            line.x1.baseVal.value + 5,
            line.y1.baseVal.value,
            symbol,
            line.style.stroke
        );
    }
}

/**
 * Add symbol to given element.
 * @param parent Element to attach symbol to
 * @param x x x-coordinate to draw at
 * @param y y-coordinate to draw at
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 * @param color Color to draw symbol
 */
function appendSymbol(parent, x, y, symbol, color) {
    if (symbol === 'none') {
        return;
    }

    parent.append("path")
        .attr("transform", function (d) {
        return "translate(" + x + ", " + y + ")";
    })
        .attr("class", "symbol")
        .attr("d", d3.svg.symbol()
              .type(symbol)
              .size(50))
        .style("fill", color)
        .style("stroke", color);
}