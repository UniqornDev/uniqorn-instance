import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml, config } from 'core';
css('metrics');

class MetricsPage extends Page
{
	async show()
	{
		this.dom.classList.add('metrics');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'metrics'));

		this.init();
	}

	async hide()
	{
		while(this.dom.firstChild) this.dom.firstChild.remove();
		this.data = null;
		if( this.graph1 ) { this.graph1.destroy(); this.graph1 = null; }
		if( this.graph2 ) { this.graph2.destroy(); this.graph2 = null; }
		if( this.graph3 ) { this.graph3.destroy(); this.graph3 = null; }
		if( this.graph4 ) { this.graph4.destroy(); this.graph4 = null; }
	}

	init()
	{
		var self = this;
		this.dom.classList.add('wait');
		while(this.dom.firstChild) this.dom.firstChild.remove();

		var now = new Date();
		now.setHours(now.getHours() - 2, now.getMinutes() - now.getTimezoneOffset());
		now = now.toISOString().slice(0, 16);

		this.dom.append(
			Node.div({className: 'action'},
			[
				config.user.level === 'manager' ? Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.downloadMetrics(); }}, [
					Node.span({className: 'icon'}, 'download'),
					Node.span(Translator.get('metrics.download'))]) : null,
				config.user.level === 'manager' ? Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.clearMetrics(); }}, [
					Node.span({className: 'icon'}, 'delete_forever'),
					Node.span(Translator.get('metrics.clear'))]) : null
			]),
			Node.section(
			[
				Node.h2([
					Translator.get('metrics.title'),
					Node.div({className: 'small_action'},
					[
						Node.span({className: 'icon', click: () => { self.codeMetrics(); }, dataset: {tooltip: Translator.get('code')}}, 'code')
					])
				]),
				Node.p(Translator.get('metrics.explain')),
				Node.div({className: 'timerange'},
				[
					Node.span(Translator.get('metrics.range.from')),
					Node.input({type: 'datetime-local', value: now}),
					Node.span(Translator.get('metrics.range.length')),
					Node.select({value: 2*3600000},
					[
						Node.option({value: 1*3600000}, Translator.get('metrics.range.length.1')),
						Node.option({value: 2*3600000, selected: true}, Translator.get('metrics.range.length.2')),
						Node.option({value: 4*3600000}, Translator.get('metrics.range.length.4')),
						Node.option({value: 8*3600000}, Translator.get('metrics.range.length.8')),
						Node.option({value: 24*3600000}, Translator.get('metrics.range.length.24')),
					]),
					Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.refresh(); }}, Translator.get('metrics.range.refresh'))
				]),
				Node.div({className: 'tab', dataset: {tab: 1}},
				[
					Node.div({click: function(e) { self.switchTab(e.target); }}, [
						Node.span(Translator.get('metrics.tab.api')),
						Node.span(Translator.get('metrics.tab.user')),
						Node.span(Translator.get('metrics.tab.custom'))
					]),
					Node.div([
						Node.div({className: 'tabcontent', id: 'metrics_api'},
						[
							Node.div(Node.select({className: 'drilldown', change: function() { self._drawGraph(); }}, [])),
							Node.h3(Translator.get('metrics.graph.title.hits')),
							Node.canvas({className: 'graph1'}),
							Node.h3(Translator.get('metrics.graph.title.ratio')),
							Node.canvas({className: 'graph2'}),
							Node.h3(Translator.get('metrics.graph.title.time')),
							Node.canvas({className: 'graph3'}),
							Node.h3(Translator.get('metrics.graph.title.table')),
							Node.div({className: 'table'}, Node.table())
						]),
						Node.div({className: 'tabcontent', id: 'metrics_user'},
						[
							Node.div(Node.select({className: 'drilldown', change: function() { self._drawGraph(); }}, [])),
							Node.h3(Translator.get('metrics.graph.title.hits')),
							Node.canvas({className: 'graph1'}),
							Node.h3(Translator.get('metrics.graph.title.ratio')),
							Node.canvas({className: 'graph2'}),
							Node.h3(Translator.get('metrics.graph.title.time')),
							Node.canvas({className: 'graph3'}),
							Node.h3(Translator.get('metrics.graph.title.table')),
							Node.div({className: 'table'}, Node.table())
						]),
						Node.div({className: 'tabcontent', id: 'metrics_custom'},
						[
							Node.div(Node.select({className: 'drilldown', change: function() { self._drawGraph(); }}, [])),
							Node.h3(Translator.get('metrics.graph.title.count')),
							Node.canvas({className: 'graph1'}),
							Node.h3(Translator.get('metrics.graph.title.sum')),
							Node.canvas({className: 'graph4'}),
							Node.h3(Translator.get('metrics.graph.title.table')),
							Node.div({className: 'table'}, Node.table())
						])
					])
				])
			])
		);

		this.refresh();
	}

	refresh()
	{
		var self = this;

		var from = new Date(this.dom.querySelector('.timerange input').value).getTime();
		var to = from + parseInt(this.dom.querySelector('.timerange select').value);

		Ajax.get('/api/manager/metrics', {data: {from: from, to: to}}).then(result =>
		{
			self.data = result.response;
			self._from = from;
			self._to = to;

			// populate select box
			var set1 = new Set();
			var set2 = new Set();
			var set3 = new Set();
			self.data.forEach(slot =>
			{
				if( slot.endpoint )
					Object.keys(slot.endpoint).forEach(v => set1.add(v));
				if( slot.user )
					Object.keys(slot.user).forEach(v => set2.add(v));
				if( slot.custom )
					Object.values(slot.custom).forEach(v => Object.keys(v).forEach(w => set3.add(w)));
			});
			var s1 = self.dom.querySelector('#metrics_api select.drilldown');
			while( s1.firstChild ) s1.firstChild.remove();
			s1.append(Node.option({value: ''}, Translator.get('metrics.graph.filter.all')));
			[...set1].sort((a,b) => { return a > b ? 1 : -1; }).forEach(v => s1.append(Node.option({value: v}, safeHtml(v))));
			var s2 = self.dom.querySelector('#metrics_user select.drilldown');
			while( s2.firstChild ) s2.firstChild.remove();
			s2.append(Node.option({value: ''}, Translator.get('metrics.graph.filter.all')));
			[...set2].sort((a,b) => { return a > b ? 1 : -1; }).forEach(v => s2.append(Node.option({value: v}, safeHtml(v))));
			var s3 = self.dom.querySelector('#metrics_custom select.drilldown');
			while( s3.firstChild ) s3.firstChild.remove();
			s3.append(Node.option({value: ''}, Translator.get('metrics.graph.filter.all')));
			[...set3].sort((a,b) => { return a > b ? 1 : -1; }).forEach(v => s3.append(Node.option({value: v}, safeHtml(v))));

			var index = parseInt(self.dom.querySelector('.tab').dataset.tab);
			if( index == 1 )
				self._drawGraph('api');
			else if( index == 2 )
				self._drawGraph('user');
			else if( index == 3 )
				self._drawGraph('custom');

			self.dom.classList.remove('wait');
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
			self.dom.classList.remove('wait');
		});
	}

	_drawGraph(tab)
	{
		var self = this;
		if( !self.data ) return;
		if( !tab )
		{
			var index = parseInt(this.dom.querySelector('.tab').dataset.tab);
			if( index == 1 ) tab = 'api';
			else if( index == 2 ) tab = 'user';
			else if( index == 3 ) tab = 'custom';
		}

		if( this.graph1 ) { this.graph1.destroy(); this.graph1 = null; }
		if( this.graph2 ) { this.graph2.destroy(); this.graph2 = null; }
		if( this.graph3 ) { this.graph3.destroy(); this.graph3 = null; }
		if( this.graph4 ) { this.graph4.destroy(); this.graph4 = null; }

		var g1 = null, g2 = null, g3 = null, g4 = null, category = null, filter1 = null, filter2 = null;
		if( tab == 'api' )
		{
			g1 = this.dom.querySelector('#metrics_api canvas.graph1');
			g2 = this.dom.querySelector('#metrics_api canvas.graph2');
			g3 = this.dom.querySelector('#metrics_api canvas.graph3');
			category = "endpoint";
			filter1 = this.dom.querySelector('#metrics_api select.drilldown').value;
		}
		else if( tab == 'user' )
		{
			g1 = this.dom.querySelector('#metrics_user canvas.graph1');
			g2 = this.dom.querySelector('#metrics_user canvas.graph2');
			g3 = this.dom.querySelector('#metrics_user canvas.graph3');
			category = "user";
			filter1 = this.dom.querySelector('#metrics_user select.drilldown').value;
		}
		else if( tab == 'custom' )
		{
			g1 = this.dom.querySelector('#metrics_custom canvas.graph1');
			g4 = this.dom.querySelector('#metrics_custom canvas.graph4');
			category = "custom";
			filter2 = this.dom.querySelector('#metrics_custom select.drilldown').value;
		}

		var labels = self._getLabels(self._from, self._to);
		var series = self._getData(self.data, category, labels, filter1, filter2);

		if( g1 )
		{
			// graph 1: total call count
			self.graph1 = new Chart(g1.getContext('2d'),
			{
				type: 'line',
				data: {
					labels: labels,
					datasets: [{
						label: Translator.get('metrics.graph.serie.api'),
						data: series.count,
						borderColor: "#36a2eb",
						backgroundColor: "#36a2eb50",
						yAxisID: 'y',
						fill: 'origin'
					}]
				},
				options:
				{
					datasets: {
						line: {
							borderColor: '#36a2eb', pointRadius: 0,
							borderWidth: 1
						}},
					plugins: {
						title: { display: false },
						legend: { display: false },
						tooltip: { enabled: true, callbacks: { title: function(context) { return new Date(parseInt(context[0].label)).toLocaleString([], {dateStyle: 'short', timeStyle: 'short'}); } } }
					},
					layout: { padding: 5 },
					responsive: true,
					interaction: { mode: 'index', intersect: false },
					maintainAspectRatio: false,
					resizeDelay: 250,
					scales: {
						x: {
							title: { display: false },
							ticks: { display: false },
							grid: { display: false }
						},
						y: {
							title: { text: Translator.get('metrics.graph.hits'), display: true },
							type: 'linear',
							grid: { color: '#3333' },
							min: 0
						}
					}
				}
			});
		}

		if( g2 )
		{
			// graph 2: success/error as %
			self.graph2 = new Chart(g2.getContext('2d'),
			{
				type: 'bar',
				data: {
					labels: labels,
					datasets: [
						{ label: Translator.get('metrics.graph.serie.success'), data: series.success, borderColor: "#4d0", backgroundColor: "#4d0", yAxisID: 'y'},
						{ label: Translator.get('metrics.graph.serie.error'), data: series.errors, borderColor: "#d00", backgroundColor: "#d00", yAxisID: 'y'}
					]
				},
				options:
				{
					datasets: { bar: { pointRadius: 0, borderWidth: 1 }},
					plugins: {
						title: { display: false },
						legend: { display: false },
						tooltip: { enabled: true, callbacks: { title: function(context) { return new Date(parseInt(context[0].label)).toLocaleString([], {dateStyle: 'short', timeStyle: 'short'}); } } }
					},
					layout: { padding: 5 },
					responsive: true,
					interaction: { mode: 'index', intersect: false },
					maintainAspectRatio: false,
					resizeDelay: 250,
					scales: {
						x: {
							title: { display: false },
							ticks: { display: false },
							grid: { display: false },
							stacked: true
						},
						y: {
							title: { text: Translator.get('metrics.graph.ratio'), display: true },
							type: 'linear',
							grid: { color: '#3333' },
							min: 0, max: 100, stacked: true
						}
					}
				}
			});
		}

		if( g3 )
		{
			// graph 3: avg time with std (or min/max)
			self.graph3 = new Chart(g3.getContext('2d'),
			{
				type: 'line',
				data: {
					labels: labels,
					datasets: [{
						label: Translator.get('metrics.graph.serie.time'),
						data: series.time,
						borderColor: "#36a2eb",
						backgroundColor: "#36a2eb50",
						yAxisID: 'y',
						fill: 'origin'
					},
					{
						label: Translator.get('metrics.graph.serie.slow'),
						data: series.max,
						borderColor: "#d00",
						yAxisID: 'y',
					}]
				},
				options:
				{
					datasets: { line: { pointRadius: 0, borderWidth: 1 }},
					plugins: {
						title: { display: false },
						legend: { display: false },
						tooltip: { enabled: true, callbacks: { title: function(context) { return new Date(parseInt(context[0].label)).toLocaleString([], {dateStyle: 'short', timeStyle: 'short'}); } } }
					},
					layout: { padding: 5 },
					responsive: true,
					interaction: { mode: 'index', intersect: false },
					maintainAspectRatio: false,
					resizeDelay: 250,
					scales: {
						x: {
							title: { display: false },
							ticks: { display: false },
							grid: { display: false }
						},
						y: {
							title: { text: Translator.get('metrics.graph.time'), display: true },
							type: 'linear',
							grid: { color: '#3333' },
							min: 0
						}
					}
				}
			});
		}

		if( g4 )
		{
			// graph 4: cumulated sum
			self.graph4 = new Chart(g4.getContext('2d'),
			{
				type: 'line',
				data: {
					labels: labels,
					datasets: [{
						label: Translator.get('metrics.graph.serie.time'),
						data: series.sum,
						borderColor: "#d00",
						backgroundColor: "#d005",
						yAxisID: 'y',
						fill: 'origin'
					}]
				},
				options:
				{
					datasets: { line: { pointRadius: 0, borderWidth: 1 }},
					plugins: {
						title: { display: false },
						legend: { display: false },
						tooltip: { enabled: true, callbacks: { title: function(context) { return new Date(parseInt(context[0].label)).toLocaleString([], {dateStyle: 'short', timeStyle: 'short'}); } } }
					},
					layout: { padding: 5 },
					responsive: true,
					interaction: { mode: 'index', intersect: false },
					maintainAspectRatio: false,
					resizeDelay: 250,
					scales: {
						x: {
							title: { display: false },
							ticks: { display: false },
							grid: { display: false }
						},
						y: {
							title: { text: Translator.get('metrics.graph.sum'), display: true },
							type: 'linear',
							grid: { color: '#3333' },
							min: 0
						}
					}
				}
			});
		}

		this._drawTable(self.data, category, filter1, filter2);
	}

	_getLabels(from, to)
	{
		var times = [];
		for( var i = new Date(from).setSeconds(0, 0); i < to; i += 60000 )
			times.push(i);
		return times;
	}

	_getData(data, name, times, filter1, filter2)
	{
		var errors = [];
		var success = [];
		var time = [];
		var max = [];
		var count = [];
		var sum = [];

		var j = 0;
		for( var i = 0; i < times.length; i++ )
		{
			while( j < data.length && data[j]._from < times[i] ) j++;
			if( j >= data.length || data[j]._from > (i < times.length ? times[i+1] : times[i]+60000) || !data[j][name] )
			{
				// missing data
				errors.push(0);
				success.push(0);
				time.push(0);
				max.push(0);
				count.push(0);
				sum.push(0);
			}
			else
			{
				var e = 0, s = 0, t = 0, c = 0, m = 0, x = 0;
				Object.entries(data[j][name]).forEach(([type, d]) =>
				{
					if( filter1 && type != filter1 ) return;

					Object.entries(d).forEach(([code, v]) =>
					{
						if( filter2 && code != filter2 ) return;

						c += v._count;
						x += v._total;
						if( parseInt(code) < 400 )
						{
							s += v._count;
							t += v._total;
							if( v._total / v._count > m ) m = v._total / v._count;
						}
						else
						{
							e += v._count;
						}
					});
				});

				if( e+s+t+c+m == 0 )
				{
					// missing data
					errors.push(0);
					success.push(0);
					time.push(0);
					max.push(0);
					count.push(0);
					sum.push(0);
				}
				else
				{
					errors.push((e/(e+s))*100);
					success.push((s/(e+s))*100);
					time.push(Math.round(t/c/100000)/10);
					max.push(Math.round(m/100000)/10);
					count.push(c);
					sum.push(x);
				}
				j++;
			}
		}

		return {errors: errors, success: success, time: time, max: max, count: count, sum: sum};
	}

	_drawTable(data, name, filter1, filter2)
	{
		var self = this;

		var table = null;
		if( name == 'endpoint' ) table = this.dom.querySelector('#metrics_api table');
		else if( name == 'user' ) table = this.dom.querySelector('#metrics_user table');
		else if( name == 'custom' ) table = this.dom.querySelector('#metrics_custom table');
		if( !table ) return;

		while( table.firstChild ) table.firstChild.remove();

		var columns = new Set();
		var rows = {};

		var col_hit = Translator.get('metrics.table.hits');
		var col_sum = Translator.get('metrics.table.sum');

		data.forEach(slot =>
		{
			if( slot._from < self._from || slot._from > self._to || !slot[name] ) return;
			Object.entries(slot[name]).forEach(([e, d]) =>
			{
				if( filter1 && e != filter1 ) return;
				Object.entries(d).forEach(([code, v]) =>
				{
					if( filter2 && code != filter2 ) return;

					if( name == 'custom' )
					{
						columns.add(col_hit);
						columns.add(col_sum);
						if( !rows[code] ) rows[code] = {};
						if( !rows[code][col_hit] ) rows[code][col_hit] = v._count;
						else rows[code][col_hit] += v._count;
						if( !rows[code][col_sum] ) rows[code][col_sum] = v._total;
						else rows[code][col_sum] += v._total;
					}
					else
					{
						columns.add(code);
						if( !rows[e] ) rows[e] = {};
						if( !rows[e][code] ) rows[e][code] = v._count;
						else rows[e][code] += v._count;
					}
				});
			});
		});

		columns = [...columns].sort((a, b) => { return a > b ? 1 : -1; });

		table.append(
			Node.thead(Node.tr(
			[
				Node.th(),
				columns.map(c => Node.th(safeHtml(c)))
			])),
			Node.tbody(Object.entries(rows).sort(([a1,a2],[b1,b2]) => { return a1 > b1 ? 1 : -1; }).map(([e, d]) => Node.tr(
			[
				Node.td(safeHtml(e)),
				columns.map(code =>
				{
					if( !d[code] ) return Node.td("0");
					else return Node.td(safeHtml(d[code] + ""));
				})
			])))
		);
	}

	switchTab(node)
	{
		if( node.nodeName !== 'SPAN' ) return;

		var index = Array.prototype.indexOf.call(node.parentNode.childNodes, node) + 1;
		var t = node.parentNode.parentNode;
		t.dataset.tab = index;
		t.classList.toggle('changed');

		if( index == 1 ) this._drawGraph('api');
		else if( index == 2 ) this._drawGraph('user');
		else if( index == 3 ) this._drawGraph('custom');
	}

	codeMetrics()
	{
		var m = Modal.alert([
			Node.h2(Translator.get('code.sample')),
			Node.p(Translator.get('code.metrics')),
			Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
				+ '\n\t.parameter("quantity")'
				+ '\n\t.process((data, user) -&gt; {\n\t\tApi.metrics("ordered", data.asLong("quantity"));\n\t\t...\n\t});')),
			Node.p(Node.a({href: "https://uniqorn.dev/doc#debug-metrics", target: "_blank"}, Translator.get('code.doc'))),
			Node.p(Node.a({href: "https://uniqorn.dev/javadoc#api-metrics", target: "_blank"}, Translator.get('code.javadoc')))
		]);

		Prism.highlightElement(m.dom.querySelector('code'));
	}

	clearMetrics()
	{
		var self = this;

		Modal.prompt(Translator.get('metrics.delete.confirm'),
			Node.input({type: 'date'})).then(form =>
		{
			if( !form.value ) return;

			self.dom.classList.add('wait');
			Ajax.delete('/api/manager/metrics', {data: {until: form.valueAsDate.getTime()}}).then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('metrics.delete.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('metrics.delete.error'));
			});
		}, () => {});
	}

	downloadMetrics()
	{
		var self = this;

		Modal.prompt(Translator.get('metrics.download.confirm'),
			Node.form(
			[
				Node.input({type: 'date', name: 'from'}),
				Node.input({type: 'date', name: 'to'})
			])).then(form =>
		{
			if( !form.from.value || !form.to.value ) return;

			self.dom.classList.add('wait');
			Ajax.get('/api/manager/metrics/download', {data: {from: form.from.valueAsDate.getTime(), to: form.to.valueAsDate.getTime()}, responseType: 'blob'}).then((response) =>
			{
				self.dom.classList.remove('wait');
				var name = response.headers["content-disposition"] || undefined;
				if( name ) name = name.match(/filename=([^;\n^]*)/i)[1].replaceAll('"','');
				Node.a({href: URL.createObjectURL(response.response), download: name||'metrics.zip', target: '_blank'}).click();
				Notify.success(Translator.get('metrics.download.success'));
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('metrics.download.error'));
			});
		}, () => {});
	}
}

const page = new MetricsPage();
export { page as default };
