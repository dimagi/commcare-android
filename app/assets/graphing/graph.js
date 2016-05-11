// This file expects a number of variables to be defined globally.
// Use only in conjunction with GraphView.getView
var loadStart = (new Date()).getTime();
document.addEventListener("DOMContentLoaded", function(event) {
    document.body.innerHTML = "<div id=\"chart-title\">Time Graph</div><div id=\"error\"></div><div id=\"chart\" class=\"c3\" style=\"max-height: 254px; position: relative;\"><svg width=\"569\" height=\"254\" style=\"overflow: hidden;\"><defs><clipPath id=\"c3-1458850879939-clip\"><rect width=\"469\" height=\"210\"></rect></clipPath><clipPath id=\"c3-1458850879939-clip-xaxis\"><rect x=\"-61\" y=\"-20\" width=\"571\" height=\"60\"></rect></clipPath><clipPath id=\"c3-1458850879939-clip-yaxis\"><rect x=\"-59\" y=\"-4\" width=\"80\" height=\"234\"></rect></clipPath><clipPath id=\"c3-1458850879939-clip-grid\"><rect width=\"469\" height=\"210\"></rect></clipPath><clipPath id=\"c3-1458850879939-clip-subchart\"><rect width=\"469\"></rect></clipPath></defs><g transform=\"translate(60.5,4.5)\"><text class=\"c3-text c3-empty\" text-anchor=\"middle\" dominant-baseline=\"middle\" x=\"234.5\" y=\"105\"></text><rect class=\"c3-zoom-rect\" width=\"469\" height=\"210\" style=\"opacity: 0;\"></rect><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip)\" class=\"c3-regions\" style=\"visibility: visible;\"></g><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-grid)\" class=\"c3-grid\" style=\"visibility: visible;\"><g class=\"c3-xgrids\"><line class=\"c3-xgrid\" x1=\"0\" x2=\"0\" y1=\"0\" y2=\"210\" style=\"opacity: 0;\"></line><line class=\"c3-xgrid\" x1=\"59\" x2=\"59\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"118\" x2=\"118\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"176\" x2=\"176\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"235\" x2=\"235\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"294\" x2=\"294\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"352\" x2=\"352\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line><line class=\"c3-xgrid\" x1=\"411\" x2=\"411\" y1=\"0\" y2=\"210\" style=\"opacity: 1;\"></line></g><g class=\"c3-ygrids\"><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"210\" y2=\"210\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"190\" y2=\"190\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"169\" y2=\"169\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"148\" y2=\"148\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"127\" y2=\"127\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"106\" y2=\"106\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"85\" y2=\"85\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"64\" y2=\"64\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"43\" y2=\"43\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"22\" y2=\"22\"></line><line class=\"c3-ygrid\" x1=\"0\" x2=\"469\" y1=\"1\" y2=\"1\"></line></g><g class=\"c3-xgrid-focus\"><line class=\"c3-xgrid-focus\" x1=\"-10\" x2=\"-10\" y1=\"0\" y2=\"210\" style=\"visibility: hidden;\"></line></g></g><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip)\" class=\"c3-chart\"><g class=\"c3-event-rects c3-event-rects-multiple\" style=\"fill-opacity: 0;\"><rect x=\"0\" y=\"0\" width=\"469\" height=\"210\" class=\" c3-event-rect c3-event-rect\"></rect></g><g class=\"c3-chart-bars\"><g class=\"c3-chart-bar c3-target c3-target-y0\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-shapes c3-shapes-y0 c3-bars c3-bars-y0\" style=\"cursor: pointer;\"></g></g><g class=\"c3-chart-bar c3-target c3-target-boundsY\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-shapes c3-shapes-boundsY c3-bars c3-bars-boundsY\" style=\"cursor: pointer;\"></g></g></g><g class=\"c3-chart-lines\"><g class=\"c3-chart-line c3-target c3-target-y0\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-shapes c3-shapes-y0 c3-lines c3-lines-y0\"><path class=\" c3-shape c3-shape c3-line c3-line-y0\" d=\"M6973,168.2L7002,126.4L7031,84.6\" style=\"stroke: #000000; opacity: 1;\"></path></g><g class=\" c3-shapes c3-shapes-y0 c3-areas c3-areas-y0\"><path class=\" c3-shape c3-shape c3-area c3-area-y0\" d=\"M 6973 168.2\" style=\"fill: #000000; opacity: 0.20000000298023224;\"></path></g><g class=\" c3-selected-circles c3-selected-circles-y0\"></g><g class=\" c3-shapes c3-shapes-y0 c3-circles c3-circles-y0\" style=\"cursor: pointer;\"><circle class=\" c3-shape c3-shape-0 c3-circle c3-circle-0\" r=\"0\" cx=\"6973\" cy=\"168.2\" style=\"fill: #000000; opacity: 1;\"></circle><circle class=\" c3-shape c3-shape-1 c3-circle c3-circle-1\" r=\"0\" cx=\"7002\" cy=\"126.4\" style=\"fill: #000000; opacity: 1;\"></circle><circle class=\" c3-shape c3-shape-2 c3-circle c3-circle-2\" r=\"0\" cx=\"7031\" cy=\"84.6\" style=\"fill: #000000; opacity: 1;\"></circle></g></g><g class=\"c3-chart-line c3-target c3-target-boundsY\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-shapes c3-shapes-boundsY c3-lines c3-lines-boundsY\"><path class=\" c3-shape c3-shape c3-line c3-line-boundsY\" d=\"M0,210L469,1\" style=\"stroke: #1f77b4; opacity: 1;\"></path></g><g class=\" c3-shapes c3-shapes-boundsY c3-areas c3-areas-boundsY\"><path class=\" c3-shape c3-shape c3-area c3-area-boundsY\" d=\"M 0 210\" style=\"fill: #1f77b4; opacity: 0.20000000298023224;\"></path></g><g class=\" c3-selected-circles c3-selected-circles-boundsY\"></g><g class=\" c3-shapes c3-shapes-boundsY c3-circles c3-circles-boundsY\" style=\"cursor: pointer;\"><circle class=\" c3-shape c3-shape-0 c3-circle c3-circle-0\" r=\"0\" cx=\"0\" cy=\"210\" style=\"fill: #1f77b4; opacity: 1;\"></circle><circle class=\" c3-shape c3-shape-1 c3-circle c3-circle-1\" r=\"0\" cx=\"469\" cy=\"1\" style=\"fill: #1f77b4; opacity: 1;\"></circle></g></g></g><g class=\"c3-chart-arcs\" transform=\"translate(234.5,100)\"><text class=\"c3-chart-arcs-title\" style=\"text-anchor: middle; opacity: 0;\"></text></g><g class=\"c3-chart-texts\"><g class=\"c3-chart-text c3-target c3-target-y0\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-texts c3-texts-y0\"><text class=\" c3-text c3-text-0\" text-anchor=\"middle\" x=\"6973\" y=\"162.2\" style=\"stroke: none; fill: #000000; fill-opacity: 1;\"></text><text class=\" c3-text c3-text-1\" text-anchor=\"middle\" x=\"7002\" y=\"120.4\" style=\"stroke: none; fill: #000000; fill-opacity: 1;\"></text><text class=\" c3-text c3-text-2\" text-anchor=\"middle\" x=\"7031\" y=\"78.6\" style=\"stroke: none; fill: #000000; fill-opacity: 1;\"></text></g></g><g class=\"c3-chart-text c3-target c3-target-boundsY\" style=\"opacity: 0; pointer-events: none;\"><g class=\" c3-texts c3-texts-boundsY\"><text class=\" c3-text c3-text-0\" text-anchor=\"middle\" x=\"0\" y=\"204\" style=\"stroke: none; fill: #1f77b4; fill-opacity: 1;\"></text><text class=\" c3-text c3-text-1\" text-anchor=\"middle\" x=\"469\" y=\"-5\" style=\"stroke: none; fill: #1f77b4; fill-opacity: 1;\"></text></g></g></g></g><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-grid)\" class=\"c3-grid c3-grid-lines\"><g class=\"c3-xgrid-lines\"></g><g class=\"c3-ygrid-lines\"></g></g><g class=\"c3-axis c3-axis-x\" clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-xaxis)\" transform=\"translate(0,210)\" style=\"visibility: visible; opacity: 1;\"><text class=\"c3-axis-x-label\" transform=\"\" x=\"234.5\" dx=\"0\" dy=\"3em\" style=\"text-anchor: middle;\"></text><g class=\"tick\" transform=\"translate(118, 0)\" style=\"opacity: 1;\"><line y2=\"6\" x1=\"0\" x2=\"0\"></line><text y=\"9\" x=\"0\" transform=\"\" style=\"text-anchor: middle; display: block;\"><tspan x=\"0\" dy=\".71em\" dx=\"0\">2015-11-29</tspan></text></g><path class=\"domain\" d=\"M0,6V0H469V6\"></path></g><g class=\"c3-axis c3-axis-y\" clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-yaxis)\" transform=\"translate(0,0)\" style=\"visibility: visible; opacity: 1;\"><text class=\"c3-axis-y-label\" transform=\"rotate(-90)\" x=\"-105\" dx=\"0\" dy=\"-36.640625\" style=\"text-anchor: middle;\"></text><g class=\"tick\" transform=\"translate(0,210)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">0</tspan></text></g><g class=\"tick\" transform=\"translate(0,190)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">0.5</tspan></text></g><g class=\"tick\" transform=\"translate(0,169)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">1</tspan></text></g><g class=\"tick\" transform=\"translate(0,148)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">1.5</tspan></text></g><g class=\"tick\" transform=\"translate(0,127)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">2</tspan></text></g><g class=\"tick\" transform=\"translate(0,106)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">2.5</tspan></text></g><g class=\"tick\" transform=\"translate(0,85)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">3</tspan></text></g><g class=\"tick\" transform=\"translate(0,64)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">3.5</tspan></text></g><g class=\"tick\" transform=\"translate(0,43)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">4</tspan></text></g><g class=\"tick\" transform=\"translate(0,22)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">4.5</tspan></text></g><g class=\"tick\" transform=\"translate(0,1)\" style=\"opacity: 1;\"><line x2=\"-6\"></line><text x=\"-9\" y=\"0\" style=\"text-anchor: end;\"><tspan x=\"-9\" dy=\"3\">5</tspan></text></g><path class=\"domain\" d=\"M-6,1H0V210H-6\"></path></g><g class=\"c3-axis c3-axis-y2\" transform=\"translate(469,0)\" style=\"visibility: visible; opacity: 1;\"><text class=\"c3-axis-y2-label\" transform=\"rotate(-90)\" x=\"-105\" dx=\"0\" dy=\"30\" style=\"text-anchor: middle;\"></text><path class=\"domain\" d=\"M6,1H0V210H6\"></path></g></g><g transform=\"translate(40.5,254.5)\" style=\"visibility: hidden;\"><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-subchart)\" class=\"c3-chart\"><g class=\"c3-chart-bars\"></g><g class=\"c3-chart-lines\"></g></g><g clip-path=\"url(file:///android_asset/#c3-1458850879939-clip)\" class=\"c3-brush\" style=\"pointer-events: all; -webkit-tap-highlight-color: rgba(0, 0, 0, 0);\"><rect class=\"background\" x=\"0\" width=\"489\" style=\"visibility: hidden; cursor: crosshair;\"></rect><rect class=\"extent\" x=\"0\" width=\"0\" style=\"cursor: move;\"></rect><g class=\"resize e\" transform=\"translate(0,0)\" style=\"cursor: ew-resize; display: none;\"><rect x=\"-3\" width=\"6\" height=\"6\" style=\"visibility: hidden;\"></rect></g><g class=\"resize w\" transform=\"translate(0,0)\" style=\"cursor: ew-resize; display: none;\"><rect x=\"-3\" width=\"6\" height=\"6\" style=\"visibility: hidden;\"></rect></g></g><g class=\"c3-axis-x\" transform=\"translate(0,0)\" clip-path=\"url(file:///android_asset/#c3-1458850879939-clip-xaxis)\" style=\"visibility: hidden; opacity: 1;\"><g class=\"tick\" transform=\"translate(118, 0)\" style=\"opacity: 1;\"><line y2=\"6\" x1=\"0\" x2=\"0\"></line><text y=\"9\" x=\"0\" transform=\"\" style=\"text-anchor: middle; display: block;\"><tspan x=\"0\" dy=\".71em\" dx=\"0\">2015-11-29</tspan></text></g><path class=\"domain\" d=\"M0,6V0H469V6\"></path></g></g><g transform=\"translate(0,254)\" style=\"visibility: hidden;\"></g><text class=\"c3-title\" x=\"284.5\" y=\"0\"></text></svg><div class=\"c3-tooltip-container\" style=\"position: absolute; pointer-events: none; display: none;\"></div></div><h1 style=\"position:fixed;top:0;left:0;\">readyDuration=3644</h1>";
    /*var retryFrequency = 10,
        delay = 0;
    var readyStart = (new Date()).getTime();
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

            config.transition = { duration: 0 };

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
                    var label = axis.xLabels[key] || d;
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
            console.log("rendering");
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

                var end = (new Date()).getTime();
                document.body.innerHTML += "<h1 style='position:fixed;top:0;left:0;'>readyDuration=" + (end - readyStart) + "</h1>";
            };

            // Generate chart
            console.log("generating");
            var renderStart = (new Date()).getTime();
            c3.generate(config);
            console.log("generateTime=" + ((new Date()).getTime() - renderStart));
        } catch(e) {
            displayError(e);
        }
    }, retryFrequency);*/
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
