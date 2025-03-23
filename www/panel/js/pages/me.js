
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.me.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('me');
				document.body.querySelectorAll('nav li').forEach(e => { e.classList.remove('selected'); });
				
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
				while(this.dom.firstChild) this.dom.firstChild.remove();
				
				this.dom.classList.add('wait');
				
				Ajax.get("/api/security/me").then((result) =>
				{
					var user = result.response;
					
					self.dom.append(
						Node.div({className: 'action'},
						[
							Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.logout(); }}, [
								Node.span({className: 'icon'}, 'logout'), 
								Node.span(Translator.get('me.logout'))])
						]),
						Node.section({className: 'open'},
						[
							Node.h2(Translator.get('me.info')),
							Node.div(Node.div({className: 'detail'}, [
								Node.p([
									Node.span({className: 'title'}, Translator.get('me.info.login')),
									Node.span({className: 'value'}, ae.safeHtml(user.login))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('me.info.name')),
									Node.span({className: 'text'}, ae.safeHtml(user.name))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('me.info.mfa')),
									Node.span({className: 'text'}, Translator.get(!!user.mfa ? 'yes' : 'no'))
								]),
								Node.p([
									Node.span({className: 'title'}, Translator.get('me.info.level')),
									Node.span({className: 'value'}, 
										user.roles.find(r => r.id == '22200000-2200000000000000') ? Translator.get('me.level.contributor') : 
										user.roles.find(r => r.id == '22200000-2100000000000000') ? Translator.get('me.level.manager') : 
										Translator.get('me.level.other'))
								])
							]))
						]),
						Node.div({className: 'action'},
						[
							Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.reset_mfa(); }}, [
								Node.span({className: 'icon'}, 'fingerprint'), 
								Node.span(Translator.get('me.reset_mfa'))]),
							Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.reset_password(); }}, [
								Node.span({className: 'icon'}, 'key'), 
								Node.span(Translator.get('me.reset_password'))])
						])
					);
					self.dom.classList.remove('wait');
					
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
				});
			},
			
			logout: function()
			{
				Ajax.post("/api/security/logout").then((result) =>
				{
					localStorage.removeItem('utoken');
					location.reload(true);
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
				});
			},
			
			reset_mfa: function()
			{
				var self = this;
				Modal.confirm([
					Node.h2(Translator.get('me.mfa.title')),
					Node.p(Translator.get('me.mfa.confirm'))
				], [Translator.get('reset'), Translator.get('cancel')]).then(index =>
				{
					if( index > 0 ) return;
					Ajax.delete('/api/contributor/self/otp').then(() =>
					{
						Notify.success(Translator.get('me.mfa.success'));
					}, () =>
					{
						Notify.error(Translator.get('me.mfa.error'));
					});
				}, () => {});
			},
			
			reset_password: function()
			{
				var self = this;
				var m = Modal.custom([
					Node.h2(Translator.get('me.password.title')),
					Node.p(Translator.get('me.password.confirm')),
					Node.form(
					[
						Node.input({name: 'first', type: 'password', placeholder: Translator.get('me.password.first')}),
						Node.input({name: 'second', type: 'password', placeholder: Translator.get('me.password.second')})
					]),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							let form = m.dom.querySelector('form');
							
							if( !form.elements['first'].value )
							{
								Notify.warning(Translator.get('me.password.empty'));
								return;
							}
							
							if( form.elements['first'].value.length < 10 )
							{
								Notify.warning(Translator.get('me.password.short'));
								return;
							}
							
							if( form.elements['first'].value != form.elements['second'].value )
							{
								Notify.warning(Translator.get('me.password.mismatch'));
								return;
							}
							
							m.dom.classList.add('wait');
							Ajax.patch('/api/contributor/self', {data: {password: form.elements['first'].value}}).then(() =>
							{
								m.ok();
								Notify.success(Translator.get('me.password.success'));
								localStorage.removeItem('utoken');
								location.reload(true);
							}, () =>
							{
								Notify.error(Translator.get('me.password.error'));
								m.dom.classList.remove('wait');
							});
						}}, Translator.get('save')),
						Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
					])
				]);
			}
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };