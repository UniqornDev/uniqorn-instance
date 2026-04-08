import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml } from 'core';
css('endpoints');

class EndpointsPage extends Page
{
	async show()
	{
		this.dom.classList.add('endpoints');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'endpoints'));

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

		Ajax.get('/api/contributor/workspaces', {data: {full: true}}).then(result =>
		{
			var list = result.response.workspaces.sort((a, b) => { return a.name.toUpperCase() > b.name.toUpperCase() ? 1 : -1; });

			self.dom.append(
				Node.div({className: 'action'},
				[
					Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.addWorkspace(); }}, [
						Node.span({className: 'icon'}, 'folder'),
						Node.span(Translator.get('endpoints.workspace.add'))])
				]),
				Node.div({className: 'search'}, [
					Node.input({type: 'search', input: function()
					{
						self.filter(this.value);
					}}),
					Node.span({className: 'icon'}, 'search')
				]),
				Node.div([
					Translator.get('endpoints.prefix.global'),
					Node.span({className: 'prefix g'}, safeHtml(result.response.prefix))
				])
			);

			Node.append(self.dom, list.map(w => Node.section({dataset: {id: w.id, name: w.name, prefix: w.prefix}},
			[
				Node.h2([
					safeHtml(w.name),
					Node.span({className: 'prefix g'}, safeHtml(result.response.prefix)),
					w.prefix ? Node.span({className: 'prefix w'}, safeHtml(w.prefix)) : null,
					Node.div({className: 'small_action'},
					[
						Node.span({className: 'icon', click: function()
						{
							let x = this.parentNode.parentNode.parentNode.dataset;
							self.editWorkspace(x.id, x.name, x.prefix);
						}, dataset: {tooltip: Translator.get('edit')}}, 'edit'),
						Node.span({className: 'icon', click: function()
						{
							let x = this.parentNode.parentNode.parentNode.dataset;
							self.removeWorkspace(x.id, x.name);
						}, dataset: {tooltip: Translator.get('remove')}}, 'delete'),
						Node.span({className: 'icon', click: function()
						{
							let x = this.parentNode.parentNode.parentNode.dataset;
							self.addEndpoint(x.id);
						}, dataset: {tooltip: Translator.get('endpoints.endpoint.add')}}, 'webhook')
					])
				]),
				Node.ol(w.endpoints.sort((a, b) => { return (a.path||'').toUpperCase() > (b.path||'').toUpperCase() ? 1 : -1; }).map(e => Node.li(
				{
					dataset: {id: e.id}
				},
				[
					Node.div({className: 'endpoint ' +
						(['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(e.method)?e.method:'OTHER') +
						(!e.enabled?' disabled':'')},
					[
						Node.span({className: 'method ' + (['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(e.method)?e.method:'OTHER')}, e.method||'-'),
						Node.span(safeHtml(result.response.prefix + w.prefix + (e.path ? e.path : Translator.get('endpoints.nopath')))),
						Node.div({className: 'small_action'},
						[
							Node.span({className: 'icon', click: function()
							{
								location.href = "#endpoint?id=" + this.parentNode.parentNode.parentNode.dataset.id;
							}, dataset: {tooltip: Translator.get('info')}}, 'settings')
						]),
						Node.span({className: 'switch', click: function(e)
						{
							var div = this.parentNode;
							var enabled = div.classList.contains('disabled');
							Ajax.put('/api/contributor/endpoint/' + encodeURIComponent(div.parentNode.dataset.id), {data: {enabled: enabled}}).then(result =>
							{
								div.classList.toggle('disabled', !enabled);
								Notify.success(Translator.get('endpoints.endpoint.' + (enabled?'enable':'disable') + ".success"));
							}, (error) =>
							{
								if( error.response && error.response.error && error.response.error.message )
									Notify.error(safeHtml(error.response.error.message));
								else
									Notify.error(Translator.get('endpoints.endpoint.' + (enabled?'enable':'disable') + ".error"));
							});
						}})
					])
				])))
			])));

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
				if( !words[w].test(p.firstChild.firstChild.textContent) && !words[w].test(p.firstChild.children[1].textContent) )
				{
					p.classList.add('hidden');
					return;
				}
			}
			p.classList.remove('hidden');
		});

		// hide main section if empty
		[].slice.call(this.dom.querySelectorAll('section')).forEach(div =>
		{
			if( !value || value.length == 0 )
			{
				div.classList.remove('hidden');
			}
			else if( !!div.querySelector('li:not(.hidden)') )
			{
				div.classList.add('open')
				div.classList.remove('hidden')
			}
			else
			{
				div.classList.add('hidden')
			}
		});
	}

	// =========================
	//
	// WORKSPACE
	//
	// =========================

	addWorkspace()
	{
		var self = this;
		Modal.prompt(
			Node.h2(Translator.get('endpoints.workspace.add')),
			Node.form([
				Node.input({type: 'text', name: 'name', beforeinput: function(e) { if( /[^a-zA-Z0-9._-]/.test(e.data) ) e.preventDefault(); return false; }, placeholder: Translator.get('endpoints.workspace.name')})
			])
		).then((form) =>
		{
			if( !form.elements.name.value )
			{
				Notify.warning(Translator.get('endpoints.workspace.empty'));
				return;
			}
			if( form.elements.name.value.length > 0 )
			{
				if( !/^[a-zA-Z0-9_.-]+$/.test(form.elements.name.value) )
				{
					Notify.warning(Translator.get('endpoints.workspace.invalid'));
					return;
				}
			}

			self.dom.classList.add('wait');
			Ajax.post('/api/contributor/workspace', {data: form}).then(result =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('endpoints.workspace.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('endpoints.workspace.error'));
			});
		}, () => {});
	}

	editWorkspace(id, name, prefix)
	{
		var self = this;
		Modal.prompt(
			Node.h2(Translator.get('endpoints.workspace.edit')),
			Node.form([
				Node.input({type: 'text', name: 'name', beforeinput: function(e) { if( /[^a-zA-Z0-9._-]/.test(e.data) ) e.preventDefault(); return false; }, placeholder: Translator.get('endpoints.workspace.name'), value: name}),
				Node.input({type: 'text', name: 'message', placeholder: Translator.get('commit'), max: 50})
			])
		).then((form) =>
		{
			if( !form.elements.name.value )
			{
				Notify.warning(Translator.get('endpoints.workspace.empty'));
				return;
			}

			self.dom.classList.add('wait');
			Ajax.put('/api/contributor/workspace/' + encodeURIComponent(id), {data: form}).then(result =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('endpoints.workspace.edit.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('endpoints.workspace.edit.error'));
			});
		}, () => {});
	}

	removeWorkspace(id, name)
	{
		var self = this;
		Modal.prompt(
			Translator.get('endpoints.workspace.delete.confirm', name),
			Node.form(
				Node.input({type: 'text', name: 'value', placeholder: Translator.get('commit'), max: 50})
			)
		).then(form =>
		{
			self.dom.classList.add('wait');
			Ajax.delete('/api/contributor/workspace/' + encodeURIComponent(id), {data: {message: form.value.value}}).then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('endpoints.workspace.delete.success'));
				self.init();
			}, (error) =>
			{
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('endpoints.workspace.delete.error'));
				self.dom.classList.remove('wait');
			});
		}, () => {});
	}

	// =========================
	//
	// ENDPOINT
	//
	// =========================

	addEndpoint(workspace)
	{
		var self = this;

		var code = "import uniqorn.*;\n\n"
			+ "public class Custom implements Supplier<Api> {\n"
			+ "\tpublic Api get() {\n"
			+ "\t\treturn new Api(\"/hello\", \"GET\")\n"
			+ "\t\t\t.process(data -> {\n"
			+ "\t\t\t\treturn JSON.object().put(\"success\", true);\n"
			+ "\t\t\t});\n"
			+ "\t}\n"
			+ "}";

		this.addEndpoint_review(workspace, code);
	}

	addEndpoint_review(workspace, code)
	{
		var self = this;
		var ci = Node.create('code-input', {lang: "Java", 'line-numbers': true});
		ci.setAttribute("value", code||'');
		var msg = Node.input({type: 'text', name: 'value', placeholder: Translator.get('commit'), max: 50});

		var m = Modal.custom([
			Node.h2(Translator.get('endpoints.endpoint.add')),
			Node.div({className: 'codewrapper'}, ci),
			msg,
			Node.div({className: 'action'},
			[
				Node.button({click: function(e)
				{
					e.preventDefault();

					m.dom.classList.add('wait');
					Ajax.post('/api/contributor/endpoint', {data: {workspace: workspace, code: ci.value, message: msg.value}}).then((result) =>
					{
						m.ok();

						if( result.response.error )
						{
							// in case of error, redirect to the endpoint page
							if( result.response.error.line )
								Modal.alert(safeHtml(result.response.error.message));
							location.href = "#endpoint?id=" + result.response.id;
						}
						else
						{
							Notify.success(Translator.get('endpoints.endpoint.deploy.success'));
							self.init();
						}
					}, (error) =>
					{
						m.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Modal.alert(safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('endpoints.endpoint.deploy.error'));
					});
				}}, Translator.get('endpoints.endpoint.deploy')),
				Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
			])
		]);

		m.dom.addEventListener('dragover', function(event) { event.preventDefault(); });
		m.dom.addEventListener('drop', function(event) {
			event.preventDefault();
			var file = event.dataTransfer.files[0];
			if( file && file.name.endsWith('.java') )
			{
				var reader = new FileReader();
				reader.onload = function(e)
				{
					ci.setAttribute("value", e.target.result||'');
				};
				reader.readAsText(file);
			}
			else
				Notify.error(Translator.get('code.file.invalid'));
		});
	}
}

const page = new EndpointsPage();
export { page as default };
