
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Translator', 'Ajax', 'Notify', 'page.home.css').then(([Page, Node, Translator, Ajax, Notify]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('home');
				document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'home'));
				
				this.init();
				return Promise.resolve();
			},
			
			hide: function()
			{
				return Promise.resolve(); 
			},
			
			init: function()
			{
				var self = this;
				this.dom.classList.add('wait');
				while(this.dom.firstChild) this.dom.firstChild.remove();
				
				Ajax.get('/api/contributor/status').then(result =>
				{
					Node.append(self.dom,
					[
						Node.div({className: 'plan ' + result.response.plan}, 
						[
							Translator.get('home.plan.' + result.response.plan),
							Node.p(Translator.get('home.plan'))
						]),
						result.response.warnings.map(w => Node.div({className: 'warning ' + w.type, dataset: {id: w.endpoint}, click: function()
						{
							location.href = "#endpoint?id=" + this.dataset.id;
						}},
						[
							Translator.get('home.warning.' + w.type, w.endpoint)
						])),
						Node.div({className: 'center'},
							result.response.limits.sort((a, b) => { return a.name > b.name ? 1 : -1; }).map(l => Node.div(
							{className: 'limit', dataset: {tooltip: Translator.get('home.limit.tooltip.' + l.name)}},
							[
								Node.h3(Translator.get('home.limit.' + l.name)),
								self.createGauge(
									'g_'+l.name, ['#d75d7f','#d75d7f','#d75d7f'], 
									l.max == 0 ? 0 : l.current/l.max*100, 
									self.withUnits(l.current) + ' / ' + self.withUnits(l.max)
								)
							]))
						),
					]);
					
					self.dom.classList.remove('wait');
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
			},
			
			createGauge: function(id, colors, percent, label)
			{
				if( !colors ) colors = ["#1eaa59", "#f1c40f", "#e84c3d"];
				
				var node = Node.svg({id: id, viewBox: '0 0 70 45', width: '100%'}, [
					Node.defs([
						Node.linearGradient({id: id+"_grad", gradientTransform: "rotate(15)"}, [
							Node.stop({offset: "10%", 'stop-color': colors[2]}),
							Node.stop({offset: "70%", 'stop-color': colors[1]}),
							Node.stop({offset: "100%", 'stop-color': colors[0]}),
						])
					]),
					Node.circle({
						r: "30", cx: "35", cy: "35", fill: "transparent",
						'stroke-width': "6",
						'stroke-dasharray': "100 188.5",
						transform: "rotate(174.5, 35, 35)",
						'stroke-linecap': "round",
						stroke: "#0002"
					}),
					Node.circle({
						r: "30", cx: "35", cy: "35", fill: "transparent",
						'stroke-width': "6",
						'stroke-dasharray': "100 188.5",
						'stroke-dashoffset': "100",
						transform: "rotate(174.5, 35, 35)",
						'stroke-linecap': "round",
						stroke: "url(#" + id + "_grad)"
					}),
					Node.text({
						x: "50%", y: "35", fill: "#000", 'font-size': "7",
						'font-family': "body, sans-serif",
						'dominant-baseline': "middle", 'text-anchor': "middle"
					})
				]);
				
				if( percent !== undefined )
					node.children[2].setAttribute('stroke-dashoffset', 100 - Math.min(100, Math.max(0, percent)));
				if( label !== undefined )
					node.children[3].textContent = label;
				
				return node;
			},
			
			setGauge: function(id, percent, label)
			{
				var svg = this.dom.querySelector('#' + id);
				svg.children[2].setAttribute('stroke-dashoffset', 100 - Math.min(100, Math.max(0, percent)));
				svg.children[3].textContent = label;
			},
			
			withUnits: function(n)
			{
				if( n < 1000 ) return (Math.round(n * 100) / 100) + '';
				n /= 1000;
				if( n < 1000 ) return (Math.round(n * 100) / 100) + 'k';
				n /= 1000;
				if( n < 1000 ) return (Math.round(n * 100) / 100) + 'M';
				n /= 1000;
				return (Math.round(n * 100) / 100) + 'G';
			},
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };