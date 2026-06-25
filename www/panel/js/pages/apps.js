import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml } from 'core';
css('apps');

class AppsPage extends Page
{
	async show()
	{
		this.dom.classList.add('apps');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'apps'));

		this.init();
	}

	async hide()
	{
		while(this.dom.firstChild) this.dom.firstChild.remove();
	}

	init()
	{
		var self = this;
		this.dom.classList.add('wait');
		while(this.dom.firstChild) this.dom.firstChild.remove();

		Ajax.get('/api/contributor/oidc/app').then(result =>
		{
			var list = result.response.sort((a, b) => { return (a.name||'') > (b.name||'') ? 1 : -1; });

			self.dom.append(
				Node.div({className: 'search'}, [
					Node.input({type: 'search', input: function()
					{
						self.filter(this.value);
					}}),
					Node.span({className: 'icon'}, 'search')
				]),
				Node.section(
				[
					Node.h2([
						Translator.get('apps.title'),
						Node.div({className: 'small_action'},
						[
							Node.span({className: 'icon', click: () => { self.addApp(); }, dataset: {tooltip: Translator.get('apps.add')}}, 'add')
						])
					]),
					Node.p(Translator.get('apps.explain')),
					Node.p(Node.a({href: "https://uniqorn.dev/doc#apps", target: "_blank"}, Translator.get('apps.doc'))),
					list.length === 0
						? Node.p({className: 'empty'}, Translator.get('apps.empty'))
						: Node.ol(list.map(a => Node.li({dataset: {client_id: a.client_id, name: a.name, redirect_uri: a.redirect_uri}},
						[
							Node.h3([
								Node.span(safeHtml(a.name || a.client_id)),
								Node.div({className: 'small_action'},
								[
									Node.span({className: 'icon', click: function()
									{
										self.copyClientId(this.parentNode.parentNode.parentNode.dataset.client_id);
									}, dataset: {tooltip: Translator.get('apps.copy')}}, 'content_copy'),
									Node.span({className: 'icon', click: function()
									{
										let x = this.parentNode.parentNode.parentNode.dataset;
										self.removeApp(x.client_id, x.name);
									}, dataset: {tooltip: Translator.get('remove')}}, 'delete')
								])
							]),
							Node.p([
								Node.span({className: 'title'}, Translator.get('apps.client_id')),
								Node.code(safeHtml(a.client_id))
							]),
							Node.p([
								Node.span({className: 'title'}, Translator.get('apps.redirect')),
								Node.span(safeHtml(a.redirect_uri))
							])
						])))
				])
			);
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

	filter(value)
	{
		var words = (value||'').split(/\s+/g).map(w => new RegExp((w||'').replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&'), 'i'));

		[].slice.call(this.dom.querySelectorAll('section li')).forEach(p =>
		{
			if( !value || value.length == 0 ) { p.classList.remove('hidden'); return; }

			for (var w = 0; w < words.length; w++)
			{
				if( !words[w].test(p.textContent) )
				{
					p.classList.add('hidden');
					return;
				}
			}
			p.classList.remove('hidden');
		});
	}

	copyClientId(clientId)
	{
		var ok = () => Notify.success(Translator.get('apps.copied'));
		try
		{
			navigator.clipboard.writeText(clientId).then(ok, () => {});
		}
		catch(e) { /* clipboard unavailable */ }
	}

	addApp()
	{
		var self = this;
		Modal.prompt(Node.h2(Translator.get('apps.add')),
			Node.form([
				Node.input({type: 'text', name: 'name', placeholder: Translator.get('apps.name')}),
				Node.p(Translator.get('apps.redirect.explain')),
				Node.input({type: 'text', name: 'redirect_uri', placeholder: Translator.get('apps.redirect')})
			])
		).then((form) =>
		{
			if( !form.elements.name.value )
			{
				Notify.warning(Translator.get('apps.name.empty'));
				return;
			}
			if( !form.elements.redirect_uri.value )
			{
				Notify.warning(Translator.get('apps.redirect.empty'));
				return;
			}

			self.dom.classList.add('wait');
			Ajax.post('/api/contributor/oidc/app', {data: form}).then(result =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('apps.add.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('apps.add.error'));
			});
		}, () => {});
	}

	removeApp(clientId, name)
	{
		var self = this;

		Modal.confirm(Translator.get('apps.delete.confirm', name || clientId), [
			Translator.get('remove'),
			Translator.get('cancel'),
		]).then(index =>
		{
			if( index > 0 ) return;

			self.dom.classList.add('wait');
			Ajax.delete('/api/contributor/oidc/app/' + encodeURIComponent(clientId)).then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('apps.delete.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('apps.delete.error'));
			});
		}, () => {});
	}
}

const page = new AppsPage();
export { page as default };
