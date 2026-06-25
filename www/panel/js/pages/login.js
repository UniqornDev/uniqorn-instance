import { Page, Node, Notify, Modal, Ajax, Translator } from 'core';
import { css, safeHtml, config, locale, urlValue } from 'core';
css('login');
await locale('default');

const URL_TO_CHECK = "/api/contributor/check";

// the app is registered as a public client of the RP; redirect_uri is looked up server-side from this client_id
const CLIENT_ID = "f8f9c518ce8bf1eda6a38c4ee4ebf123528b9fd7a1a1ca6243b602e40e4b90af";

function base64url(buffer)
{
	const bytes = new Uint8Array(buffer);
	let str = '';
	for( let i = 0; i < bytes.length; i++ ) str += String.fromCharCode(bytes[i]);
	return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function randomToken()
{
	const bytes = new Uint8Array(32);
	crypto.getRandomValues(bytes);
	return base64url(bytes.buffer);
}

async function challengeOf(verifier)
{
	const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
	return base64url(digest);
}

/* ======================
 * Rules for login:
 *
 * 0) check the return signals on /panel
 *     0.1) if an error is present, show the error
 *     0.2) if a PKCE code is present, verify state, exchange it at /oidc/token for a token, store it, go to 3
 *     0.3) else go to 1
 *
 * 1) check if there is a token in localStorage or sessionStorage
 *     1.1) if yes, go to 3
 *     1.2) else go to 2
 *
 * 2) get local provider and display login form
 *     2.1) when click on button, generate a PKCE verifier/challenge and redirect to the login url. Upon completion it will end up back here, go to 0
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
		// returning from the RP: an error or a one-time PKCE code lands here on /panel
		if( urlValue('error') )
		{
			this.cleanUrl();
			this.rule_error();
			return;
		}

		var code = urlValue('code');
		if( code )
		{
			this.rule_pkce(code);
			return;
		}

		this.rule_1();
	}

	cleanUrl()
	{
		try { history.replaceState(null, document.title, location.pathname + location.hash); } catch(e) { /* ignore */ }
	}

	rule_pkce(code)
	{
		var self = this;
		var div = document.getElementById('login_panel');
		div.classList.add('wait');
		while(div.firstChild) div.firstChild.remove();

		var verifier = sessionStorage.getItem('pkce_verifier');
		var expected = sessionStorage.getItem('pkce_state');
		var returned = urlValue('state');
		sessionStorage.removeItem('pkce_verifier');
		sessionStorage.removeItem('pkce_state');
		this.cleanUrl();

		if( !verifier || !returned || returned !== expected )
		{
			this.rule_error();
			return;
		}

		Ajax.post("/oidc/token", {data: {client_id: CLIENT_ID, code: code, code_verifier: verifier}}).then((result) =>
		{
			var token = result.response.access_token;
			var storage = sessionStorage.getItem('remember') === 'true' ? localStorage : sessionStorage;
			storage.setItem('utoken', token);
			self.rule_3(token);
		}, (error) =>
		{
			self.rule_error();
		});
	}

	rule_error()
	{
		var self = this;
		var div = document.getElementById('login_panel');
		div.classList.remove('wait');
		while(div.firstChild) div.firstChild.remove();

		div.append(
			Node.p({className: 'error'}, Translator.get('login.error.auth')),
			Node.button({className: 'raised', click: function(e)
			{
				e.stopImmediatePropagation();
				e.preventDefault();
				self.rule_2_display();
			}}, Translator.get('login.login'))
		);
	}

	async startLogin(loginRedirect, remember)
	{
		sessionStorage.setItem('remember', remember ? 'true' : 'false');

		var verifier = randomToken();
		var state = randomToken();
		sessionStorage.setItem('pkce_verifier', verifier);
		sessionStorage.setItem('pkce_state', state);

		var challenge = await challengeOf(verifier);
		var sep = loginRedirect.indexOf('?') < 0 ? '?' : '&';
		location.href = loginRedirect + sep
			+ 'client_id=' + encodeURIComponent(CLIENT_ID)
			+ '&code_challenge=' + encodeURIComponent(challenge)
			+ '&code_challenge_method=S256'
			+ '&state=' + encodeURIComponent(state);
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
					self.startLogin(response.response.login_redirect, document.getElementById('remember_me').checked);
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
