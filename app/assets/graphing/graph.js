// This file expects a number of variables to be defined globally.
// Use only in conjunction with GraphView.getView
document.addEventListener("DOMContentLoaded", function(event) {
    // Match graph size to view size
    config.size = {
        height: document.body.offsetHeight,
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
    if (type === "bar") {
        config.axis.x.tick.format = function(d) {
            return barLabels[d];
        };
    }

    // Hide any system-generated series


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

        // TODO: support point-style: s.getConfiguration("point-style", "circle").toLowerCase()
    };

    // Generate chart
    c3.generate(config);
});
