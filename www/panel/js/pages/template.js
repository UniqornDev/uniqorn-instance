import { App, Page, Node, Translator, Ajax, Notify } from 'core';
import { locale, css, config, safeHtml } from 'core';
css('template');
css('ae.tab', config.corePath);

const loadScript = (src) => new Promise((resolve, reject) =>
{
	const s = document.createElement('script');
	s.src = src;
	s.onload = resolve;
	s.onerror = reject;
	document.head.appendChild(s);
});

css('ext/prism');
css('ext/code-input.min');
await loadScript(config.sitePath + 'js/ext/prism.js');
await loadScript(config.sitePath + 'js/ext/code-input.min.js');
await loadScript(config.sitePath + 'js/ext/chart.min.js');

await locale('default');

class TemplatePage extends Page
{
	async show()
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
					safeHtml(config.user.name)
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
				plan == "trial" || config.user.level === 'contributor' ? null : Node.li({className: location.hash == '#metrics' ? 'selected' : '', dataset: {link: 'metrics'}}, [
					Node.span({className: 'icon'}, 'monitoring'),
					Translator.get('menu.metrics')
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
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
			self.dom.classList.remove('wait');
		});

		App.instance.container = container;
		codeInput.registerTemplate("syntax-highlighted", codeInput.templates.prism(Prism));

		return Promise.resolve(null);
	}
}

const page = new TemplatePage();
export { page as default };
