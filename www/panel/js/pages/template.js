
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('App', 'Page', 'Node', 'Translator', 'ae.layout.css', 'ae.tab.css').then(([App, Page, Node, Translator]) =>
	{
		ok(Object.assign(new Page(), 
		{
			show: function()
			{
				var self = this;
				var container = Node.main({id: "main_container"});
				
				document.body.append(
					Node.nav({click: function(e) {
						var li = e.target.closest('nav li');
						if( !li ) return;
						
						this.querySelectorAll('li').forEach(e => e.classList.remove('selected'));
						li.classList.add('selected');
						location.hash = '#' + li.dataset.link;
					}}, [
						Node.aside({click: function() { location.hash = '#me'; }}, [
							Node.span({className: 'icon'}, 'person'), 
							Node.br(),
							ae.safeHtml(ae.username)
						]),
						Node.ol([
							Node.li({className: location.hash == '#home' || location.hash == '' || location.hash == '#' ? 'selected' : '', dataset: {link: 'home'}}, [
								Node.span({className: 'icon'}, 'public'), 
								Translator.get('menu.endpoints')
							]),
							Node.li({className: location.hash == '#security' ? 'selected' : '', dataset: {link: 'security'}}, [
								Node.span({className: 'icon'}, 'security'), 
								Translator.get('menu.security')
							]),
							Node.li({className: location.hash == '#config' ? 'selected' : '', dataset: {link: 'config'}}, [
								Node.span({className: 'icon'}, 'tune'), 
								Translator.get('menu.config')
							]),
							Node.li({className: location.hash == '#workflow' ? 'selected' : '', dataset: {link: 'workflow'}}, [
								Node.span({className: 'icon'}, 'share'), 
								Translator.get('menu.workflow')
							]),
							Node.li({className: location.hash == '#endpoints' ? 'selected' : '', dataset: {link: 'endpoints'}}, [
								Node.span({className: 'icon'}, 'public'), 
								Translator.get('menu.endpoints')
							]),
							Node.li({className: location.hash == '#storage' ? 'selected' : '', dataset: {link: 'storage'}}, [
								Node.span({className: 'icon'}, 'storage'), 
								Translator.get('menu.storage')
							]),
							Node.li({className: location.hash == '#debug' ? 'selected' : '', dataset: {link: 'debug'}}, [
								Node.span({className: 'icon'}, 'bug_report'), 
								Translator.get('menu.debug')
							]),
							Node.li({className: location.hash == '#logs' ? 'selected' : '', dataset: {link: 'logs'}}, [
								Node.span({className: 'icon'}, 'description'), 
								Translator.get('menu.logs')
							]),
							Node.li({className: location.hash == '#metrics' ? 'selected' : '', dataset: {link: 'metrics'}}, [
								Node.span({className: 'icon'}, 'troubleshoot'), 
								Translator.get('menu.metrics')
							])
						])
					]),
					container
				);
				
				App.container = container;
				return Promise.resolve(null);
			}
		}));
	}, (e) => { nok(e); });
});

export { x as default };