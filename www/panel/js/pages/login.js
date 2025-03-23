
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Notify', 'Modal', 'Ajax', 'Translator', 'Cookie', 'page.login.css').then(([Page, Node, Notify, Modal, Ajax, Translator, Cookie]) =>
	{
		Translator.load('default').then(() =>
		{
			/* ======================
			 * Rules for login:
			 *
			 * 0) check if there is a token cookie
			 *     0.1) if no, go to 1
			 *     1.2) if yes it means we are coming back from authentication
			 *         1.2.1) remove the cookie and set local storage
			 *         1.2.2) go to 3
			 *
			 * 1) check if there is a token local storage
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
			
			ok(Object.assign(new Page(), 
			{
				show: function()
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
				},
				
				checkAccess: function(token)
				{
					// todo : check if the token is valid for this app
					// if no, then display a notification and return false
					// if yes, just return true
					return true;
				},
				
				rule_0: function()
				{
					var cookie = Cookie.get('token');
					Cookie.unset('token', '/panel');
					if( cookie )
					{
						localStorage.setItem('utoken', cookie);
						this.rule_3(cookie);
					}
					else
						this.rule_1();
				},
				
				rule_1: function()
				{
					var token = localStorage.getItem('utoken');
					if( token )
						this.rule_3(token);
					else
						this.rule_2_display();
				},
				
				rule_2_display: function(username)
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
								Node.p(Translator.get("login.welcome", ae.safeHtml(username))),
								Node.p({className: 'warning'}, Translator.get("login.no_access"))
							);
						}
						else
							div.append(Translator.get("login.required"));
						
						div.append(
							Node.button({className: 'raised', click: function(e)
							{
								e.stopImmediatePropagation();
								e.preventDefault();
								location.href = response.response.login_redirect;
							}}, Translator.get('login.login'))
						);
						div.classList.remove('wait');
					}, (error) =>
					{
						div.append(Node.p({className: 'error'}, Translator.get('login.error.fetch')));
						div.classList.remove('wait');
					});
				},
				
				rule_3: function(token)
				{
					var self = this;
					var div = document.getElementById('login_panel');
					div.classList.add('wait');
					while(div.firstChild) div.firstChild.remove();
					
					Ajax.authorization = 'Bearer ' + token;
					Ajax.get("/api/security/me").then((result) =>
					{
						if( !!result.response.anonymous ) { self.rule_2_display(); return; }
						
						ae.username = result.response.name;
						ae.userlevel = 
							result.response.roles.find(r => r.id == '22200000-2200000000000000') ? 'contributor' : 
							result.response.roles.find(r => r.id == '22200000-2100000000000000') ? 'manager' : 
							'other';
										
						Ajax.post("/api/security/check", {data: {scope: 'http', context: JSON.stringify({path: '/api/contributor/check'})}}).then((result) =>
						{
							if( !!result.response.granted )
							{
								self.dom.classList.remove('wait');
								self.dom.remove();
								self.grantor.ok();
							}
							else
								self.rule_2_display(ae.username);
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
			}));
		});
	}, (e) => { nok(e); });
});

export { x as default };