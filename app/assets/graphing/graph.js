document.addEventListener("DOMContentLoaded", function(event) {
    config.size = {
        height: document.body.offsetHeight,
        width: document.body.offsetWidth,
    };
    var chart = c3.generate(config);
});
