import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml, config } from 'core';
css('troubleshoot');

class TroubleshootPage extends Page
{
	async show()
	{
		this.dom.classList.add('troubleshoot');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'troubleshoot'));

		this.init();
	}

	async hide()
	{
		if( this.ws_log )
		{
			this.ws_log.close();
			this.ws_log = null;
		}

		if( this.ws_debug )
		{
			this.ws_debug.close();
			this.ws_debug = null;
		}

		while(this.dom.firstChild) this.dom.firstChild.remove();
	}

	init()
	{
		var self = this;

		Node.append(this.dom,
		[
			Node.div({className: 'action'},
			[
				config.user.level === 'manager' ? Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.downloadLogs(); }}, [
					Node.span({className: 'icon'}, 'download'),
					Node.span(Translator.get('troubleshoot.download'))]) : null,
				config.user.level === 'manager' ? Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.clearLogs(); }}, [
					Node.span({className: 'icon'}, 'delete_forever'),
					Node.span(Translator.get('troubleshoot.delete'))]) : null
			]),
			Node.section(
			[
				Node.h2([
					Translator.get('troubleshoot.log'),
					Node.div({className: 'small_action'},
					[
						Node.span({className: 'icon', id: 'start_log_button', click: function() {
							if( this.classList.contains('disabled') ) return;
							self.startLog();
						}, dataset: {tooltip: Translator.get('troubleshoot.start')}}, 'play_arrow'),
						Node.span({className: 'icon disabled', id: 'stop_log_button', click: function() {
							if( this.classList.contains('disabled') ) return;
							self.stopLog();
						}, dataset: {tooltip: Translator.get('troubleshoot.stop')}}, 'stop'),
						Node.span({className: 'icon', click: () => { self.codeLog(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
						Node.span({className: 'icon', click: () => {
							var ol = document.getElementById('log_entries');
							while( ol.firstChild ) ol.firstChild.remove();
						}, dataset: {tooltip: Translator.get('troubleshoot.cleanup')}}, 'block')
					])
				]),
				Node.p(Translator.get('troubleshoot.log.explain')),
				Node.ol({id: 'log_entries'})
			]),
			Node.section(
			[
				Node.h2([
					Translator.get('troubleshoot.debug'),
					Node.div({className: 'small_action'},
					[
						Node.span({className: 'icon', id: 'start_debug_button', click: function() {
							if( this.classList.contains('disabled') ) return;
							self.startDebug();
						}, dataset: {tooltip: Translator.get('troubleshoot.start')}}, 'play_arrow'),
						Node.span({className: 'icon disabled', id: 'stop_debug_button', click: function() {
							if( this.classList.contains('disabled') ) return;
							self.stopDebug();
						}, dataset: {tooltip: Translator.get('troubleshoot.stop')}}, 'stop'),
						Node.span({className: 'icon', click: () => { self.codeDebug(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
						Node.span({className: 'icon', click: () => {
							var ol = document.getElementById('debug_entries');
							while( ol.firstChild ) ol.firstChild.remove();
						}, dataset: {tooltip: Translator.get('troubleshoot.cleanup')}}, 'block')
					])
				]),
				Node.p(Translator.get('troubleshoot.debug.explain')),
				Node.ol({id: 'debug_entries'})
			])
		]);
	}

	codeLog()
	{
		var m = Modal.alert([
			Node.h2(Translator.get('code.sample')),
			Node.p(Translator.get('code.log')),
			Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
				+ '\n\t.process((data, user) -&gt; {\n\t\ttry {\n\t\t\tApi.log(500, "Start transaction for {}", user.name());\n\t\t\t...\n\t\t} catch(Exception e) {'
				+ '\n\t\t\tApi.log(900, e);\n\t});')),
			Node.p(Node.a({href: "https://uniqorn.dev/doc#debug-log", target: "_blank"}, Translator.get('code.doc'))),
			Node.p(Node.a({href: "https://uniqorn.dev/javadoc#api-log", target: "_blank"}, Translator.get('code.javadoc')))
		]);

		Prism.highlightElement(m.dom.querySelector('code'));
	}

	codeDebug()
	{
		var m = Modal.alert([
			Node.h2(Translator.get('code.sample')),
			Node.p(Translator.get('code.debug')),
			Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
				+ '\n\t.process(data -&gt; {\n\t\tApi.debug("checkpoint", data);\n\t\t...\n\t});')),
			Node.p(Node.a({href: "https://uniqorn.dev/doc#debug-debug", target: "_blank"}, Translator.get('code.doc'))),
			Node.p(Node.a({href: "https://uniqorn.dev/javadoc#api-debug", target: "_blank"}, Translator.get('code.javadoc')))
		]);

		Prism.highlightElement(m.dom.querySelector('code'));
	}

	startLog()
	{
		var self = this;
		var start = document.getElementById('start_log_button');
		if( start.classList.contains('disabled') ) return;
		var stop = document.getElementById('stop_log_button');

		Modal.prompt(Translator.get('troubleshooting.log.level'),
			Node.select({name: 'level'}, [
				Node.option({value: '1000'}, 'SEVERE (1000)'),
				Node.option({value: '900'}, 'WARNING (900)'),
				Node.option({value: '800'}, 'INFO (800)'),
				Node.option({value: '700'}, 'CONFIG (700)'),
				Node.option({value: '500'}, 'FINE (500)'),
				Node.option({value: '400'}, 'FINER (400)'),
				Node.option({value: '300'}, 'FINEST (300)'),
				Node.option({value: '0'}, 'ALL (0)')
			])
		).then(form =>
		{
			if( !form.value ) return;

			self.ws_log = new WebSocket((location.protocol === 'https:' ? 'wss' : 'ws') + "://" + location.host +
				"/api/ws/logs?level=" + encodeURIComponent(form.value),
				[Ajax.authorization.replace(/^Bearer /i, '')]);

			self.ws_log.addEventListener('open', () =>
			{
				start.classList.add('disabled');
				stop.classList.remove('disabled');
				Notify.success(Translator.get('troubleshoot.log.connected'));
			});

			self.ws_log.addEventListener('close', () =>
			{
				self.ws_log = null;

				start.classList.remove('disabled');
				stop.classList.add('disabled');
				Notify.warning(Translator.get('troubleshoot.log.disconnected'));
			});

			self.ws_log.addEventListener('message', (m) => { self.dataLog(JSON.parse(m.data)); });
			self.ws_log.addEventListener('error', (e) => { self.ws_log.close(); });
		}, () => {});
	}

	startDebug()
	{
		var self = this;
		var start = document.getElementById('start_debug_button');
		if( start.classList.contains('disabled') ) return;
		var stop = document.getElementById('stop_debug_button');

		Modal.prompt(Translator.get('troubleshoot.debug.filter'),
			Node.input({type: 'text', value: '#'})
		).then(form =>
		{
			if( !form.value ) return;

			self.ws_debug = new WebSocket((location.protocol === 'https:' ? 'wss' : 'ws') + "://" + location.host +
				"/api/ws/debug?filter=" + encodeURIComponent(form.value),
				[Ajax.authorization.replace(/^Bearer /i, '')]);

			self.ws_debug.addEventListener('open', () =>
			{
				start.classList.add('disabled');
				stop.classList.remove('disabled');
				Notify.success(Translator.get('troubleshoot.debug.connected'));
			});

			self.ws_debug.addEventListener('close', () =>
			{
				self.ws_debug = null;

				start.classList.remove('disabled');
				stop.classList.add('disabled');
				Notify.warning(Translator.get('troubleshoot.debug.disconnected'));
			});

			self.ws_debug.addEventListener('message', (m) => { self.dataDebug(m.data); });
			self.ws_debug.addEventListener('error', (e) => { self.ws_debug.close(); });
		}, () => {});
	}

	stopLog()
	{
		if( !this.ws_log ) return;
		this.ws_log.close();
	}

	stopDebug()
	{
		if( !this.ws_debug ) return;
		this.ws_debug.close();
	}

	dataLog(data)
	{
		if( !data || !data.level || !data.message ) return;

		var ol = document.getElementById('log_entries');
		if( !ol ) return;

		while( ol.children.length >= 50 ) ol.lastChild.remove();

		var level = 'severe';
		if( data.level < 700 ) level = 'fine';
		else if( data.level < 800 ) level = 'config';
		else if( data.level < 900 ) level = 'info';
		else if( data.level < 1000 ) level = 'warning';

		ol.insertBefore(Node.li([
			Node.span({className: 'date'}, new Date(data.date).toLocaleString([], {hour: '2-digit', minute: '2-digit', second: '2-digit', fractionalSecondDigits: 3})),
			Node.span({className: 'level ' + level}, safeHtml('(' + data.level + ')')),
			Node.span({className: 'message'}, safeHtml(data.message))
		]), ol.firstChild);
	}

	dataDebug(data)
	{
		if( !data ) return;

		var ol = document.getElementById('debug_entries');
		if( !ol ) return;

		while( ol.children.length >= 20 ) ol.lastChild.remove();

		var code = Node.pre(Node.code({className: 'language-json'}, JSON.stringify(JSON.parse(data), null, '\t')));
		ol.insertBefore(Node.li([
			code
		]), ol.firstChild);

		Prism.highlightElement(code.firstChild);
	}

	clearLogs()
	{
		var self = this;

		Modal.prompt(Translator.get('troubleshoot.delete.confirm'),
			Node.input({type: 'date'})).then(form =>
		{
			if( !form.value ) return;

			self.dom.classList.add('wait');
			Ajax.delete('/api/manager/logs', {data: {until: form.valueAsDate.getTime()}}).then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('troubleshoot.delete.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('troubleshoot.delete.error'));
			});
		}, () => {});
	}

	downloadLogs()
	{
		var self = this;

		Modal.prompt(Translator.get('troubleshoot.download.confirm'),
			Node.form(
			[
				Node.input({type: 'date', name: 'from'}),
				Node.input({type: 'date', name: 'to'})
			])).then(form =>
		{
			if( !form.from.value || !form.to.value ) return;

			self.dom.classList.add('wait');
			Ajax.get('/api/manager/logs/download', {data: {from: form.from.valueAsDate.getTime(), to: form.to.valueAsDate.getTime()}, responseType: 'blob'}).then((response) =>
			{
				self.dom.classList.remove('wait');
				var name = response.headers["content-disposition"] || undefined;
				if( name ) name = name.match(/filename=([^;\n^]*)/i)[1].replaceAll('"','');
				Node.a({href: URL.createObjectURL(response.response), download: name||'logs.zip', target: '_blank'}).click();
				Notify.success(Translator.get('troubleshoot.download.success'));
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('troubleshoot.download.error'));
			});
		}, () => {});
	}
}

const page = new TroubleshootPage();
export { page as default };
