
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.troubleshoot.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('troubleshoot');
				document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'troubleshoot'));
				
				this.init();
				return Promise.resolve();
			},
			
			hide: function()
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
				return Promise.resolve(); 
			},
			
			init: function()
			{
				var self = this;
				
				this.dom.append(
					ae.userlevel == 'manager' ? Node.div({className: 'action'},
					[
						Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.reboot(); }}, [
							Node.span({className: 'icon'}, 'power_settings_new'), 
							Node.span(Translator.get('troubleshoot.reboot'))])
					]) : null,
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
				);
			},
			
			codeLog: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.log')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
						+ '\n\t.process((data, user) -&gt; {\n\t\ttry {\n\t\t\tApi.log(500, "Start transaction for {}", user.name());\n\t\t\t...\n\t\t} catch(Exception e) {' 
						+ '\n\t\t\tApi.log(900, e);\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
			
			codeDebug: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.debug')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")'
						+ '\n\t.process(data -&gt; {\n\t\tApi.debug("checkpoint", data);\n\t\t...\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
			
			startLog: function()
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
					
					self.ws_log = new WebSocket(location.protocol.replace(/^http/i, "ws") + "//" + location.host + 
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
			},
			
			startDebug: function()
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
					
					self.ws_debug = new WebSocket(location.protocol.replace(/^http/i, "ws") + "//" + location.host + 
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
			},
			
			stopLog: function()
			{
				if( !this.ws_log ) return;
				this.ws_log.close();
			},
			
			stopDebug: function()
			{
				if( !this.ws_debug ) return;
				this.ws_debug.close();
			},
			
			dataLog: function(data)
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
					Node.span({className: 'level ' + level}, ae.safeHtml('(' + data.level + ')')),
					Node.span({className: 'message'}, ae.safeHtml(data.message))
				]), ol.firstChild);
			},
			
			dataDebug: function(data)
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
			},
			
			reboot: function()
			{
				var self = this;
				var m = Modal.custom([
					Node.p(Translator.get('troubleshoot.reboot.confirm')), 
					Node.input({name: 'mfa', type: 'text', placeholder: Translator.get('troubleshoot.mfa')}),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							let mfa = m.dom.querySelector('input').value;
							if( !mfa )
							{
								m.ok();
								Notify.error(Translator.get('troubleshoot.reboot.abort'));
								return;
							}
							
							document.body.classList.add('wait');
							Ajax.get('/api/manager/reboot', {data: {mfa: mfa}}).then(() =>
							{
								var i = setInterval(() =>
								{
									Ajax.get('/api/ping').then((result) =>
									{
										location.reload(true);
									}, (error) => {});
								}, 2000);
							}, () =>
							{
								document.body.classList.remove('wait');
								Notify.error(Translator.get('troubleshoot.reboot.error'));
							});
						}}, Translator.get('troubleshoot.reboot')),
						Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
					])
				], true);
			}
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };