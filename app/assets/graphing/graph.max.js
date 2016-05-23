function displayError(message) {
    console.log(message);
    var error = document.getElementById('error');
    error.innerHTML = message;
    error.style.display = 'block';
}

// This file expects a number of variables to be defined globally.
// Use only in conjunction with GraphView.getView
document.addEventListener("DOMContentLoaded", function(event) {
    var retryFrequency = 10,
        delay = 0;
    var intervalID = setInterval(function() {
        delay += retryFrequency;

        // Ghastly hack: Occasionally, when the graphing javascript runs, document.body has no
        // dimensions, which causes the graph to not actually display full screen, and it also
        // causes extra points to appear on the y axis. If we wait, dimensions will sometimes get
        // populated. If they never do, give up, and user will see a blank space.
        if (!document.body.offsetWidth || !document.body.offsetHeight) {
            if (delay > 10000) {
                clearInterval(intervalID);
            }
            return;
        }
        clearInterval(intervalID);

        try {
            // Match graph size to view size
            var titleHeight = (document.getElementById('chart-title') || { offsetHeight: 0 }).offsetHeight;
            config.size = {
                height: document.body.offsetHeight - titleHeight,
                width: document.body.offsetWidth,
            };

            // Turn off default hover/click behaviors
            config.interaction = { enabled: true };
            var formatXTooltip = function(x) {
                return x;
            }
            if (type === "time") {
                formatXTooltip = d3.time.format(config.axis.x.tick.format);
            }
            config.tooltip = {
                show: true,
                grouped: type === "bar",
                contents: function(yData, defaultTitleFormat, defaultValueFormat, color) {
                    var html = "";

                    // Add rows for y values
                    for (var i = 0; i < yData.length; i++) {
                        if (data.isData[yData[i].id]) {
                            var yName = config.data.names[yData[i].id];
                            html += "<tr><td>" + yName + "</td><td>" + yData[i].value + "</td></tr>";
                        }
                    }
                    if (!html) {
                        return "";
                    }

                    // Add a top row for x value
                    if (type === "bar") {
                        html = "<tr><td colspan='2'>" + data.barLabels[yData[0].x] + "</td></tr>" + html;
                    } else {
                        html = "<tr><td>" + data.xNames[yData[0].id] + "</td><td>"
                                + formatXTooltip(yData[0].x) + "</td></tr>"
                                + html;
                    }

                    if (type === "bubble") {
                        html += "<tr><td>Radius</td><td>" + data.radii[d.id][d.index] + "</td></tr>";
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
                    if (data.radii[d.id]) {
                        // Arbitrary max size of 30
                        return 30 * data.radii[d.id][d.index] / data.maxRadii[d.id];
                    }
                    return 0;
                },
            };

            // Add functions for custom tick label text (where foo-labels was an object).
            // Don't do this if the tick format was already set by Java (which will
            // only happen for time-based graphs that are NOT using custom tick text).
            if (config.axis.x.tick && !config.axis.x.tick.format) {
                config.axis.x.tick.format = function(d) {
                    var key = String(d);
                    if (type === "time") {
                        var time = key.match(/\d+:\d+:\d+/)[0];
                        key = d.getFullYear() + "-" + (d.getMonth() + 1) + "-" + d.getDate() + " " + time;

                    }
                    var label = axis.xLabels[key] === undefined ? d : axis.xLabels[key];
                    return Math.round(label) || label;
                };
            }
            if (config.axis.y.tick) {
                config.axis.y.tick.format = function(d) {
                    return axis.yLabels[String(d)] || Math.round(d);
                };
            }
            if (config.axis.y2.tick) {
                config.axis.y2.tick.format = function(d) {
                    return axis.y2Labels[String(d)] || Math.round(d);
                };
            }

            // Hide any system-generated series from legend
            var hideSeries = [];
            for (var yID in config.data.xs) {
                if (!data.isData[yID]) {
                    hideSeries.push(yID);
                }
            }
            config.legend.hide = hideSeries;

            // Configure data labels, which we use only to display annotations
            config.data.labels = {
                format: function(value, id, index) {
                    return data.annotations[id] || '';
                },
            };

            // Don't use C3's default ordering for stacked series, use the order series are defined
            config.data.order = false;

            // Post-processing
            config.onrendered = function() {
                // For annotations series, nudge text so it appears on top of data point
                d3.selectAll("g.c3-texts text").attr("dy", 10);

                // Support point-style
                for (var yID in data.pointStyles) {
                    var symbol = data.pointStyles[yID];
                    applyPointShape(yID, symbol);
                    applyLegendShape(yID, symbol);
                }

                // Configure colors more specifically than C3 allows
                for (var yID in config.data.colors) {
                    // Data itself
                    if (type === "bar") {
                        var bars = d3.selectAll(".c3-bars-" + yID + " path")[0];
                        for (var i = 0; i < bars.length; i++) {
                            // If there's a bar-specific color, set it
                            if (data.barColors[yID] && data.barColors[yID][i]) {
                                bars[i].style.fill = data.barColors[yID][i];
                            }
                            // Get opacity: bar-specific if it's there, otherwise series-specific
                            var opacity;
                            if (data.barOpacities[yID]) {
                                opacity = data.barOpacities[yID][i];
                            }
                            opacity = opacity || data.lineOpacities[yID];
                            bars[i].style.opacity = opacity;
                        }
                    } else {
                        var line = d3.selectAll(".c3-lines-" + yID + " path")[0][0];
                        if (line) {
                            line.style.opacity = data.lineOpacities[yID];
                        }
                    }

                    // Legend
                    var legend = d3.selectAll(".c3-legend-item-" + yID + " path")[0];
                    if (!legend.length) {
                        legend = d3.selectAll(".c3-legend-item-" + yID + " line")[0];
                    }
                    if (legend.length) {
                        legend = legend[0];
                        legend.style.opacity = data.lineOpacities[yID];
                    }

                    // Point shapes
                    var points = d3.selectAll(".c3-circles-" + yID + " path")[0];
                    if (!points.length) {
                        points = d3.selectAll(".c3-circles-" + yID + " circle")[0];
                    }
                    for (var i = 0; i < points.length; i++) {
                        points[i].style.opacity = data.lineOpacities[yID];
                    }
                }
                for (var yID in data.areaColors) {
                    var area = d3.selectAll(".c3-areas-" + yID + " path")[0][0];
                    if (area) {
                        area.style.fill = data.areaColors[yID];
                        area.style.opacity = data.areaOpacities[yID];
                    }
                }

                Android.showGraph();
            };

            // Generate chart
            c3.generate(config);
        } catch(e) {
            displayError(e);
        }
    }, retryFrequency);
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
 * @param x x-coordinate to draw at
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
