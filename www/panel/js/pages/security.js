
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Ajax', 'Translator', 'Notify', 'Modal', 'page.security.css', 'ext/prism.js', 'ext/prism.css').then(([Page, Node, Ajax, Translator, Notify, Modal]) =>
	{
		var page = new Page();
		Object.assign(page, 
		{
			show: function()
			{
				this.dom.classList.add('security');
				document.body.querySelectorAll('nav li').forEach(e => { if( e.dataset.link == 'security') e.classList.add('selected'); else e.classList.remove('selected'); });
				
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
				this.dom.classList.add('wait');
				while(this.dom.firstChild) this.dom.firstChild.remove();
				
				Promise.all([
					Ajax.get('/api/contributor/roles'),
					Ajax.get('/api/contributor/groups'),
					Ajax.get('/api/contributor/users', {data: {type: 'consumer'}}),
					ae.userlevel == 'manager' ? Ajax.get('/api/contributor/users', {data: {type: 'contributor'}}) : Promise.resolve({response: []}),
					ae.userlevel == 'manager' ? Ajax.get('/api/contributor/users', {data: {type: 'manager'}}) : Promise.resolve({response: []}),
				]).then(results =>
				{
					let roles = results[0].response.sort((a, b) => { return a.name.toUpperCase() < b.name.toUpperCase() ? -1 : 1; });
					let groups = results[1].response.sort((a, b) => { return a.name.toUpperCase() < b.name.toUpperCase() ? -1 : 1; });
					let consumers = results[2].response.sort((a, b) => { return a.name.toUpperCase() < b.name.toUpperCase() ? -1 : 1; });
					let users = results[3].response.concat(results[4].response).sort((a, b) => { return a.name.toUpperCase() < b.name.toUpperCase() ? -1 : 1; });
					
					Node.append(this.dom,
					[
						Node.section(
						[
							Node.h2([
								Translator.get('security.roles'),
								Node.div({className: 'small_action'},
								[
									ae.userlevel == 'manager' ? Node.span({className: 'icon', click: () => { self.addRole(); }, dataset: {tooltip: Translator.get('security.role.add')}}, 'add') : null,
									Node.span({className: 'icon', click: () => { self.codeRole(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
								])
							]),
							Node.p(Translator.get('security.roles.explain')),
							Node.ol(roles.map(r => Node.li({className: 'card', dataset: {id: r.id}, click: function() { self.showRole(this.dataset.id); }}, 
							[
								Node.span({className: 'icon'}, 'badge'),
								ae.safeHtml(r.name)
							])))
						]),
						
						Node.section(
						[
							Node.h2([
								Translator.get('security.groups'),
								Node.div({className: 'small_action'},
								[
									ae.userlevel == 'manager' ? Node.span({className: 'icon', click: () => { self.addGroup(); }, dataset: {tooltip: Translator.get('security.group.add')}}, 'add') : null,
									Node.span({className: 'icon', click: () => { self.codeGroup(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
								])
							]),
							Node.p(Translator.get('security.groups.explain')),
							Node.ol(groups.map(g => Node.li({className: 'card', dataset: {id: g.id}, click: function() { self.showGroup(this.dataset.id); }}, 
							[
								Node.span({className: 'icon'}, 'group'),
								ae.safeHtml(g.name)
							])))
						]),
						
						Node.section(
						[
							Node.h2([
								Translator.get('security.consumers'),
								Node.div({className: 'small_action'},
								[
									ae.userlevel == 'manager' ? Node.span({className: 'icon', click: () => { self.addConsumer(); }, dataset: {tooltip: Translator.get('security.consumer.add')}}, 'add') : null,
									Node.span({className: 'icon', click: () => { self.codeConsumer(); }, dataset: {tooltip: Translator.get('code')}}, 'code'),
								])
							]),
							Node.p(Translator.get('security.consumers.explain')),
							Node.ol(consumers.map(u => Node.li({className: 'card', dataset: {id: u.id}, click: function() { self.showConsumer(this.dataset.id); }}, 
							[
								Node.span({className: 'icon'}, 'frame_person'),
								ae.safeHtml(u.name)
							])))
						]),
						
						ae.userlevel == 'manager' ? Node.section(
						[
							Node.h2([
								Translator.get('security.users'),
								Node.div({className: 'small_action'},
								[
									Node.span({className: 'icon', click: () => { self.addUser(); }, dataset: {tooltip: Translator.get('security.user.add')}}, 'add')
								])
							]),
							Node.p(Translator.get('security.users.explain')),
							Node.ol(users.map(u => Node.li({className: 'card', dataset: {id: u.id}, click: function() { self.showUser(this.dataset.id); }}, 
							[
								Node.span({className: 'icon'}, 
									u.type == 'contributor' ? 'identity_platform' : 
									u.type == 'manager' ? 'shield_person' : 'person'
									),
								ae.safeHtml(u.name)
							])))
						]) : null,
					]);
					
					self.dom.classList.remove('wait');
				}, (error) =>
				{
					Notify.error(Translator.get('fetch.error'));
					self.dom.classList.remove('wait');
				});
			},
			
			// =========================
			//
			// ROLES
			//
			// =========================
			
			addRole: function()
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('security.role.add')),
					Node.input({type: 'text', placeholder: Translator.get('security.role.name')})
				).then((form) =>
				{
					if( !form.value )
					{
						Notify.warning(Translator.get('security.role.empty'));
						return;
					}
					
					self.dom.classList.add('wait');
					Ajax.post('/api/manager/role', {data: {name: form.value}}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('security.role.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('security.role.error'));
					});
				}, () => {});
			},
			
			showRole: function(id)
			{
				var self = this;
				
				var m = Modal.custom([], true);
				var div = m.dom.firstChild;
				div.classList.add('wait');
				
				Ajax.get('/api/contributor/role/' + encodeURIComponent(id)).then(result =>
				{
					div.classList.remove('wait');
					
					Node.append(div,
					[
						Node.h2(ae.safeHtml(result.response.name)),
						Node.p(Translator.get('security.role.users')),
						Node.ol({classList: 'short'}, result.response.users.map(u => Node.li({className: 'card'}, [Node.span({className: 'icon'}, 'frame_person'), ae.safeHtml(u.name)]))),
						ae.userlevel == 'manager' ? Node.div({className: 'action'},
						[
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.role.delete.confirm', result.response.name), [
									Translator.get('remove'),
									Translator.get('cancel'),
								]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.delete('/api/manager/role/' + encodeURIComponent(id)).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.role.delete.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.role.delete.error'));
									});
								}, () => {});
							}}, Translator.get('remove')),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.prompt(Translator.get('security.role.rename.confirm'),
									Node.input({type: 'text', value: result.response.name})
								).then((form) =>
								{
									if( !form.value )
									{
										Notify.warning(Translator.get('security.role.empty'));
										return;
									}
									
									if( form.value == result.response.name ) return;
									
									self.dom.classList.add('wait');
									Ajax.put('/api/manager/role/' + encodeURIComponent(id), {data: {name: form.value}}).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.role.rename.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.role.rename.error'));
									});
								}, () => {});
							}}, Translator.get('rename')),
						]) : null
					]);
				}, (error) => 
				{
					Notify.error(Translator.get('fetch.error'));
					m.nok();
				});
			},
			
			codeRole: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.security.role')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")\n\t.allowRole("Viewer") // allow role\n\t.denyRole("Public") // deny role'
						+ '\n\t.process((data, user) -&gt; {\n\t\tif (user.hasRole("Private")) // manual role check\n\t\t...\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
			
			// =========================
			//
			// GROUPS
			//
			// =========================
			
			addGroup: function()
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('security.group.add')),
					Node.input({type: 'text', placeholder: Translator.get('security.group.name')})
				).then((form) =>
				{
					if( !form.value )
					{
						Notify.warning(Translator.get('security.group.empty'));
						return;
					}
					
					self.dom.classList.add('wait');
					Ajax.post('/api/manager/group', {data: {name: form.value}}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('security.group.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('security.group.error'));
					});
				}, () => {});
			},
			
			showGroup: function(id)
			{
				var self = this;
				
				var m = Modal.custom([], true);
				var div = m.dom.firstChild;
				div.classList.add('wait');
				
				Ajax.get('/api/contributor/group/' + encodeURIComponent(id)).then(result =>
				{
					div.classList.remove('wait');
					
					Node.append(div,
					[
						Node.h2(ae.safeHtml(result.response.name)),
						Node.p(Translator.get('security.group.users')),
						Node.ol({classList: 'short'}, result.response.users.map(u => Node.li({className: 'card'}, [Node.span({className: 'icon'}, 'frame_person'), ae.safeHtml(u.name)]))),
						ae.userlevel == 'manager' ? Node.div({className: 'action'},
						[
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.group.delete.confirm', result.response.name), [
									Translator.get('remove'),
									Translator.get('cancel'),
								]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.delete('/api/manager/group/' + encodeURIComponent(id)).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.group.delete.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.group.delete.error'));
									});
								}, () => {});
							}}, Translator.get('remove')),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.prompt(Translator.get('security.group.rename.confirm'),
									Node.input({type: 'text', value: result.response.name})
								).then((form) =>
								{
									if( !form.value )
									{
										Notify.warning(Translator.get('security.group.empty'));
										return;
									}
									
									if( form.value == result.response.name ) return;
									
									self.dom.classList.add('wait');
									Ajax.put('/api/manager/group/' + encodeURIComponent(id), {data: {name: form.value}}).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.group.rename.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.group.rename.error'));
									});
								}, () => {});
							}}, Translator.get('rename')),
						]) : null
					]);
				}, (error) => 
				{
					Notify.error(Translator.get('fetch.error'));
					m.nok();
				});
			},
			
			codeGroup: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.security.group')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")\n\t.allowGroup("Alpha Team") // allow group\n\t.denyGroup("Beta Team") // deny group'
						+ '\n\t.process((data, user) -&gt; {\n\t\tif (user.isMemberOf("Zeta Team")) // manual group check\n\t\t...\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('code'));
			},
		
			// =========================
			//
			// CONSUMERS
			//
			// =========================
			
			addConsumer: function()
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('security.consumer.add')),
					Node.form([
						Node.input({type: 'text', name: 'name', placeholder: Translator.get('security.consumer.name')}),
						Node.input({type: 'hidden', name: 'login', value: 'undefined'}),
						Node.input({type: 'hidden', name: 'type', value: 'consumer'}),
					])
				).then((form) =>
				{
					if( !form.elements.login.value || !form.elements.name.value )
					{
						Notify.warning(Translator.get('security.consumer.empty'));
						return;
					}
					
					self.dom.classList.add('wait');
					Ajax.post('/api/manager/user', {data: form}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('security.consumer.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('security.consumer.error'));
					});
				}, () => {});
			},
			
			showConsumer: function(id)
			{
				var self = this;
				
				var m = Modal.custom([], true);
				var div = m.dom.firstChild;
				div.classList.add('wait');
				
				Ajax.get('/api/contributor/user/' + encodeURIComponent(id)).then(result =>
				{
					div.classList.remove('wait');
					
					let user = result.response;
					Node.append(div,
					[
						Node.h2(ae.safeHtml(user.name)),
						Node.p([
							Translator.get('security.consumer.groups'),
							ae.userlevel == 'manager' ? Node.div({className: 'small_action'},
							[
								Node.span({className: 'icon', click: () => { self.editGroups(user.id, user.groups, m); }, dataset: {tooltip: Translator.get('update')}}, 'edit'),
							]) : null
						]),
						Node.ol({classList: 'short'}, result.response.groups.map(g => Node.li({className: 'card'}, [Node.span({className: 'icon'}, 'group'), ae.safeHtml(g.name)]))),
						Node.p([
							Translator.get('security.consumer.roles'),
							ae.userlevel == 'manager' ? Node.div({className: 'small_action'},
							[
								Node.span({className: 'icon', click: () => { self.editRoles(user.id, user.roles, m); }, dataset: {tooltip: Translator.get('update')}}, 'edit'),
							]) : null
						]),
						Node.ol({classList: 'short'}, result.response.roles.map(r => Node.li({className: 'card'}, [Node.span({className: 'icon'}, 'badge'), ae.safeHtml(r.name)]))),
						ae.userlevel == 'manager' ? Node.div({className: 'action'},
						[
							Node.button({className: 'raised', click: function(e) 
							{
								e.preventDefault();
								self.dom.classList.add('wait');
								Ajax.get('/api/manager/user/' + encodeURIComponent(id) + '/key').then((result) =>
								{
									self.dom.classList.remove('wait');
									if( result.response.token )
										Modal.alert(Translator.get('security.consumer.reveal.result', result.response.token));
									else
										Modal.alert(Translator.get('security.consumer.reveal.empty'));
								}, () =>
								{
									self.dom.classList.remove('wait');
									Notify.error(Translator.get('security.consumer.reveal.error'));
								});
							}}, [
								Node.span({className: 'icon'}, 'visibility'), 
								Node.span(Translator.get('security.consumer.reveal'))]),
							Node.button({className: 'raised', click: function(e) 
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.consumer.rotate.confirm', user.name), [Translator.get('reset'), Translator.get('cancel')]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.patch('/api/manager/user/' + encodeURIComponent(id) + '/key').then((result) =>
									{
										self.dom.classList.remove('wait');
										Modal.alert(Translator.get('security.consumer.rotate.result', result.response.token));
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.consumer.rotate.error'));
									});
								}, () => {});
							}}, [
								Node.span({className: 'icon'}, 'sync'), 
								Node.span(Translator.get('security.consumer.rotate'))]),
							Node.br(),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.consumer.delete.confirm', user.name), [
									Translator.get('remove'),
									Translator.get('cancel'),
								]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.delete('/api/manager/user/' + encodeURIComponent(id)).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.consumer.delete.success'));
										m.ok();
										self.init();
									}, (error) =>
									{
										self.dom.classList.remove('wait');
										if( error.response && error.response.error && error.response.error.message )
											Notify.error(ae.safeHtml(error.response.error.message));
										else
											Notify.error(Translator.get('security.consumer.delete.error'));
									});
								}, () => {});
							}}, Translator.get('remove')),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.prompt(Translator.get('security.consumer.rename.confirm', user.name),
									Node.form([
										Node.input({type: 'text', name: 'name', value: user.name, placeholder: Translator.get('security.user.name')})
									])
								).then((form) =>
								{
									self.dom.classList.add('wait');
									Ajax.put('/api/manager/user/' + encodeURIComponent(id), {data: form}).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.consumer.rename.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.consumer.rename.error'));
									});
								}, () => {});
							}}, Translator.get('rename')),
						]) : null
					]);
				}, (error) => 
				{
					Notify.error(Translator.get('fetch.error'));
					m.nok();
				});
			},
			
			codeConsumer: function()
			{
				var m = Modal.alert([
					Node.h2(Translator.get('code.sample')),
					Node.p(Translator.get('code.security.user')),
					Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/test", "GET")\n\t.allowUser("Bob") // allow user\n\t.denyUser("Alice") // deny user'
						+ '\n\t.process((data, user) -&gt; {\n\t\tif (user.name().equals("John Doe")) // manual user check\n\t\t...\n\t});'))
				]);
				
				Prism.highlightElement(m.dom.querySelector('pre code'));
			},
			
			editGroups: function(id, groups, m)
			{
				var self = this;
				
				// get the original group list
				var list = this.dom.querySelectorAll("section > ol")[1].children;
				var m2 = Modal.custom([
					Node.div({className: "bicolumn"},
					[
						Node.div([
							Node.p(Translator.get('security.consumer.groups.available')),
							Node.ol({click: function(e)
							{
								if( e.target.nodeName != 'LI' ) return;
								this.parentNode.nextSibling.lastChild.append(e.target);
							}}, Array.from(list).filter(n => !groups.some(g => g.id == n.dataset.id)).map(n => n.cloneNode(true)))
						]),
						Node.div([
							Node.p(Translator.get('security.consumer.groups.selected')),
							Node.ol({click: function(e)
							{
								if( e.target.nodeName != 'LI' ) return;
								this.parentNode.previousSibling.lastChild.append(e.target);
							}}, Array.from(list).filter(n => groups.some(g => g.id == n.dataset.id)).map(n => n.cloneNode(true)))
						])
					]),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							
							self.dom.classList.add('wait');
							var selected = Array.from(this.parentNode.previousSibling.lastChild.lastChild.querySelectorAll('li')).map(n => n.dataset.id);
							Ajax.put('/api/manager/user/' + encodeURIComponent(id) + '/groups', {data: {groups: JSON.stringify(selected)}}).then(result =>
							{
								self.dom.classList.remove('wait');
								Notify.success(Translator.get('security.consumer.groups.success'));
								m.ok();
								m2.ok();
								self.showConsumer(id);
							}, (error) =>
							{
								self.dom.classList.remove('wait');
								Notify.error(Translator.get('security.consumer.groups.error'));
							});
						}}, Translator.get('save')),
						Node.button({click: function(e)
						{
							e.preventDefault();
							m2.ok();
						}}, Translator.get('cancel')),
					])
				], true);
			},
			
			editRoles: function(id, roles, m)
			{
				var self = this;
				
				// get the original group list
				var list = this.dom.querySelectorAll("section > ol")[0].children;
				var m2 = Modal.custom([
					Node.div({className: "bicolumn"},
					[
						Node.div([
							Node.p(Translator.get('security.consumer.roles.available')),
							Node.ol({click: function(e)
							{
								if( e.target.nodeName != 'LI' ) return;
								this.parentNode.nextSibling.lastChild.append(e.target);
							}}, Array.from(list).filter(n => !roles.some(r => r.id == n.dataset.id)).map(n => n.cloneNode(true)))
						]),
						Node.div([
							Node.p(Translator.get('security.consumer.roles.selected')),
							Node.ol({click: function(e)
							{
								if( e.target.nodeName != 'LI' ) return;
								this.parentNode.previousSibling.lastChild.append(e.target);
							}}, Array.from(list).filter(n => roles.some(r => r.id == n.dataset.id)).map(n => n.cloneNode(true)))
						])
					]),
					Node.div({className: 'action'},
					[
						Node.button({click: function(e)
						{
							e.preventDefault();
							
							self.dom.classList.add('wait');
							var selected = Array.from(this.parentNode.previousSibling.lastChild.lastChild.querySelectorAll('li')).map(n => n.dataset.id);
							Ajax.put('/api/manager/user/' + encodeURIComponent(id) + '/roles', {data: {roles: JSON.stringify(selected)}}).then(result =>
							{
								self.dom.classList.remove('wait');
								Notify.success(Translator.get('security.consumer.roles.success'));
								m.ok();
								m2.ok();
								self.showConsumer(id);
							}, (error) =>
							{
								self.dom.classList.remove('wait');
								Notify.error(Translator.get('security.consumer.roles.error'));
							});
						}}, Translator.get('save')),
						Node.button({click: function(e)
						{
							e.preventDefault();
							m2.ok();
						}}, Translator.get('cancel')),
					])
				], true);
			},
			
			// =========================
			//
			// USERS
			//
			// =========================
			
			addUser: function()
			{
				var self = this;
				Modal.prompt(
					Node.h2(Translator.get('security.user.add')),
					Node.form([
						Node.input({type: 'text', name: 'login', placeholder: Translator.get('security.user.login')}),
						Node.input({type: 'text', name: 'name', placeholder: Translator.get('security.user.name')}),
						Node.select({name: 'type'}, [
							Node.option({value: 'contributor'}, Translator.get('security.user.type.contributor')),
							Node.option({value: 'manager'}, Translator.get('security.user.type.manager')),
						])
					])
				).then((form) =>
				{
					if( !form.elements.login.value || !form.elements.name.value )
					{
						Notify.warning(Translator.get('security.user.empty'));
						return;
					}
					
					self.dom.classList.add('wait');
					Ajax.post('/api/manager/user', {data: form}).then(result =>
					{
						self.dom.classList.remove('wait');
						Notify.success(Translator.get('security.user.success'));
						self.init();
					}, (error) =>
					{
						self.dom.classList.remove('wait');
						if( error.response && error.response.error && error.response.error.message )
							Notify.error(ae.safeHtml(error.response.error.message));
						else
							Notify.error(Translator.get('security.user.error'));
					});
				}, () => {});
			},
			
			showUser: function(id)
			{
				var self = this;
				
				var m = Modal.custom([], true);
				var div = m.dom.firstChild;
				div.classList.add('wait');
				
				Ajax.get('/api/contributor/user/' + encodeURIComponent(id)).then(result =>
				{
					div.classList.remove('wait');
					
					let user = result.response;
					div.append(
						Node.h2(ae.safeHtml(user.name)),
						Node.div(Node.div({className: 'detail'}, [
							Node.p([
								Node.span({className: 'title'}, Translator.get('security.user.info.login')),
								Node.span({className: 'value'}, ae.safeHtml(user.login))
							]),
							Node.p([
								Node.span({className: 'title'}, Translator.get('security.user.info.name')),
								Node.span({className: 'text'}, ae.safeHtml(user.name))
							]),
							Node.p([
								Node.span({className: 'title'}, Translator.get('security.user.info.level')),
								Node.span({className: 'value'}, 
									user.type == 'contributor' ? Translator.get('security.user.type.contributor') : 
									user.type == 'manager' ? Translator.get('security.user.type.manager') : 
									Translator.get('security.user.type.other'))
							])
						])),
						Node.div({className: 'action'},
						[
							Node.button({className: 'raised', click: function(e) 
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.user.reset.confirm', user.name), [Translator.get('reset'), Translator.get('cancel')]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.patch('/api/manager/user/' + encodeURIComponent(id) + '/password').then((result) =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.user.reset.success'));
										Modal.alert(Translator.get('security.user.reset.result', result.response.password));
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.user.reset.error'));
									});
								}, () => {});
							}}, [
								Node.span({className: 'icon'}, 'key'), 
								Node.span(Translator.get('security.user.reset_password'))]),
							Node.br(),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.confirm(Translator.get('security.user.delete.confirm', user.name), [
									Translator.get('remove'),
									Translator.get('cancel'),
								]).then(index =>
								{
									if( index > 0 ) return;
									
									self.dom.classList.add('wait');
									Ajax.delete('/api/manager/user/' + encodeURIComponent(id)).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.user.delete.success'));
										m.ok();
										self.init();
									}, (error) =>
									{
										self.dom.classList.remove('wait');
										if( error.response && error.response.error && error.response.error.message )
											Notify.error(ae.safeHtml(error.response.error.message));
										else
											Notify.error(Translator.get('security.user.delete.error'));
									});
								}, () => {});
							}}, Translator.get('remove')),
							Node.button({click: function(e)
							{
								e.preventDefault();
								Modal.prompt(Translator.get('security.user.rename.confirm', user.name),
									Node.form([
										Node.input({type: 'text', name: 'login', value: user.login, placeholder: Translator.get('security.user.login')}),
										Node.input({type: 'text', name: 'name', value: user.name, placeholder: Translator.get('security.user.name')}),
										Node.select({name: 'type', value: user.type}, [
											Node.option({value: 'contributor'}, Translator.get('security.user.type.contributor')),
											Node.option({value: 'manager'}, Translator.get('security.user.type.manager')),
										])
									])
								).then((form) =>
								{
									self.dom.classList.add('wait');
									Ajax.put('/api/manager/user/' + encodeURIComponent(id), {data: form}).then(() =>
									{
										self.dom.classList.remove('wait');
										Notify.success(Translator.get('security.user.rename.success'));
										m.ok();
										self.init();
									}, () =>
									{
										self.dom.classList.remove('wait');
										Notify.error(Translator.get('security.user.rename.error'));
									});
								}, () => {});
							}}, Translator.get('update')),
						])
					);
				}, (error) => 
				{
					Notify.error(Translator.get('fetch.error'));
					m.nok();
				});
			},
		});
		
		ok(page);
	}, (e) => { nok(e); });
});

export { x as default };