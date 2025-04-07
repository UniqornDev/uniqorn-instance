
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.endpoints.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('endpoints');
				document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'endpoints'));
				
				this.init();
				return Promise.resolve();
			},
			
			hide: function()
			{
				while(this.dom.firstChild) this.dom.firstChild.remove();
				return Promise.resolve(); 
			},
			
			init: function()
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
						Node.div([
							Translator.get('endpoints.prefix.global'),
							Node.span({className: 'prefix g'}, ae.safeHtml(result.response.prefix))
						])
					);
					
					Node.append(self.dom, list.map(w => Node.section({dataset: {id: w.id, name: w.name, prefix: w.prefix}}, 
					[
						Node.h2([
							ae.safeHtml(w.name),
							Node.span({className: 'prefix g'}, ae.safeHtml(result.response.prefix)),
							w.prefix ? Node.span({className: 'prefix w'}, ae.safeHtml(w.prefix)) : null,
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
						Node.ol(w.endpoints.sort((a, b) => { return a.path.toUpperCase() > b.path.toUpperCase() ? 1 : -1; }).map(e => Node.li(
						{
							dataset: {id: e.id}
						},
						[
							Node.div({className: 'endpoint ' + 
								(['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(e.method)?e.method:'OTHER') + 
								(!e.enabled?' disabled':'')},
							[
								Node.span({className: 'method ' + (['GET', 'POST', 'PUT', 'DELETE', 'PATCH'].includes(e.method)?e.method:'OTHER')}, e.method||'-'),
								ae.safeHtml(result.response.prefix + w.prefix + (e.path ? e.path : Translator.get('endpoints.nopath'))),
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
										Notify.error(Translator.get('endpoints.endpoint.' + (enabled?'enable':'disable') + ".error"));
									});
								}})
							])
						])))
					])));
					
					self.dom.classList.remove('wait');
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
			},
			
			// =========================
			//
			// WORKSPACE
			//
			// =========================
			
			addWorkspace: function()
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('endpoints.workspace.add')),
					Node.form([
						Node.input({type: 'text', name: 'name', placeholder: Translator.get('endpoints.workspace.name')}),
						Node.input({type: 'text', name: 'prefix', placeholder: Translator.get('endpoints.workspace.prefix')})
					])
				).then((form) =>
				{
					if( !form.elements.name.value )
					{
						Notify.warning(Translator.get('endpoints.workspace.empty'));
						return;
					}
					if( form.elements.prefix.value.length > 0 )
					{
						if( !form.elements.prefix.value.startsWith("/") )
							form.elements.prefix.value = "/" + form.elements.prefix.value;
						if( form.elements.prefix.value.endsWith("/") )
							form.elements.prefix.value = form.elements.prefix.value.slice(0,-1);
						if( !/^\/(?!\.{1,2}(\/|$))(?:[^\/?#]+(?:\/(?!\.{1,2}(\/|$))[^\/?#]+)*)?$/.test(form.elements.prefix.value) )
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
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('endpoints.workspace.error'));
					});
				}, () => {});
			},
			
			editWorkspace: function(id, name, prefix)
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('endpoints.workspace.edit')),
					Node.form([
						Node.input({type: 'text', name: 'name', placeholder: Translator.get('endpoints.workspace.name'), value: name}),
						Node.input({type: 'text', name: 'prefix', placeholder: Translator.get('endpoints.workspace.prefix'), value: prefix||''})
					])
				).then((form) =>
				{
					if( !form.elements.name.value )
					{
						Notify.warning(Translator.get('endpoints.workspace.empty'));
						return;
					}
					if( form.elements.prefix.value.length > 0 )
					{
						if( !form.elements.prefix.value.startsWith("/") )
							form.elements.prefix.value = "/" + form.elements.prefix.value;
						if( form.elements.prefix.value.endsWith("/") )
							form.elements.prefix.value = form.elements.prefix.value.slice(0,-1);
						if( !/^\/(?!\.{1,2}(\/|$))(?:[^\/?#]+(?:\/(?!\.{1,2}(\/|$))[^\/?#]+)*)?$/.test(form.elements.prefix.value) )
						{
							Notify.warning(Translator.get('endpoints.workspace.invalid'));
							return;
						}
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
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('endpoints.workspace.edit.error'));
					});
				}, () => {});
			},
			
			removeWorkspace: function(id, name)
			{
				var self = this;				
				Modal.confirm(Translator.get('endpoints.workspace.delete.confirm', name), [
					Translator.get('remove'),
					Translator.get('cancel'),
				]).then(index =>
				{
					if( index > 0 ) return;
					
					self.dom.classList.add('wait');
					Ajax.delete('/api/contributor/workspace/' + encodeURIComponent(id)).then(() =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoints.workspace.delete.success'));
						self.init();
					}, () =>
					{
						self.dom.classList.remove('wait');
						Notify.error(Translator.get('endpoints.workspace.delete.error'));
					});
				}, () => {});
			},
			
			// =========================
			//
			// ENDPOINT
			//
			// =========================
			
			addEndpoint: function(workspace)
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
			},
			
			addEndpoint_review: function(workspace, code)
			{
				var self = this;
				var ci = Node.create('code-input', {lang: "Java", 'line-numbers': true});
				ci.setAttribute("value", code||'');
				
				var m = Modal.custom([
					Node.h2(Translator.get('endpoints.endpoint.add')),
					Node.div({className: 'codewrapper'}, ci),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							
							m.dom.classList.add('wait');
							Ajax.post('/api/contributor/endpoint', {data: {workspace: workspace, code: ci.value}}).then(() =>
							{
								m.ok();
								Notify.success(Translator.get('endpoints.endpoint.deploy.success'));
								self.init();
							}, (error) =>
							{	
								m.dom.classList.remove('wait');
								if( error.response && error.response.error && error.response.error.line )
									Modal.alert(ae.safeHtml(error.response.error.message));
								else
									Notify.error(Translator.get('endpoints.endpoint.deploy.error'));
							});
						}}, Translator.get('endpoints.endpoint.deploy')),
						Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
					])
				]);
			},
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };