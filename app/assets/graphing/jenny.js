/*
 * Comment block to make line numbers match console
 *
 */
    var data = new google.visualization.DataTable();
    data.addColumn('number', 'X');
    //var graphData = {"series":[[{"x":"1","y":"1"},{"x":"2","y":"2"},{"x":"3","y":"3"}]]};
    for (var s = 0; s < graphData.series.length; s++) {
        console.log("s=" + s);
        console.log("series[s]=" + graphData.series[s]);
        data.addColumn('number', 'series ' + s);
        var rows = [];
        for (var p = 0; p < graphData.series[s].length; p++) {
            var point = graphData.series[s][p];
            var row = [parseInt(point.x)];
            row[s + 1] = parseInt(point.y);
            rows.push(row);
            console.log("row=" + row);
        }
        console.log("rows.length=" + rows.length);
        console.log("rows=" + rows);
        data.addRows(rows);
    }

    var options = {
        hAxis: {
            title: 'Time'
        },
        vAxis: {
            title: 'Popularity'
        },
        colors: ['#a52714'],// '#097138'],
        interpolateNulls: true,
    };

    var chart = new google.visualization.LineChart(document.getElementById('chart_div'));
    chart.draw(data, options);
