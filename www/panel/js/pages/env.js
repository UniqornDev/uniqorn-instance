
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.env.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('env');
				document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'env'));
				
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
				
				Ajax.get('/api/contributor/env').then(result =>
				{
					var list = result.response.sort((a, b) => { return a.name > b.name ? 1 : -1; });
					
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
								Translator.get('env.title'),
								Node.div({className: 'small_action'},
								[
									Node.span({className: 'icon', click: () => { self.setEnv(); }, dataset: {tooltip: Translator.get('env.add')}}, 'add'),
									Node.span({className: 'icon', click: () => { self.codeEnv(); }, dataset: {tooltip: Translator.get('code')}}, 'code')
								])
							]),
							Node.p(Translator.get('env.explain')),
							Node.ol(
								list.map(c => Node.li({dataset: {name: c.name, value: c.value, description: c.description}},
								[
									Node.h3([
										Node.span(ae.safeHtml(c.name)),
										Node.div({className: 'small_action'},
										[
											Node.span({className: 'icon', click: function()
											{
												let x = this.parentNode.parentNode.parentNode.dataset;
												self.setEnv(x.name, x.description, x.value);
											}, dataset: {tooltip: Translator.get('edit')}}, 'edit'),
											Node.span({className: 'icon', click: function()
											{
												let x = this.parentNode.parentNode.parentNode.dataset;
												self.removeEnv(x.name);
											}, dataset: {tooltip: Translator.get('remove')}}, 'delete')
										])
									]),
									Node.p(ae.safeHtml(c.description))
								]))
							)
						])
					);
					self.dom.classList.remove('wait');
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
			},
			
			filter: function(value)
			{
				var words = (value||'').split(/\s+/g).map(w => new RegExp((w||'').replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&'), 'i'));
				
				[].slice.call(this.dom.querySelectorAll('section li')).forEach(p =>
				{
					if( !value || value.length == 0 ) { p.classList.remove('hidden'); return; }
					
					for (var w = 0; w < words.length; w++)
					{
						if( !words[w].test(p.firstChild.firstChild.textContent) )
						{
							p.classList.add('hidden');
							return;
						}
					}
					p.classList.remove('hidden');
				});
			},
			
			setEnv: function(name, description, value)
			{
				var self = this;
				Modal.prompt(Node.h2(Translator.get('env.set')),
					Node.form([
						!!name ? [
							Node.p({className: 'green'}, ae.safeHtml(name)),
							Node.input({type: 'hidden', name: 'name', value: name})
						] : [
							Node.p(Translator.get('env.add.explain')), 
							Node.input({type: 'text', name: 'name', placeholder: Translator.get('env.name')})
						],
						Node.textarea({name: 'description', value: description||'', placeholder: Translator.get('env.description')}),
						Node.textarea({name: 'value', value: value||'', placeholder: Translator.get('env.value')})
					])
				).then((form) =>
				{
					if( !form.elements.name.value && !form.elements.value.value )
						return;
					
					self.dom.classList.add('wait');
					Ajax.put('/api/contributor/env/' + encodeURIComponent(form.elements.name.value), {data: form}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('env.add.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('env.add.error'));
					});
				}, () => {});
			},
			
			codeEnv: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.env')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
						+ '\n\t.process((data, user) -&gt; {\n\t\tString apiKey = Api.env("api_key").asString();\n\t\t...\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
			
			removeEnv: function(name)
			{
				var self = this;
				
				Modal.confirm(Translator.get('env.delete.confirm', name), [
					Translator.get('remove'),
					Translator.get('cancel'),
				]).then(index =>
				{
					if( index > 0 ) return;
					
					self.dom.classList.add('wait');
					Ajax.delete('/api/contributor/env/' + encodeURIComponent(name)).then(() =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('env.delete.success'));
						self.init();
					}, () =>
					{
						self.dom.classList.remove('wait');
						Notify.error(Translator.get('env.delete.error'));
					});
				}, () => {});
			}
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };