import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml, urlValue } from 'core';
css('endpoint');

class EndpointPage extends Page
{
	async show()
	{
		var id = urlValue('id');
		if( !id )
		{
			location.href = "#endpoints";
			return;
		}

		this.dom.classList.add('endpoint');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'endpoints'));

		this.eid = id;
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
							Node.span({className: 'text'}, safeHtml(result.response.summary)||Translator.get('endpoint.empty'))
						]),
						Node.p([
							Node.span({className: 'title'}, Translator.get('endpoint.description')),
							Node.span({className: 'text'}, safeHtml(result.response.description)||Translator.get('endpoint.empty'))
						]),
						Node.p([
							Node.span({className: 'title'}, Translator.get('endpoint.returns')),
							Node.span({className: 'text'}, safeHtml(result.response.returns)||Translator.get('endpoint.empty'))
						]),
						Node.p([
							Node.span({className: 'title'}, Translator.get('endpoint.parameters')),
							Object.keys(result.response.parameters).length == 0 ? Translator.get('endpoint.no_parameters') :
							Node.div(
								Object.entries(result.response.parameters).map(([name, description]) => Node.p({className: 'param'}, [
									Node.span({className: 'tag'}, safeHtml(name)),
									Node.span(safeHtml(description)||Translator.get('endpoint.empty'))
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
									}, dataset: {tooltip: Translator.get('edit')}}, 'edit')
								])
							])
						]),
						result.response.versions.map(v => Node.li({dataset: {id: v.id}},
						[
							Node.h3([
								Node.span({className: 'icon'}, 'graph_1'),
								Node.span(safeHtml((v.message||'').split('\n')[0].substr(0, 50))),
								Node.div({className: 'small_action'},
								[
									Node.span({className: 'icon', click: function()
									{
										let data = this.parentNode.parentNode.parentNode.dataset;
										self.viewVersion(data.id);
									}, dataset: {tooltip: Translator.get('endpoint.view')}}, 'description')
								])
							]),
							Node.p([
								Translator.get('endpoint.version.author', safeHtml(v.author)),
								Node.br(),
								Translator.get('endpoint.version.date', new Date(v.date).toLocaleString([], {dateStyle: 'medium', timeStyle: 'medium'}))
							])
						]))
					])
				]),
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

	removeEndpoint()
	{
		var self = this;

		Modal.prompt(
			Translator.get('endpoint.delete.confirm'),
			Node.form(
				Node.input({type: 'text', name: 'value', placeholder: Translator.get('commit'), max: 50})
			)
		).then(form =>
		{
			self.dom.classList.add('wait');
			Ajax.delete('/api/contributor/endpoint/' + encodeURIComponent(self.eid), {data: {message: form.value.value}}).then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('endpoint.delete.success'));
				location.href = '#endpoints';
			}, (error) =>
			{
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('endpoint.delete.error'));
				self.dom.classList.remove('wait');
			});
		}, () => {});
	}

	codeDoc()
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
	}

	editEndpoint(code)
	{
		var self = this;
		var ci = Node.create('code-input', {lang: "Java", 'line-numbers': true});
		ci.setAttribute("value", code||'');
		var msg = Node.input({type: 'text', name: 'value', placeholder: Translator.get('commit'), max: 50});

		var m = Modal.custom([
			Node.h2(Translator.get('endpoint.head.update')),
			Node.div({className: 'codewrapper'}, ci),
			msg,
			Node.div({className: 'action'},
			[
				Node.button({click: function(e)
				{
					e.preventDefault();

					m.dom.classList.add('wait');
					Ajax.put('/api/contributor/endpoint/' + encodeURIComponent(self.eid), {data: {code: ci.value, message: msg.value}}).then(() =>
					{
						m.ok();
						Notify.success(Translator.get('endpoint.head.success'));
						self.init();
					}, (error) =>
					{
						m.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Modal.alert(safeHtml(error.response.error.message));
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
	}

	viewVersion(id)
	{
		var self = this;
		self.dom.classList.add('wait');
		Ajax.get('/api/contributor/endpoint/' + encodeURIComponent(self.eid) + '/version/' + encodeURIComponent(id)).then(result =>
		{
			self.dom.classList.remove('wait');
			var m = Modal.alert([
				Node.h2(safeHtml((result.response.message||'').split('\n')[0].substr(0, 50))),
				Node.p(Translator.get('endpoint.version.author', safeHtml(result.response.author))),
				Node.p(Translator.get('endpoint.version.date', new Date(result.response.date).toLocaleString([], {dateStyle: 'medium', timeStyle: 'medium'}))),
				Node.pre(Node.code({className: 'language-java'}, result.response.code))
			]);

			Prism.highlightElement(m.dom.querySelector('code'));
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('endpoint.version.fetch.error'));
			self.dom.classList.remove('wait');
		});
	}
}

const page = new EndpointPage();
export { page as default };
