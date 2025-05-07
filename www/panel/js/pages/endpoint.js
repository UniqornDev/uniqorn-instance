
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.endpoint.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				var id = ae.urlValue('id');
				if( !id )
				{
					location.href = "#endpoints";
					return Promise.resolve();
				}
				
				this.dom.classList.add('endpoint');
				document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'endpoints'));
				
				this.eid = id;
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
				
				this.dom.append(
					Node.div({className: 'back', click: () => { location.href = '#endpoints'; }},
					[
						Node.span({className: 'icon'}, 'arrow_back'),
						Node.span(Translator.get('endpoint.back'))
					]),
					Node.div({className: 'action'},
					[
						Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.removeEndpoint(); }}, [
							Node.span({className: 'icon'}, 'delete'), 
							Node.span(Translator.get('endpoint.delete'))])
					])
				);
				
				Ajax.get('/api/contributor/endpoint/' + encodeURIComponent(this.eid)).then(result =>
				{
					self.dom.append(
						Node.section(
						[
							Node.h2(
							[
								Translator.get('endpoint.documentation'),
								Node.div({className: 'small_action'},
								[
									Node.span({className: 'icon', click: () => { self.codeDoc(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
								])
							]),
							Node.div(Node.div({className: 'detail'}, [
								Node.p([
									Node.span({className: 'title'}, Translator.get('endpoint.summary')),
									Node.span({className: 'text'}, ae.safeHtml(result.response.summary)||Translator.get('endpoint.empty'))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('endpoint.description')),
									Node.span({className: 'text'}, ae.safeHtml(result.response.description)||Translator.get('endpoint.empty'))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('endpoint.returns')),
									Node.span({className: 'text'}, ae.safeHtml(result.response.returns)||Translator.get('endpoint.empty'))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('endpoint.parameters')),
									Object.keys(result.response.parameters).length == 0 ? Translator.get('endpoint.no_parameters') :
									Node.div(
										Object.entries(result.response.parameters).map(([name, description]) => Node.p({className: 'param'}, [
											Node.span({className: 'tag'}, ae.safeHtml(name)),
											Node.span(ae.safeHtml(description)||Translator.get('endpoint.empty'))
										]))
									)
								])
							]))
						]),
						Node.section(
						[
							Node.h2(
							[
								Translator.get('endpoint.versions')
							]),
							Node.ol({className: 'versions'}, 
							[
								Node.li({className: 'head', dataset: {code: result.response.head.code}}, [
									Node.h3([
										Node.span({className: 'icon'}, 'deployed_code'),
										Node.span(Translator.get('endpoint.head')),
										Node.div({className: 'small_action'},
										[
											Node.span({className: 'icon', click: function()
											{
												self.editEndpoint(this.parentNode.parentNode.parentNode.dataset.code);
											}, dataset: {tooltip: Translator.get('edit')}}, 'edit'),
											Node.span({className: 'icon', click: function()
											{
												self.tagEndpoint();
											}, dataset: {tooltip: Translator.get('endpoint.tag')}}, 'bookmark')
										])
									]),
									Node.p(Translator.get('endpoint.modified', new Date(result.response.head.date).toLocaleString([], {dateStyle: 'medium', timeStyle: 'medium'}))),
								]),
								result.response.versions.map(v => Node.li({dataset: {id: v.id, name: v.name}}, 
								[
									Node.h3([
										Node.span({className: 'icon'}, 'graph_1'),
										Node.span(ae.safeHtml(v.name)),
										Node.div({className: 'small_action'},
										[
											Node.span({className: 'icon', click: function()
											{
												let data = this.parentNode.parentNode.parentNode.dataset;
												self.viewVersion(data.id, data.name);
											}, dataset: {tooltip: Translator.get('endpoint.view')}}, 'description'),
											Node.span({className: 'icon', click: function()
											{
												let data = this.parentNode.parentNode.parentNode.dataset;
												self.editVersion(data.id, data.name);
											}, dataset: {tooltip: Translator.get('edit')}}, 'edit'),
											Node.span({className: 'icon', click: function()
											{
												let data = this.parentNode.parentNode.parentNode.dataset;
												self.restoreVersion(data.id, data.name);
											}, dataset: {tooltip: Translator.get('endpoint.restore')}}, 'publish'),
											Node.span({className: 'icon', click: function()
											{
												let data = this.parentNode.parentNode.parentNode.dataset;
												self.deleteVersion(data.id, data.name);
											}, dataset: {tooltip: Translator.get('remove')}}, 'delete')
										])
									]),
									Node.p(Translator.get('endpoint.version.date', new Date(v.date).toLocaleString([], {dateStyle: 'medium', timeStyle: 'medium'}))),
								]))
							])
						]),
					);
					
					self.dom.classList.remove('wait');
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
			},
			
			removeEndpoint: function()
			{
				var self = this;
				
				Modal.confirm(Translator.get('endpoint.delete.confirm'), [
					Translator.get('remove'),
					Translator.get('cancel'),
				]).then(index =>
				{
					if( index > 0 ) return;
					
					self.dom.classList.add('wait');
					Ajax.delete('/api/contributor/endpoint/' + encodeURIComponent(self.eid)).then(() =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoint.delete.success'));
						location.href = '#endpoints';
					}, () =>
					{
						self.dom.classList.remove('wait');
						Notify.error(Translator.get('endpoint.delete.error'));
					});
				}, () => {});
			},
			
			codeDoc: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('endpoint.documentation')),
					Node.p(Translator.get('code.endpoint.documentation')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")\n\t.summary("Test")\n\t'
						+ '.description("This endpoint is a simple test")\n\t'
						+ '.returns("Always returns success: true")'
						+ '\n\t.process(data -&gt; {\n\t\treturn JSON.object().put("success", true);\n\t});')),
					Node.p(Node.a({href: "https://uniqorn.dev/doc#start-code", target: "_blank"}, Translator.get('code.doc'))),
					Node.p(Node.a({href: "https://uniqorn.dev/javadoc#api-description", target: "_blank"}, Translator.get('code.javadoc')))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
			
			tagEndpoint: function()
			{
				var self = this;
				Modal.prompt(
					Translator.get('endpoint.version.tag'),
					Node.input({type: 'text', placeholder: Translator.get('endpoint.version.name')})
				).then(form =>
				{
					if( !form.value ) return;
					
					self.dom.classList.add('wait');
					Ajax.post('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version', {data: {name: form.value}}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoint.tag.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('endpoint.tag.error'));
					});
				}, () => {});
			},
			
			editEndpoint: function(code)
			{
				var self = this;
				var ci = Node.create('code-input', {lang: "Java", 'line-numbers': true});
				ci.setAttribute("value", code||'');
				
				var m = Modal.custom([
					Node.h2(Translator.get('endpoint.head.update')),
					Node.div({className: 'codewrapper'}, ci),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							
							m.dom.classList.add('wait');
							Ajax.put('/api/contributor/endpoint/' + encodeURIComponent(self.eid), {data: {code: ci.value}}).then(() =>
							{
								m.ok();
								Notify.success(Translator.get('endpoint.head.success'));
								self.init();
							}, (error) =>
							{	
								m.dom.classList.remove('wait');
								if( error.response && error.response.error && error.response.error.line )
									Modal.alert(ae.safeHtml(error.response.error.message));
								else
									Notify.error(Translator.get('endpoint.head.error'));
							});
						}}, Translator.get('update')),
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
			},
			
			viewVersion: function(id, name)
			{
				var self = this;
				self.dom.classList.add('wait');
				Ajax.get('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version/' + encodeURIComponent(id)).then(result =>
				{
					self.dom.classList.remove('wait');
					var m = Modal.alert([
						Node.h2(ae.safeHtml(result.response.name)),
						Node.pre(Node.code({className: 'language-java'}, result.response.code))
					]);
					
					Prism.highlightElement(m.dom.querySelector('code'));
				}, (error) =>
				{
					self.dom.classList.remove('wait');
					Notify.error(Translator.get('endpoint.version.fetch.error'));
				});
			},
			
			editVersion: function(id, name)
			{
				var self = this;
				Modal.prompt(
					Translator.get('endpoint.version.rename'),
					Node.input({type: 'text', value: name})
				).then(form =>
				{
					if( !form.value ) return;
					
					self.dom.classList.add('wait');
					Ajax.put('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version/' + encodeURIComponent(id), {data: {name: form.value}}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoint.version.rename.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						Notify.error(Translator.get('endpoint.version.rename.error'));
					});
				}, () => {});
			},
			
			restoreVersion: function(id, name)
			{
				var self = this;
				Modal.confirm(Translator.get('endpoint.version.restore.confirm', name), [
					Translator.get('endpoint.restore'),
					Translator.get('cancel'),
				]).then(index =>
				{
					if( index > 0 ) return;
					
					self.dom.classList.add('wait');
					Ajax.patch('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version/' + encodeURIComponent(id)).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoint.version.restore.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('endpoint.version.restore.error'));
					});
				}, () => {});
			},
			
			deleteVersion: function(id, name)
			{
				var self = this;
				Modal.confirm(Translator.get('endpoint.version.delete.confirm', name), [
					Translator.get('remove'),
					Translator.get('cancel'),
				]).then(index =>
				{
					if( index > 0 ) return;
					
					self.dom.classList.add('wait');
					Ajax.delete('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version/' + encodeURIComponent(id)).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('endpoint.version.delete.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						Notify.error(Translator.get('endpoint.version.delete.error'));
					});
				}, () => {});
			},
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };