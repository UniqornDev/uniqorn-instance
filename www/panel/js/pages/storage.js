import { Page, Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml, config } from 'core';
import { open as openExplorer } from './explorer.js';
css('storage');

class StoragePage extends Page
{
	async show()
	{
		this.dom.classList.add('storage');
		document.body.querySelectorAll('nav li').forEach(e => e.classList.toggle('selected', e.dataset.link === 'storage'));

		this.init();
	}

	async hide()
	{
		while(this.dom.firstChild) this.dom.firstChild.remove();
	}

	init()
	{
		var self = this;
		this.dom.classList.add('wait');
		while(this.dom.firstChild) this.dom.firstChild.remove();

		this.dom.append(Node.div({className: 'action'},
			[
				Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.addStorage(); }}, [
					Node.span({className: 'icon'}, 'folder'),
					Node.span(Translator.get('storage.file.add'))]),
				Node.button({className: 'raised', click: (e) => { e.preventDefault(); self.addDatabase(); }}, [
					Node.span({className: 'icon'}, 'database'),
					Node.span(Translator.get('storage.database.add'))]),
				Node.button({className: 'raised', click: (e) => { e.preventDefault(); if( self.storages && self.databases ) openExplorer(self.storages, self.databases); }}, [
					Node.span({className: 'icon'}, 'explore'),
					Node.span(Translator.get('storage.explorer'))])
			])
		);

		Promise.all([
			Ajax.get('/api/contributor/storages'),
			Ajax.get('/api/contributor/databases')
		]).then(results =>
		{
			var storages = results[0].response.sort((a, b) => { return a.name > b.name ? 1 : -1; });
			var databases = results[1].response.sort((a, b) => { return a.name > b.name ? 1 : -1; });
			self.databases = databases;
			self.storages = storages;

			self.dom.append(
				Node.div({className: 'search'}, [
					Node.input({type: 'search', input: function()
					{
						self.filter(this.value);
					}}),
					Node.span({className: 'icon'}, 'search')
				]),
				Node.section(
				[
					Node.h2([
						Translator.get('storage.file.title'),
						Node.div({className: 'small_action'},
						[
							config.user.level === 'manager' ? Node.span({className: 'icon', click: () => { self.flattenGit(); }, dataset: {tooltip: Translator.get('storage.git.flatten')}}, 'compress') : null,
							config.user.level === 'manager' ? Node.span({className: 'icon', click: () => { self.resetGit(); }, dataset: {tooltip: Translator.get('storage.git.reset')}}, 'restart_alt') : null,
							Node.span({className: 'icon', click: () => { self.codeStorage(); }, dataset: {tooltip: Translator.get('code')}}, 'code')
						])
					]),
					Node.p(Translator.get('storage.file.explain')),
					Node.ol(
						storages.map(c => Node.li({className: 'card', dataset: {id: c.id}, click: function() { self.showStorage(this.dataset.id); }},
						[
							Node.span({className: 'icon'}, 'folder'),
							Node.span(safeHtml(c.name))
						]))
					)
				]),
				Node.section(
				[
					Node.h2([
						Translator.get('storage.database.title'),
						Node.div({className: 'small_action'},
						[
							Node.span({className: 'icon', click: () => { self.codeDatabase(); }, dataset: {tooltip: Translator.get('code')}}, 'code')
						])
					]),
					Node.p(Translator.get('storage.database.explain')),
					Node.ol(
						databases.map(c => Node.li({className: 'card', dataset: {id: c.id}, click: function() { self.showDatabase(this.dataset.id); }},
						[
							Node.span({className: 'icon'}, 'database'),
							Node.span(safeHtml(c.name))
						]))
					)
				])
			);

			self.dom.classList.remove('wait');
		}, (error) =>
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('fetch.error'));
			self.dom.classList.remove('wait');
		});
	}

	filter(value)
	{
		var words = (value||'').split(/\s+/g).map(w => new RegExp((w||'').replace(/[-\/\\^$*+?.()|[\]{}]/g, '\\$&'), 'i'));

		[].slice.call(this.dom.querySelectorAll('section li')).forEach(p =>
		{
			if( !value || value.length == 0 ) { p.classList.remove('hidden'); return; }

			for (var w = 0; w < words.length; w++)
			{
				if( !words[w].test(p.lastChild.textContent) )
				{
					p.classList.add('hidden');
					return;
				}
			}
			p.classList.remove('hidden');
		});

		// hide main section if empty
		[].slice.call(this.dom.querySelectorAll('section')).forEach(div =>
		{
			if( !value || value.length == 0 )
			{
				div.classList.remove('hidden');
			}
			else if( !!div.querySelector('li:not(.hidden)') )
			{
				div.classList.add('open')
				div.classList.remove('hidden')
			}
			else
			{
				div.classList.add('hidden')
			}
		});
	}

	// =========================
	//
	// STORAGE
	//
	// =========================

	showStorage(id)
	{
		var self = this;

		var storage = this.storages.find(s => s.id == id);
		if( !storage ) return;

		var m = Modal.custom([
			Node.h2(safeHtml(storage.name)),
			Node.div(Node.div({className: 'detail'}, self.showStorage_params(storage))),
			Node.div({className: 'action'},
			[
				Node.button({click: function(e)
				{
					e.preventDefault();
					Modal.confirm(Translator.get('storage.file.delete.confirm', storage.name), [
						Translator.get('remove'),
						Translator.get('cancel'),
					]).then(index =>
					{
						if( index > 0 ) return;

						self.dom.classList.add('wait');
						Ajax.delete('/api/contributor/storage/' + encodeURIComponent(id)).then(() =>
						{
							self.dom.classList.remove('wait');
							Notify.success(Translator.get('storage.file.delete.success'));
							m.ok();
							self.init();
						}, (error) =>
						{
							self.dom.classList.remove('wait');
							if( error.response && error.response.error && error.response.error.message )
								Notify.error(safeHtml(error.response.error.message));
							else
								Notify.error(Translator.get('storage.file.delete.error'));
						});
					}, () => {});
				}}, Translator.get('remove')),
				Node.button({click: function(e)
				{
					e.preventDefault();
					Modal.prompt(
						Translator.get('storage.file.rename.confirm'),
						Node.input({type: 'text', value: storage.name})
					).then(form =>
					{
						if( !form.value ) return;

						self.dom.classList.add('wait');
						Ajax.put('/api/contributor/storage/' + encodeURIComponent(id), {data: {name: form.value}}).then(() =>
						{
							self.dom.classList.remove('wait');
							Notify.success(Translator.get('storage.file.rename.success'));
							m.ok();
							self.init();
						}, (error) =>
						{
							self.dom.classList.remove('wait');
							if( error.response && error.response.error && error.response.error.message )
								Notify.error(safeHtml(error.response.error.message));
							else
								Notify.error(Translator.get('storage.file.rename.error'));
						});
					}, () => {});
				}}, Translator.get('rename')),
			])
		], true);
	}

	showStorage_params(storage)
	{
		if( storage.type == 'uniqorn.storage.file' )
		{
			return [
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.file.type')),
					Node.span({className: 'value'}, "Filesystem")
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.file.path')),
					Node.span({className: 'text'}, safeHtml(storage.parameters.root.replace(/^\/?storage\//i, '')))
				])
			];
		}

		if( storage.type == 'uniqorn.storage.aws' )
		{
			return [
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.file.type')),
					Node.span({className: 'value'}, "Amazon S3")
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.aws.region')),
					Node.span({className: 'text'}, safeHtml(storage.parameters.region))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.aws.bucket')),
					Node.span({className: 'text'}, safeHtml(storage.parameters.bucket))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.aws.key')),
					Node.span({className: 'text'}, safeHtml(storage.parameters.key))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.aws.secret')),
					Node.span({className: 'text'}, "**********")
				])
			];
		}

		return [];
	}

	addStorage()
	{
		var self = this;
		var m = Modal.custom([
			Node.h2(Translator.get('storage.file.add')),
			Node.form({className: 'storage'}, [
				Node.fieldset([
					Node.label(Translator.get('storage.file.name')),
					Node.input({type: 'text', name: 'name', placeholder: Translator.get('storage.file.name')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.file.type')),
					Node.select({name: 'type', change: function() {
						var d = this.parentNode.nextSibling;
						while(d.firstChild) d.firstChild.remove();
						Node.append(d, self.addStorage_params(this.value));
					}}, [
						Node.option({value: 'file'}, "Filesystem"),
						//Node.option({value: 'google'}, "Google Drive"),
						//Node.option({value: 'dropbox'}, "Dropbox"),
						Node.option({value: 'aws'}, "Amazon S3"),
						//Node.option({value: 'onedrive'}, "One Drive")
					])
				]),
				Node.div(self.addStorage_params('file'))
			]),
			Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
			Node.button({click: function(e)
			{
				e.preventDefault();

				var form = this.previousSibling.previousSibling;

				let params = {}
				form.querySelectorAll("div input").forEach(i => params[i.name] = i.value);

				m.dom.classList.add('wait');
				Ajax.post('/api/contributor/storage', {data: {
					name: form.elements.name.value,
					type: form.elements.type.value,
					parameters: JSON.stringify(params)
					}}).then(() =>
				{
					m.ok();
					Notify.success(Translator.get('storage.file.add.success'));
					self.init();
				}, (error) =>
				{
					m.dom.classList.remove('wait');
					if( error.response && error.response.error && error.response.error.message )
						Modal.alert(safeHtml(error.response.error.message));
					else
						Notify.error(Translator.get('storage.file.add.error'));
				});
			}}, Translator.get('create')),
		], true);
		m.dom.classList.add('promptable');
	}

	addStorage_params(type)
	{
		if( type == 'file' )
		{
			return Node.fieldset([
				Node.label(Translator.get('storage.file.path')),
				Node.input({type: 'text', name: 'root', placeholder: Translator.get('storage.file.path')})
			]);
		}

		if( type == 'aws' )
		{
			return [
				Node.fieldset([
					Node.label(Translator.get('storage.aws.region')),
					Node.input({type: 'text', name: 'region', placeholder: Translator.get('storage.aws.region')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.aws.bucket')),
					Node.input({type: 'text', name: 'bucket', placeholder: Translator.get('storage.aws.bucket')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.aws.key')),
					Node.input({type: 'text', name: 'key', placeholder: Translator.get('storage.aws.key')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.aws.secret')),
					Node.input({type: 'password', name: 'secret', placeholder: Translator.get('storage.aws.secret')})
				]),
			];
		}

		return [];
	}

	flattenGit()
	{
		var self = this;
		Modal.confirm(Translator.get('storage.git.flatten.confirm'), [
			Translator.get('storage.git.flatten'),
			Translator.get('cancel'),
		]).then(index =>
		{
			if( index > 0 ) return;

			self.dom.classList.add('wait');
			Ajax.post('/api/manager/git/flatten').then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('storage.git.flatten.success'));
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('storage.git.flatten.error'));
			});
		}, () => {});
	}

	resetGit()
	{
		var self = this;
		Modal.confirm(Translator.get('storage.git.reset.confirm'), [
			Translator.get('reset'),
			Translator.get('cancel'),
		]).then(index =>
		{
			if( index > 0 ) return;

			self.dom.classList.add('wait');
			Ajax.post('/api/manager/git/reset').then(() =>
			{
				self.dom.classList.remove('wait');
				Notify.success(Translator.get('storage.git.reset.success'));
				self.init();
			}, (error) =>
			{
				self.dom.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('storage.git.reset.error'));
			});
		}, () => {});
	}

	codeStorage()
	{
		var m = Modal.alert([
			Node.h2(Translator.get('code.sample')),
			Node.p(Translator.get('code.storage')),
			Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/download", "GET")'
				+ '\n\t.process(data -&gt; {\n\t\t'
				+ 'Storage.Type store = Api.storage("my_content"); // get our storage\n\t\t'
				+ 'return store.get("/path/to/file"); // fetch a file\n\t});')),
			Node.p(Node.a({href: "https://uniqorn.dev/doc#howto-storage", target: "_blank"}, Translator.get('code.doc'))),
			Node.p(Node.a({href: "https://uniqorn.dev/javadoc#storage", target: "_blank"}, Translator.get('code.javadoc')))
		]);

		Prism.highlightElement(m.dom.querySelector('code'));
	}

	// =========================
	//
	// DATABASE
	//
	// =========================

	showDatabase(id)
	{
		var self = this;

		var db = this.databases.find(d => d.id == id);
		if( !db ) return;

		var m = Modal.custom([
			Node.h2(safeHtml(db.name)),
			Node.div(Node.div({className: 'detail'}, self.showDatabase_params(db))),
			Node.div({className: 'action'},
			[
				Node.button({click: function(e)
				{
					e.preventDefault();
					Modal.confirm(Translator.get('storage.database.delete.confirm', db.name), [
						Translator.get('remove'),
						Translator.get('cancel'),
					]).then(index =>
					{
						if( index > 0 ) return;

						self.dom.classList.add('wait');
						Ajax.delete('/api/contributor/database/' + encodeURIComponent(id)).then(() =>
						{
							self.dom.classList.remove('wait');
							Notify.success(Translator.get('storage.database.delete.success'));
							m.ok();
							self.init();
						}, (error) =>
						{
							self.dom.classList.remove('wait');
							if( error.response && error.response.error && error.response.error.message )
								Notify.error(safeHtml(error.response.error.message));
							else
								Notify.error(Translator.get('storage.database.delete.error'));
						});
					}, () => {});
				}}, Translator.get('remove')),
				Node.button({click: function(e)
				{
					e.preventDefault();
					Modal.prompt(
						Translator.get('storage.database.rename.confirm'),
						Node.input({type: 'text', value: db.name})
					).then(form =>
					{
						if( !form.value ) return;

						self.dom.classList.add('wait');
						Ajax.put('/api/contributor/database/' + encodeURIComponent(id), {data: {name: form.value}}).then(() =>
						{
							self.dom.classList.remove('wait');
							Notify.success(Translator.get('storage.database.rename.success'));
							m.ok();
							self.init();
						}, (error) =>
						{
							self.dom.classList.remove('wait');
							if( error.response && error.response.error && error.response.error.message )
								Notify.error(safeHtml(error.response.error.message));
							else
								Notify.error(Translator.get('storage.database.rename.error'));
						});
					}, () => {});
				}}, Translator.get('rename')),
			])
		], true);
	}

	showDatabase_params(db)
	{
		if( db.type == 'uniqorn.database.sqlite' )
		{
			return [
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.type')),
					Node.span({className: 'value'}, "SqLite")
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.path')),
					Node.span({className: 'text'}, safeHtml(db.parameters.path.replace(/^\/?storage\//i, '')))
				])
			];
		}

		if( db.type == 'uniqorn.database.mariadb' || db.type == 'uniqorn.database.pgsql' )
		{
			return [
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.type')),
					Node.span({className: 'value'},
						db.parameters.driver == 'org.mariadb.jdbc.Driver' ? "Mariadb / Mysql" :
						db.parameters.driver == 'org.postgresql.Driver' ? "Postgresql" :
						db.parameters.driver)
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.host')),
					Node.span({className: 'text'}, safeHtml(db.parameters.host))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.port')),
					Node.span({className: 'text'}, safeHtml(db.parameters.port))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.database')),
					Node.span({className: 'text'}, safeHtml(db.parameters.database))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.username')),
					Node.span({className: 'text'}, safeHtml(db.parameters.username))
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.password')),
					Node.span({className: 'text'}, "**********")
				]),
				Node.p([
					Node.span({className: 'title'}, Translator.get('storage.database.ssl')),
					Node.span({className: 'text'}, !db.parameters.hasOwnProperty('ssl') || !!db.parameters.ssl ? Translator.get('yes') : Translator.get('no'))
				])
			];
		}

		return [];
	}

	addDatabase()
	{
		var self = this;
		var m = Modal.custom([
			Node.h2(Translator.get('storage.database.add')),
			Node.form({className: 'database'}, [
				Node.fieldset([
					Node.label(Translator.get('storage.database.name')),
					Node.input({type: 'text', name: 'name', placeholder: Translator.get('storage.database.name')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.type')),
					Node.select({name: 'type', change: function() {
						var d = this.parentNode.nextSibling;
						while(d.firstChild) d.firstChild.remove();
						Node.append(d, self.addDatabase_params(this.value));
					}}, [
						Node.option({value: 'mariadb'}, "Mariadb / Mysql"),
						Node.option({value: 'pgsql'}, "Postgresql"),
						Node.option({value: 'sqlite'}, "SqLite")
					])
				]),
				Node.div(self.addDatabase_params('mariadb'))
			]),
			Node.button({click: function(e) { e.preventDefault(); m.ok(); }}, Translator.get('cancel')),
			Node.button({click: function(e)
			{
				e.preventDefault();

				var form = this.previousSibling.previousSibling;

				let params = {}
				form.querySelectorAll("div input").forEach(i =>
				{
					if( i.type == 'checkbox' ) params[i.name] = i.checked;
					else params[i.name] = i.value
				});

				m.dom.classList.add('wait');
				Ajax.post('/api/contributor/database', {data: {
					name: form.elements.name.value,
					type: form.elements.type.value,
					parameters: JSON.stringify(params)
					}}).then(() =>
				{
					m.ok();
					Notify.success(Translator.get('storage.database.add.success'));
					self.init();
				}, (error) =>
				{
					m.dom.classList.remove('wait');
					if( error.response && error.response.error && error.response.error.message )
						Modal.alert(safeHtml(error.response.error.message));
					else
						Notify.error(Translator.get('storage.database.add.error'));
				});
			}}, Translator.get('create')),
		], true);
		m.dom.classList.add('promptable');
	}

	addDatabase_params(type)
	{
		if( type == 'sqlite' )
		{
			return Node.fieldset([
				Node.label(Translator.get('storage.database.path')),
				Node.input({type: 'text', name: 'path', placeholder: Translator.get('storage.database.path')})
			]);
		}

		if( type == 'mariadb' || type == 'pgsql' )
		{
			return [
				Node.fieldset([
					Node.label(Translator.get('storage.database.host')),
					Node.input({type: 'text', name: 'host', placeholder: Translator.get('storage.database.host')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.port')),
					Node.input({type: 'text', name: 'port', placeholder: Translator.get('storage.database.port')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.database')),
					Node.input({type: 'text', name: 'database', placeholder: Translator.get('storage.database.database')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.username')),
					Node.input({type: 'text', name: 'username', placeholder: Translator.get('storage.database.username')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.password')),
					Node.input({type: 'password', name: 'password', placeholder: Translator.get('storage.database.password')})
				]),
				Node.fieldset([
					Node.label(Translator.get('storage.database.ssl')),
					Node.input({type: 'checkbox', name: 'ssl', placeholder: Translator.get('storage.database.ssl')})
				])
			];
		}

		return [];
	}

	codeDatabase()
	{
		var m = Modal.alert([
			Node.h2(Translator.get('code.sample')),
			Node.p(Translator.get('code.database')),
			Node.pre(Node.code({className: 'language-java'}, 'new Api("/api/query", "GET")'
				+ '\n\t.process(data -&gt; {\n\t\t'
				+ 'Database.Type db = Api.database("my_db"); // get our database\n\t\t'
				+ 'return db.query("SELECT * FROM table"); // execute query\n\t});')),
			Node.p(Node.a({href: "https://uniqorn.dev/doc#howto-database", target: "_blank"}, Translator.get('code.doc'))),
			Node.p(Node.a({href: "https://uniqorn.dev/javadoc#database", target: "_blank"}, Translator.get('code.javadoc')))
		]);

		Prism.highlightElement(m.dom.querySelector('code'));
	}
}

const page = new StoragePage();
export { page as default };
