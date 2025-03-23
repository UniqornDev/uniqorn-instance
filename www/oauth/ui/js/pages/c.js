
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Translator', 'Ajax', 'page.c.css').then(([Page, Node, Translator, Ajax]) =>
	{
		Translator.load('default').then(() =>
		{
			/* ======================
			 * Rules for consent:
			 *
			 * 0) check if the code is correct
			 *     0.1) if no, redirect to error page
			 *     0.2) if yes, go to 1
			 *
			 * 1) ask for user consent and send response to /oauth/consent
			 */
			 
			ok(Object.assign(new Page(), 
			{
				show: function()
				{
					this.code = new URLSearchParams(window.location.search).get('code');
					
					this.dom.classList.add('consent');
					this.dom.appendChild(Node.div({className: 'wait', id: 'consent_panel'}));
					var self = this; setTimeout(function() { self.rule_0(); }, 1);
					return Promise.resolve();
				},
				
				updateCodeValidity: function()
				{
					var div = this.dom;
					
					var left = Math.floor((this.code_ttl - (Date.now() - this.code_epoch))/1000);
					var min = Math.floor(left / 60);
					var sec = left % 60;
					
					if( left < 0 )
					{
						var searchParams = new URLSearchParams();
						searchParams.set("error", 'expired');
						searchParams.set("error_description", Translator.get('code.validity.expired'));
						location.href = 'error?' + searchParams.toString() + '#e';
					}
					
					div.dataset.ttl = Translator.get('code.validity', (min > 0 ? min + 'min ' : '') + (sec > 0 ? sec + 's' : ''));
					div.classList.toggle('__force_update');
				},
				
				rule_0: function()
				{
					var self = this;
					var div = document.getElementById('consent_panel');
					while(div.firstChild) div.firstChild.remove();
					
					Ajax.get("/oauth/codeinfo", {data: {code: this.code, verbose: "true"}}).then((response) =>
					{
						if( !response.response.signin_token )
						{
							var searchParams = new URLSearchParams();
							searchParams.set("error", 'access_denied');
							searchParams.set("error_description", 'Invalid sign in token.');
							location.href = 'error?' + searchParams.toString() + '#e';
							return;
						}
						
						var tokens = JSON.parse(localStorage.getItem('tokens') || "[]");
						if( !tokens.includes(response.response.signin_token) )
						{
							tokens.push(response.response.signin_token);
							localStorage.setItem('tokens', JSON.stringify(tokens));
						}
						
						self.code_epoch = response.response.epoch;
						self.code_ttl = response.response.ttl;
						self.updateCodeValidity();
						setInterval(() => { self.updateCodeValidity(); }, 1000);
						
						self.rule_1(response.response.name);
					}, (error) =>
					{
						var searchParams = new URLSearchParams();
						searchParams.set("error", error.response.error||'server_error');
						searchParams.set("error_description", error.response.error_description||'Unspecified error.');
						location.href = 'error?' + searchParams.toString() + '#e';
					});
				},
				
				rule_1: function(name)
				{
					var self = this;
					var div = document.getElementById('consent_panel');
					while(div.firstChild) div.firstChild.remove();
					
					div.append(
						Node.p(Translator.get("consent.title")),
						Node.p({className: 'explain'}, Translator.get("consent.provider", ae.safeHtml(name))),
						Node.button({className: 'raised agree', click: function(e)
						{
							e.preventDefault();
							e.stopImmediatePropagation();
							div.classList.add('wait');
							div.append(Node.form({id: 'form_form', action: '/oauth/consent', method: 'POST', enctype: 'application/x-www-form-urlencoded', style: {display: 'none'}}, [
								Node.input({type: 'hidden', value: self.code, name: 'code'}),
								Node.input({type: 'hidden', value: 'true', name: 'granted'})
							]));
							div.lastChild.submit();
						}}, Translator.get('consent.agree')),
						Node.button({className: 'raised disagree', click: function(e)
						{
							e.preventDefault();
							e.stopImmediatePropagation();
							div.classList.add('wait');
							div.append(Node.form({id: 'form_form', action: '/oauth/consent', method: 'POST', enctype: 'application/x-www-form-urlencoded', style: {display: 'none'}}, [
								Node.input({type: 'hidden', value: self.code, name: 'code'}),
								Node.input({type: 'hidden', value: 'false', name: 'granted'})
							]));
							div.lastChild.submit();
						}}, Translator.get('consent.disagree'))
					);
					
					div.classList.remove('wait');
				}
			}));
		});
	}, (e) => { nok(e); });
});

export { x as default };