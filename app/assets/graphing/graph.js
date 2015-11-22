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
    config.interaction = { enabled: true };
    config.tooltip = {
        show: true,
        grouped: type === "bar",
        contents: function(data, defaultTitleFormat, defaultValueFormat, color) {
            var html = "";
            for (var i = 0; i < data.length; i++) {
                if (isData[data[i].id]) {
                    var yName = config.data.names[data[i].id];
            	    html += "<tr><td>" + yName + "</td><td>" + data[i].value + "</td></tr>";
            	}
            }
            if (!html) {
                return "";
            }
            if (type === "bar") {
                html = "<tr><td colspan='2'>" + barLabels[data[0].x] + "</td></tr>" + html;
            } else {
                html = "<tr><td>" + xNames[data[0].id] + "</td><td>" + data[0].x + "</td></tr>" + html;  // TODO: test with time charts
            }
        	if (type === "bubble") {
        	    html += "<tr><td>Radius</td><td>" + radii[d.id][d.index] + "</td></tr>";
        	}
        	html = "<table>" + html + "</table>";
        	html = "<div id='tooltip'>" + html + "</div>";
        	return html;
    	},
    };

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
            return xLabels[String(d)] || d;
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

    // Hide any system-generated series from legend
    var hideSeries = [];
    for (var yID in config.data.xs) {
        if (!isData[yID]) {
            hideSeries.push(yID);
        }
    }
    config.legend.hide = hideSeries;

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
            var symbol = pointStyles[yID];
            if (type === "bar") {
                symbol = "none";
            } else if (type=== "bubble") {
                symbol = "circle";
            } else {
                applyPointShape(yID, symbol);
            }
            applyLegendShape(yID, symbol);
        }

        // Configure colors more specifically than C3 allows
        for (var yID in config.data.colors) {
            // Data itself
            if (type === "bar") {
                var bars = d3.selectAll(".c3-bars-" + yID + " path")[0];
                for (var i = 0; i < bars.length; i++) {
                    bars[i].style.opacity = lineOpacities[yID];
                }
            } else {
                var line = d3.selectAll(".c3-lines-" + yID + " path")[0][0];
                if (line) {
                    line.style.opacity = lineOpacities[yID];
                }
            }

            // Legend
            var legend = d3.selectAll(".c3-legend-item-" + yID + " path")[0];
            if (!legend.length) {
                legend = d3.selectAll(".c3-legend-item-" + yID + " line")[0];
            }
            if (legend.length) {
                legend = legend[0];
                legend.style.opacity = lineOpacities[yID];
            }

            // Point shapes
            var points = d3.selectAll(".c3-circles-" + yID + " path")[0];
            if (!points.length) {
                points = d3.selectAll(".c3-circles-" + yID + " circle")[0];
            }
            for (var i = 0; i < points.length; i++) {
                points[i].style.opacity = lineOpacities[yID];
            }
        }
        for (var yID in areaColors) {
            var area = d3.selectAll(".c3-areas-" + yID + " path")[0][0];
            area.style.fill = areaColors[yID];
            area.style.opacity = areaOpacities[yID];
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
function applyPointShape(yID, symbol) {
    if (type === 'bar' || type === 'bubble') {
        return;
    }

    var circleSet = d3.selectAll(".c3-circles-" + yID);
    var circles = circleSet.selectAll("circle")[0];
    if (!circles) {
        return;
    }

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
}

/**
 * Make shape displayed in legend match shape used on line.
 * @param yID String ID of y-values to manipulate
 * @param symbol string representing symbol: "none", "circle", "cross", etc.
 *  Unknown symbols will be drawn as circles.
 */
function applyLegendShape(yID, symbol) {
    if (symbol !== "none") {
        var legendItem = d3.selectAll(".c3-legend-item-" + yID);
        var line = legendItem.selectAll("line");    // there will only be one line
        if (!line || !line.length) {
            return;
        }
        line = line[0][0]
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