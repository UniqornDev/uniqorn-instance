
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('App', 'Page', 'Node', 'Translator', 'Ajax', 'Notify', 'ae.layout.css', 'ae.tab.css', 'ext/prism.js', 'ext/prism.css', 'ext/code-input.min.js', 'ext/code-input.min.css').then(([App, Page, Node, Translator, Ajax, Notify]) =>
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
						if( !li || !li.dataset.link ) return;
						
						this.querySelectorAll('li').forEach(e => e.classList.remove('selected'));
						li.classList.add('selected');
						location.hash = '#' + li.dataset.link;
					}}, [
						Node.aside({click: function() { location.hash = '#me'; }}, [
							Node.span({className: 'icon'}, 'person'), 
							Node.br(),
							ae.safeHtml(ae.username)
						])
					]),
					container
				);
				
				Ajax.get('/api/contributor/status').then(result =>
				{
					var menu = document.body.querySelector("nav");
					var plan = result.response.plan||"trial";
					
					menu.append(Node.ol([
						Node.li({className: location.hash == '#home' || location.hash == '' || location.hash == '#' ? 'selected' : '', dataset: {link: 'home'}}, [
							Node.span({className: 'icon'}, 'leaderboard'), 
							Translator.get('menu.home')
						]),
						Node.li({className: location.hash == '#endpoints' ? 'selected' : '', dataset: {link: 'endpoints'}}, [
							Node.span({className: 'icon'}, 'webhook'), 
							Translator.get('menu.endpoints')
						]),
						Node.li({className: location.hash == '#security' ? 'selected' : '', dataset: {link: 'security'}}, [
							Node.span({className: 'icon'}, 'security'), 
							Translator.get('menu.security')
						]),
						Node.li({className: location.hash == '#env' ? 'selected' : '', dataset: {link: 'env'}}, [
							Node.span({className: 'icon'}, 'tune'), 
							Translator.get('menu.env')
						]),
						Node.li({className: location.hash == '#storage' ? 'selected' : '', dataset: {link: 'storage'}}, [
							Node.span({className: 'icon'}, 'storage'), 
							Translator.get('menu.storage')
						]),
						plan == "trial" ? null : Node.li({className: location.hash == '#troubleshoot' ? 'selected' : '', dataset: {link: 'troubleshoot'}}, [
							Node.span({className: 'icon'}, 'bug_report'), 
							Translator.get('menu.troubleshoot')
						]),
						Node.li({className: 'external', click: function(e)
						{
							e.stopImmediatePropagation();
							window.open('https://uniqorn.dev/doc', '_blank').focus();
						}}, [
							Node.span({className: 'icon'}, 'open_in_new'), 
							Translator.get('menu.doc')
						]),
						Node.li({className: 'external', click: function(e)
						{
							e.stopImmediatePropagation();
							window.open('https://uniqorn.dev/javadoc', '_blank').focus();
						}}, [
							Node.span({className: 'icon'}, 'open_in_new'), 
							Translator.get('menu.javadoc')
						])
					]));
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
				
				App.container = container;
				codeInput.registerTemplate("syntax-highlighted", codeInput.templates.prism(Prism));
				
				return Promise.resolve(null);
			}
		}));
	}, (e) => { nok(e); });
});

export { x as default };