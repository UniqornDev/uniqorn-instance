import { Page, Node, Notify, Modal, Ajax, Translator, Cookie } from 'core';
import { css, safeHtml, config, locale } from 'core';
css('login');
await locale('default');

const URL_TO_CHECK = "/api/contributor/check";
/* ======================
 * Rules for login:
 *
 * 0) check if there is a token cookie
 *     0.1) if no, go to 1
 *     1.2) if yes it means we are coming back from authentication
 *         1.2.1) remove the cookie and store token based on "remember me" preference
 *         1.2.2) go to 3
 *
 * 1) check if there is a token in localStorage or sessionStorage
 *     1.1) if yes, go to 3
 *     1.2) else go to 2
 *
 * 2) get local provider and display login form
 *     2.1) when click on button, redirect user to login url. Upon completion it will end up back here, go to 0
 *
 * 3) check if the token is valid
 *     3.1) if not, remove it from local storage and go to 2
 *     3.2) if yes, check if access is granted
 *         3.2.1) if no, go to 2
 *         3.2.2) if yes redirect the user to the home screen
 *
 * ======================
 */

class LoginPage extends Page
{
	async show()
	{
		var _ok, _nok;
		this.grantor = new Promise((ok, nok) =>
		{
			_ok = ok;
			_nok = nok;
		});
		this.grantor.ok = _ok;
		this.grantor.nok = _nok;
		
		this.dom.classList.add('login');
		this.dom.appendChild(Node.div({className: 'wait', id: 'login_panel'}));
		document.body.appendChild(this.dom);
		
		this.rule_0();
		return this.grantor;
	}
	
	rule_0()
	{
		var cookie = Cookie.get('token');
		Cookie.unset('token', '/panel');
		if( cookie )
		{
			var storage = sessionStorage.getItem('remember') === 'true' ? localStorage : sessionStorage;
			storage.setItem('utoken', cookie);
			this.rule_3(cookie);
		}
		else
			this.rule_1();
	}
	
	rule_1()
	{
		var token = localStorage.getItem('utoken') || sessionStorage.getItem('utoken');
		if( token )
			this.rule_3(token);
		else
			this.rule_2_display();
	}
	
	rule_2_display(username)
	{
		var self = this;
		var div = document.getElementById('login_panel');
		div.classList.add('wait');
		while(div.firstChild) div.firstChild.remove();
		
		Ajax.get("/oidc/local").then((response) =>
		{
			if( !!username )
			{
				div.append(
					Node.p(Translator.get("login.welcome", safeHtml(username))),
					Node.p({className: 'warning'}, Translator.get("login.no_access"))
				);
			}
			else
				div.append(Translator.get("login.required"));
						
			div.append(
				Node.label({className: 'remember'}, [
					Node.input({type: 'checkbox', id: 'remember_me', checked: false}),
					Translator.get('login.remember')
				]),
				Node.button({className: 'raised', click: function(e)
				{
					e.stopImmediatePropagation();
					e.preventDefault();
					sessionStorage.setItem('remember', document.getElementById('remember_me').checked ? 'true' : 'false');
					location.href = response.response.login_redirect;
				}}, Translator.get('login.login'))
			);
			div.classList.remove('wait');
		}, (error) =>
		{
			div.append(Node.p({className: 'error'}, Translator.get('login.error.fetch')));
			div.classList.remove('wait');
		});
	}
	
	rule_3(token)
	{
		var self = this;
		var div = document.getElementById('login_panel');
		div.classList.add('wait');
		while(div.firstChild) div.firstChild.remove();
		
		Ajax.authorization = 'Bearer ' + token;
		Ajax.get("/api/security/me").then((result) =>
		{
			if( !!result.response.anonymous ) { self.rule_2_display(); return; }
			
			config.user = result.response;
			config.user.level =
				result.response.roles.find(r => r.id == '22200000-2200000000000000') ? 'contributor' :
				result.response.roles.find(r => r.id == '22200000-2100000000000000') ? 'manager' :
				'other';

			Ajax.post("/api/security/check", {data: {scope: 'http', context: JSON.stringify({path: URL_TO_CHECK})}}).then((result) =>
			{
				if( !!result.response.granted )
				{
					self.dom.classList.remove('wait');
					self.dom.remove();
					self.grantor.ok();
				}
				else
					self.rule_2_display(config.user.name);
			}, (error) =>
			{
				div.append(Node.p({className: 'error'}, Translator.get('login.error.fetch')));
				div.classList.remove('wait');
			});
		}, (error) =>
		{
			div.append(Node.p({className: 'error'}, Translator.get('login.error.fetch')));
			div.classList.remove('wait');
		});
	}
}

const page = new LoginPage();
export { page as default };
