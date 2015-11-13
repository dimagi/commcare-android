// This file expects the config variable to be defined globally.
document.addEventListener("DOMContentLoaded", function(event) {
    // Match graph size to view size
    config.size = {
        height: document.body.offsetHeight,
        width: document.body.offsetWidth,
    };

    // Turn off various default hover/click behaviors
    config.interaction = { enabled: false };

    var chart = c3.generate(config);
});
