
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Translator', 'Ajax', 'Modal', 'Notify', 'qrcode', 'page.m.css', 'ae.notify.css').then(([Page, Node, Translator, Ajax, Modal, Notify, QRCode]) =>
	{
		Translator.load('default').then(() =>
		{
			/* ======================
			 * Rules for mfa:
			 *
			 * 0) check if the code is correct
			 *     0.1) if no, redirect to error page
			 *     0.2) if yes, go to 1
			 *
			 * 1) check if user is registered with mfa
			 *     1.1) if no, register now then go to 2
			 *     1.2) if yes, go to 2
			 *
			 * 2) prompt for mfa and submit to /oauth/otp
			 */
			 
			ok(Object.assign(new Page(), 
			{
				show: function()
				{
					this.code = new URLSearchParams(window.location.search).get('code');
					
					this.dom.classList.add('mfa');
					this.dom.appendChild(Node.div({className: 'wait', id: 'mfa_panel'}));
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
					var div = document.getElementById('mfa_panel');
					while(div.firstChild) div.firstChild.remove();
					
					Ajax.get("/oauth/codeinfo", {data: {code: this.code}}).then((response) =>
					{
						self.code_epoch = response.response.epoch;
						self.code_ttl = response.response.ttl;
						self.updateCodeValidity();
						setInterval(() => { self.updateCodeValidity(); }, 1000);
						
						self.rule_1();
					}, (error) =>
					{
						var searchParams = new URLSearchParams();
						searchParams.set("error", error.response.error||'server_error');
						searchParams.set("error_description", error.response.error_description||'Unspecified error.');
						location.href = 'error?' + searchParams.toString() + '#e';
					});
				},
				
				rule_1: function()
				{
					var self = this;
					var div = document.getElementById('mfa_panel');
					while(div.firstChild) div.firstChild.remove();
					
					Ajax.get("/oauth/otp/exists", {data: {code: this.code}}).then((response) =>
					{
						if( !response.response.hasOwnProperty("exists") )
						{
							var searchParams = new URLSearchParams();
							searchParams.set("error", error.response.error||'server_error');
							searchParams.set("error_description", error.response.error_description||'Unexpected server response.');
							location.href = 'error?' + searchParams.toString() + '#e';
						}
						else if( !!response.response.exists )
							self.rule_2();
						else
							self.rule_1_display();
						
					}, (error) =>
					{
						var searchParams = new URLSearchParams();
						searchParams.set("error", error.response.error||'server_error');
						searchParams.set("error_description", error.response.error_description||'Unspecified error.');
						location.href = 'error?' + searchParams.toString() + '#e';
					});
				},
				
				rule_1_display: function()
				{
					var self = this;
					var div = document.getElementById('mfa_panel');
					while(div.firstChild) div.firstChild.remove();
					
					div.append(
						Node.p(Translator.get("mfa.register")),
						Node.p({className: 'explain'}, Translator.get("mfa.register.detail")),
						Node.button({className: 'raised', click: function(e)
						{
							e.stopImmediatePropagation();
							e.preventDefault();
							div.classList.add('wait');
							
							Ajax.post("/oauth/otp/generate", {data: {code: self.code}}).then((response) =>
							{
								var input = Node.input({type: 'text', placeholder: Translator.get('mfa.otp')});
								Modal.alert([
									Translator.get('mfa.qrcode'),
									Node.p({className: "qr"}, QRCode({msg: response.response.url, pad: 6, dim: 256, pal: ['#000', '#fff'], ecb: 0, ecl: 'M'})),
									input
								]).then(() =>
								{
									Ajax.post("/oauth/otp/register", {data: {code: self.code, 
										check: input.value, 
										period: response.response.period, 
										digits: response.response.digits, 
										algorithm: response.response.algorithm, 
										secret: response.response.secret}}).then(() =>
									{
										self.rule_2();
									}, (error) =>
									{
										Notify.error(Translator.get('mfa.register.fail'));
										self.rule_1_display();
									});
								});
							}, (error) =>
							{
								var searchParams = new URLSearchParams();
								searchParams.set("error", error.response.error||'server_error');
								searchParams.set("error_description", error.response.error_description||'Unspecified error.');
								location.href = 'error?' + searchParams.toString() + '#e';
							});
						}}, Translator.get('mfa.enroll'))
					);
					div.classList.remove('wait');
				},
				
				rule_2: function()
				{
					var self = this;
					var div = document.getElementById('mfa_panel');
					while(div.firstChild) div.firstChild.remove();
					
					div.append(
						Node.p(Translator.get("mfa.auth")),
						Node.input({type: 'text', id: 'form_otp', placeholder: Translator.get('mfa.otp'), keydown: function(e) { if(this.value.length > 0 && (e.key === 'Enter' || e.keyCode === 13)) this.nextSibling.click(); }}),
						Node.button({className: 'raised', click: function(e)
						{
							e.stopImmediatePropagation();
							e.preventDefault();
							var o = document.getElementById('form_otp').value;
							if( !o ) return;
							
							div.classList.add('wait');
							div.append(Node.form({id: 'form_form', action: '/oauth/otp', method: 'POST', enctype: 'application/x-www-form-urlencoded', style: {display: 'none'}}, [
								Node.input({type: 'hidden', value: self.code, name: 'code'}),
								Node.input({type: 'hidden', value: o, name: 'otp'})
							]));
							div.lastChild.submit();
						}}, Translator.get('mfa.login')),
					);
					div.classList.remove('wait');
				}
			}));
		});
	}, (e) => { nok(e); });
});

export { x as default };