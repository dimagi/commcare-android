// This file expects the config variable to be defined globally.
document.addEventListener("DOMContentLoaded", function(event) {
    // Match graph size to view size
    config.size = {
        height: document.body.offsetHeight,
        width: document.body.offsetWidth,
    };

    // Turn off various default hover/click behaviors
    config.interaction = { enabled: false };

    // Add functions for custom tick labels
    var axisLabels = {
        x: {},
        y: {},
        y2: {},
    };
    for (var axis in axisLabels) {
            for (var key in config.axis[axis].tick.format) {
                axisLabels[axis][String(key)] = config.axis[axis].tick.format[String(key)];
            }
    }
    config.axis.x.tick.format = function(d) {
        return axisLabels.x[String(d)] || d;
    };
    config.axis.y.tick.format = function(d) {
        return axisLabels.y[String(d)] || d;
    };
    config.axis.y2.tick.format = function(d) {
        return axisLabels.y2[String(d)] || d;
    };

    var chart = c3.generate(config);
});
