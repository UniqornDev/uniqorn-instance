import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml } from 'core';
css('me');

class AccountPage extends Page
{
	async show()
	{
		this.dom.classList.add('me');
		document.body.querySelectorAll('nav li').forEach(e => { e.classList.remove('selected'); });

		this.init();
	}

	async hide()
	{
		while(this.dom.firstChild) this.dom.firstChild.remove();
	}

	init()
	{
		var self = this;
		while(this.dom.firstChild) this.dom.firstChild.remove();

		this.dom.append(
			Node.div({className: 'action'},
			[
				Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.logout(); }}, [
					Node.span({className: 'icon'}, 'logout'),
					Node.span(Translator.get('me.logout'))])
			]),
			Node.section({id: "user_info", classList: 'wait'}),
			Node.section({id: "token_info", classList: 'wait'})
		);

		this.initInfo();
		this.initToken();
	}

	initInfo()
	{
		var self = this;
		var section = this.dom.querySelector('#user_info');
		while(section.firstChild) section.firstChild.remove();
		section.classList.add('wait');

		Ajax.get("/api/security/me").then((result) =>
		{
			var user = result.response;
			section.append(
				Node.h2(Translator.get('me.info')),
				Node.div(Node.div({className: 'detail'}, [
					Node.p([
						Node.span({className: 'title'}, Translator.get('me.info.login')),
						Node.span({className: 'value'}, safeHtml(user.login))
					]),
					Node.p([
						Node.span({className: 'title'}, Translator.get('me.info.name')),
						Node.span({className: 'text'}, safeHtml(user.name))
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
				])),
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
			section.classList.remove('wait');
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
		});
	}

	initToken()
	{
		var self = this;
		var section = this.dom.querySelector('#token_info');
		while(section.firstChild) section.firstChild.remove();
		section.classList.add('wait');

		Ajax.get("/api/security/me/token").then((result) =>
		{
			var tokens = result.response;

			// filter tokens
			tokens.forEach(t =>
			{
				var target = ["signin", "topic", "http"];
				for( var s of target )
				{
					var i = t.scopes.indexOf(s);
					if( i > -1 ) t.scopes.splice(i, 1);
				}
			});
			tokens = tokens.filter(t => t.scopes.length > 0);

			section.append(
				Node.h2(Translator.get('me.tokens')),
				Node.p(Translator.get('me.tokens.explain')),
				Node.div(Node.div({className: 'detail'}, tokens.map(t =>
					Node.p([
						Node.span({className: 'title'}, safeHtml(t.value.substring(0, 10) + '...')),
						Node.span({className: 'value', dataset: {value: t.value}}, [
							t.scopes.map(s => Node.span({className: 'tag'}, safeHtml(s))),
							Node.span({className: 'icon', dataset: {tooltip: Translator.get('me.token.copy'), value: t.value}, click: function()
							{
								navigator.clipboard.writeText(this.parentNode.dataset.value);
								Notify.success(Translator.get('me.token.copied'));
							}}, 'content_copy'),
							Node.span({className: 'icon', dataset: {tooltip: Translator.get('me.token.rotate')}, click: function()
							{
								self.rotateToken(this.parentNode.dataset.value);
							}}, 'autorenew'),
							Node.span({className: 'icon', dataset: {tooltip: Translator.get('me.token.remove')}, click: function()
							{
								self.removeToken(this.parentNode.dataset.value);
							}}, 'clear'),
						]),
						Node.span({className: 'validity'}, 
							t.validity <= 0 ? Translator.get('me.token.permanent') :
							Translator.get('me.token.validuntil', new Date(t.epoch + t.validity).toLocaleString([], {dateStyle: 'medium', timeStyle: 'medium'})))
					]))
				)),
				Node.div({className: 'action'},
				[
					Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.createToken(); }}, [
						Node.span({className: 'icon'}, 'security'),
						Node.span(Translator.get('me.token.create'))])
				])
			);
			section.classList.remove('wait');
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
		});
	}

	logout()
	{
		Ajax.post("/api/security/logout").then((result) =>
		{
			localStorage.removeItem('utoken');
			location.reload(true);
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
		});
	}

	reset_mfa()
	{
		var self = this;
		var otpInput = Node.input({type: 'text', name: 'otp', placeholder: Translator.get('me.mfa.otp'), autocomplete: 'one-time-code'});
		Modal.confirm([
			Node.h2(Translator.get('me.mfa.title')),
			Node.p(Translator.get('me.mfa.confirm')),
			otpInput
		], [Translator.get('reset'), Translator.get('cancel')]).then(index =>
		{
			if( index > 0 ) return;
			Ajax.delete('/api/contributor/self/otp', {data: {otp: otpInput.value}}).then(() =>
			{
				Notify.success(Translator.get('me.mfa.success'));
			}, (error) =>
			{
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('me.mfa.error'));
			});
		}, () => {});
	}

	reset_password()
	{
		var self = this;
		var m = Modal.custom([
			Node.h2(Translator.get('me.password.title')),
			Node.p(Translator.get('me.password.confirm')),
			Node.form(
			[
				Node.input({name: 'current', type: 'password', placeholder: Translator.get('me.password.current')}),
				Node.input({name: 'first', type: 'password', placeholder: Translator.get('me.password.first')}),
				Node.input({name: 'second', type: 'password', placeholder: Translator.get('me.password.second')})
			]),
			Node.div({className: 'action'},
			[
				Node.button({click: function(e)
				{
					e.preventDefault();
					let form = m.dom.querySelector('form');

					if( !form.elements['current'].value )
				{
					Notify.warning(Translator.get('me.password.current.empty'));
					return;
				}

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
					Ajax.patch('/api/contributor/self', {data: {current_password: form.elements['current'].value, password: form.elements['first'].value}}).then(() =>
					{
						m.ok();
						Notify.success(Translator.get('me.password.success'));
						localStorage.removeItem('utoken');
						location.reload(true);
					}, (error) =>
					{
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('me.password.error'));
						m.dom.classList.remove('wait');
					});
				}}, Translator.get('save')),
				Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
			])
		], true);
	}

	rotateToken(value)
	{
		var self = this;
		var section = this.dom.querySelector('#token_info');
		section.classList.add('wait');

		Ajax.patch('/api/security/me/token', {data: {token: value}}).then(() =>
		{
			Notify.success(Translator.get('me.token.rotate.success'));
			self.initToken();
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('me.token.rotate.error'));
			section.classList.remove('wait');
		});
	}

	removeToken(value)
	{
		var self = this;
		var section = this.dom.querySelector('#token_info');
		section.classList.add('wait');

		Ajax.delete('/api/security/me/token', {data: {token: value}}).then(() =>
		{
			Notify.success(Translator.get('me.token.delete.success'));
			self.initToken();
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('me.token.delete.error'));
			section.classList.remove('wait');
		});
	}

	createToken()
	{
		var self = this;
		var section = this.dom.querySelector('#token_info');

		Modal.prompt(
			Node.h2(Translator.get('me.token.create')),
			Node.form({className: 'labeled'}, [
				Node.label(Translator.get('me.token.scope')),
				Node.select({name: 'scope'}, [
					Node.option({value: 'git'}, "git"),
					Node.option({value: 'api'}, "api"),
					Node.option({value: 'mcp'}, "mcp"),
				]),
				Node.label(Translator.get('me.token.validity')),
				Node.input({type: 'number', name: 'validity', min: 0, max: 8760, step: 24, value: 1})
			])
		).then(form =>
		{
			section.classList.add('wait');
			Ajax.post('/api/security/me/token', {data: {validity: parseInt(form.validity.value)*3600000, scopes: JSON.stringify(['topic', 'http', form.scope.value])}}).then(() =>
			{
				Notify.success(Translator.get('me.token.create.success'));
				self.initToken();
			}, (error) =>
			{
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('me.token.create.error'));
				section.classList.remove('wait');
			});
		}, () => {});
	}
}

const page = new AccountPage();
export { page as default };
