/*
 * Comment block to make line numbers match console's error output
 *
 */
// TODO: can't size based on pixels
var width = height = 200;
var x = d3.scale.linear().range([0, width]);
var y = d3.scale.linear().range([0, height]);

var chart = d3.select(".chart")
    .attr("width", width)
	.attr("height", height);

var data = [{
    x: 1,
    y: 4
}, {
    x: 2,
    y: 3
}, {
    x: 3,
    y: 3
}];

// TODO: d3.min and d3.max, across all series
x.domain([0, 10]);
y.domain([10, 0]);

for (var s = 0; s < graphData.series.length; s++) {
    var lineFunction = d3.svg.line()
 	    		    		.x(function(d) { return x(+d.x); })
 		    				.y(function(d) { return y(+d.y); })
			    			.interpolate("linear");

    chart.append("path")
	    	.attr("d", lineFunction(graphData.series[s]));
}