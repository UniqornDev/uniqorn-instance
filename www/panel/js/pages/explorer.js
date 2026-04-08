import { Node, Ajax, Translator, Notify, Modal } from 'core';
import { css, safeHtml } from 'core';
css('explorer');

export function open(storages, databases)
{
	// --- State ---
	var currentDrive = null;
	var currentPath = '';
	var currentTable = null;
	var queryState = {orderBy: null, orderDir: 'ASC'};

	// --- Layout ---
	var breadcrumb = Node.div({className: 'breadcrumb'});
	var fileInput = Node.input({type: 'file', multiple: true, style: {display: 'none'}, change: function()
	{
		if( !currentDrive || currentDrive.type !== 'storage' ) return;
		uploadFiles(currentDrive.id, currentPath, this.files);
		this.value = '';
	}});

	var btnUpload = Node.button({click: function(e) { e.preventDefault(); fileInput.click(); }},
		[Node.span({className: 'icon'}, 'upload'), Node.span(Translator.get('explorer.upload'))]);
	var btnNewFolder = Node.button({click: function(e) { e.preventDefault(); newFolder(); }},
		[Node.span({className: 'icon'}, 'create_new_folder'), Node.span(Translator.get('explorer.newfolder'))]);
	var btnSql = Node.button({click: function(e) { e.preventDefault(); if( currentDrive ) showSqlDialog(currentDrive.id); }},
		[Node.span({className: 'icon'}, 'terminal'), Node.span(Translator.get('explorer.sql'))]);
	var btnAddRow = Node.button({click: function(e) { e.preventDefault(); if( currentDrive && currentTable ) addRow(currentDrive.id, currentTable); }},
		[Node.span({className: 'icon'}, 'add'), Node.span(Translator.get('explorer.addrow'))]);
	var btnRefresh = Node.button({click: function(e) { e.preventDefault(); refresh(); }},
		[Node.span({className: 'icon'}, 'refresh'), Node.span(Translator.get('explorer.refresh'))]);

	btnUpload.style.display = 'none';
	btnNewFolder.style.display = 'none';
	btnSql.style.display = 'none';
	btnAddRow.style.display = 'none';

	var tree = Node.div({className: 'explorer-tree'});
	var content = Node.div({className: 'explorer-content'});

	var explorer = Node.div({className: 'explorer'},
	[
		Node.div({className: 'explorer-toolbar'},
		[
			breadcrumb,
			fileInput,
			btnUpload,
			btnNewFolder,
			btnSql,
			btnAddRow,
			btnRefresh
		]),
		Node.div({className: 'explorer-body'},
		[
			tree,
			content
		])
	]);

	var m = Modal.custom([], true);
	m.dom.classList.add('fullscreen');
	var div = m.dom.firstChild;
	div.append(explorer);

	// --- Drag & drop ---
	content.addEventListener('dragover', function(e) { e.preventDefault(); this.classList.add('dragover'); });
	content.addEventListener('dragleave', function(e) { this.classList.remove('dragover'); });
	content.addEventListener('drop', function(e)
	{
		e.preventDefault();
		this.classList.remove('dragover');
		if( currentDrive && currentDrive.type === 'storage' )
		{
			uploadFiles(currentDrive.id, currentPath, e.dataTransfer.files);
		}
	});

	buildTree(storages, databases);
	updateBreadcrumb();

	// =========================================================
	// Tree
	// =========================================================

	function buildTree(storages, databases)
	{
		while( tree.firstChild ) tree.firstChild.remove();

		storages.forEach(function(s)
		{
			var children = Node.div({className: 'tree-children collapsed'});
			var chevron = Node.span({className: 'icon chevron', click: function(e)
			{
				e.stopPropagation();
				toggleNode(node, function() { expandStorageNode(node, s.id, ''); });
			}}, 'chevron_right');
			var item = Node.div({className: 'tree-item', click: function()
			{
				selectTreeItem(this);
				currentDrive = {type: 'storage', id: s.id, name: s.name};
				currentPath = '';
				currentTable = null;
				if( children.classList.contains('collapsed') )
					expandStorageNode(node, s.id, '');
				navigateStorage(s.id, '');
			}}, [chevron, Node.span({className: 'icon'}, 'folder'), Node.span(safeHtml(s.name))]);
			var node = Node.div({className: 'tree-node', dataset: {type: 'storage', id: s.id, path: ''}}, [item, children]);
			tree.append(node);
		});

		databases.forEach(function(db)
		{
			var children = Node.div({className: 'tree-children collapsed'});
			var chevron = Node.span({className: 'icon chevron', click: function(e)
			{
				e.stopPropagation();
				toggleNode(node, function() { expandDatabaseNode(node, db.id); });
			}}, 'chevron_right');
			var item = Node.div({className: 'tree-item', click: function()
			{
				selectTreeItem(this);
				currentDrive = {type: 'database', id: db.id, name: db.name};
				currentPath = '';
				currentTable = null;
				if( children.classList.contains('collapsed') )
					expandDatabaseNode(node, db.id);
				navigateDatabase(db.id, null);
			}}, [chevron, Node.span({className: 'icon'}, 'database'), Node.span(safeHtml(db.name))]);
			var node = Node.div({className: 'tree-node', dataset: {type: 'database', id: db.id}}, [item, children]);
			tree.append(node);
		});
	}

	function expandStorageNode(node, storageId, path)
	{
		var children = node.querySelector('.tree-children');
		var chevron = node.querySelector('.tree-item > .chevron');

		Ajax.get('/api/contributor/storage/' + encodeURIComponent(storageId) + '/tree', {data: {path: path}}).then(function(response)
		{
			while( children.firstChild ) children.firstChild.remove();
			var entries = response.response || [];
			entries.sort();

			entries.forEach(function(name)
			{
				if( !name.endsWith('/') ) return; // only folders in tree

				var cleanName = name.slice(0, -1);
				var fullPath = path ? path + '/' + cleanName : cleanName;

				var subChildren = Node.div({className: 'tree-children collapsed'});
				var subChevron = Node.span({className: 'icon chevron', click: function(e)
				{
					e.stopPropagation();
					toggleNode(subNode, function() { expandStorageNode(subNode, storageId, fullPath); });
				}}, 'chevron_right');
				var subItem = Node.div({className: 'tree-item', click: function(e)
				{
					e.stopPropagation();
					selectTreeItem(this);
					currentPath = fullPath;
					if( subChildren.classList.contains('collapsed') )
						expandStorageNode(subNode, storageId, fullPath);
					navigateStorage(storageId, fullPath);
				}}, [subChevron, Node.span({className: 'icon'}, 'folder'), Node.span(safeHtml(cleanName))]);
				var subNode = Node.div({className: 'tree-node', dataset: {path: fullPath}}, [subItem, subChildren]);
				children.append(subNode);
			});

			if( children.childNodes.length > 0 )
			{
				children.classList.remove('collapsed');
				if( chevron ) chevron.classList.add('open');
			}
			else
			{
				if( chevron ) chevron.style.visibility = 'hidden';
			}
		}, function(error)
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.tree.error'));
		});
	}

	function expandDatabaseNode(node, databaseId)
	{
		var children = node.querySelector('.tree-children');
		var chevron = node.querySelector('.tree-item > .chevron');

		Ajax.get('/api/contributor/database/' + encodeURIComponent(databaseId) + '/tables').then(function(response)
		{
			var tables = response.response || [];
			while( children.firstChild ) children.firstChild.remove();

			tables.forEach(function(t)
			{
				var tableItem = Node.div({className: 'tree-item', click: function(e)
				{
					e.stopPropagation();
					selectTreeItem(this);
					currentTable = t.name;
					queryState = {orderBy: null, orderDir: 'ASC'};
					navigateDatabase(databaseId, t.name);
				}}, [Node.span({className: 'chevron'}), Node.span({className: 'icon'}, 'grid_on'), Node.span(safeHtml(t.name))]);
				children.append(Node.div({className: 'tree-node'}, [tableItem]));
			});

			children.classList.remove('collapsed');
			if( chevron ) chevron.classList.add('open');
		}, function(error)
		{
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.schema.error'));
		});
	}

	function selectTreeItem(item)
	{
		tree.querySelectorAll('.tree-item.selected').forEach(function(el) { el.classList.remove('selected'); });
		item.classList.add('selected');
	}

	function toggleNode(node, expandFn)
	{
		var children = node.querySelector('.tree-children');
		var chevron = node.querySelector('.tree-item > .chevron');
		if( children.childNodes.length > 0 && !children.classList.contains('collapsed') )
		{
			children.classList.add('collapsed');
			if( chevron ) chevron.classList.remove('open');
		}
		else
		{
			expandFn();
		}
	}

	// =========================================================
	// Storage navigation
	// =========================================================

	function navigateStorage(storageId, path)
	{
		if( !currentDrive || currentDrive.id !== storageId )
		{
			var name = '';
			storages.forEach(function(s) { if( s.id === storageId ) name = s.name; });
			currentDrive = {type: 'storage', id: storageId, name: name};
		}
		currentPath = path;
		currentTable = null;
		updateBreadcrumb();
		updateToolbarButtons();

		content.classList.add('wait');
		Ajax.get('/api/contributor/storage/' + encodeURIComponent(storageId) + '/tree', {data: {path: path}}).then(function(response)
		{
			content.classList.remove('wait');
			while( content.firstChild ) content.firstChild.remove();
			var entries = response.response || [];
			entries.sort();

			if( entries.length === 0 )
			{
				content.append(Node.div({className: 'explorer-empty'},
				[
					Node.span({className: 'icon'}, 'folder_open'),
					Node.p(Translator.get('explorer.empty')),
					Node.p(Translator.get('explorer.empty.drop'))
				]));
				return;
			}

			var grid = Node.div({className: 'file-grid'});
			entries.forEach(function(name)
			{
				var isDir = name.endsWith('/');
				var cleanName = isDir ? name.slice(0, -1) : name;
				var fullPath = path ? path + '/' + cleanName : cleanName;

				var item = Node.div({className: 'file-item'},
				[
					Node.span({className: 'icon'}, isDir ? 'folder' : 'description'),
					Node.span({className: 'name'}, safeHtml(cleanName))
				]);

				if( isDir )
				{
					item.addEventListener('click', function() { navigateStorage(storageId, fullPath); });
				}
				else
				{
					item.addEventListener('click', function() { downloadFile(storageId, fullPath); });
				}

				var del = Node.span({className: 'icon delete', click: function(e)
				{
					e.stopPropagation();
					deleteFile(storageId, fullPath);
				}}, 'delete');
				item.append(del);

				grid.append(item);
			});

			content.append(grid);
		}, function(error)
		{
			content.classList.remove('wait');
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.tree.error'));
		});
	}

	function downloadFile(storageId, path)
	{
		content.classList.add('wait');
		Ajax.get('/api/contributor/storage/' + encodeURIComponent(storageId) + '/file', {data: {path: path}, responseType: 'blob'}).then(function(response)
		{
			content.classList.remove('wait');
			var name = path.split('/').pop();
			var url = URL.createObjectURL(response.response);
			Node.a({href: url, download: name, target: '_blank'}).click();
			setTimeout(function() { URL.revokeObjectURL(url); }, 1000);
		}, function(error)
		{
			content.classList.remove('wait');
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.download.error'));
		});
	}

	function uploadFiles(storageId, path, fileList)
	{
		var promises = [];
		for( var i = 0; i < fileList.length; i++ )
		{
			var file = fileList[i];
			if( file.size > 50 * 1024 * 1024 )
			{
				Notify.error(Translator.get('explorer.upload.size'));
				continue;
			}

			var fd = new FormData();
			fd.append('path', (path ? path + '/' : '') + file.name);
			fd.append('file', file);

			promises.push(Ajax.post('/api/contributor/storage/' + encodeURIComponent(storageId) + '/file', {data: fd}));
		}

		if( promises.length === 0 ) return;

		content.classList.add('wait');
		Promise.all(promises.map(function(p) { return p.then(function() { return true; }, function() { return false; }); })).then(function(results)
		{
			content.classList.remove('wait');
			var ok = results.filter(function(r) { return r; }).length;
			var fail = results.length - ok;
			if( ok > 0 ) Notify.success(Translator.get('explorer.upload.success'));
			if( fail > 0 ) Notify.error(Translator.get('explorer.upload.error'));
			refresh();
		});
	}

	function deleteFile(storageId, path)
	{
		var name = path.split('/').pop();
		Modal.confirm(Translator.get('explorer.delete.confirm', name), [
			Translator.get('remove'),
			Translator.get('cancel')
		]).then(function(index)
		{
			if( index > 0 ) return;

			content.classList.add('wait');
			Ajax.delete('/api/contributor/storage/' + encodeURIComponent(storageId) + '/file', {data: {path: path}}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.delete.success'));
				refresh();
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.delete.error'));
			});
		}, function() {});
	}

	function newFolder()
	{
		if( !currentDrive || currentDrive.type !== 'storage' ) return;

		Modal.prompt(Translator.get('explorer.newfolder.prompt'),
			Node.input({type: 'text', placeholder: Translator.get('explorer.newfolder')})
		).then(function(form)
		{
			if( !form.value ) return;

			var folderPath = (currentPath ? currentPath + '/' : '') + form.value + '/.keep';
			var fd = new FormData();
			fd.append('path', folderPath);
			fd.append('file', new File([], '.keep'));

			content.classList.add('wait');
			Ajax.post('/api/contributor/storage/' + encodeURIComponent(currentDrive.id) + '/file', {data: fd}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.newfolder.success'));
				refresh();
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.newfolder.error'));
			});
		}, function() {});
	}

	function dropTable(databaseId, tableName)
	{
		Modal.confirm(Translator.get('explorer.delete.confirm', tableName), [
			Translator.get('remove'),
			Translator.get('cancel')
		]).then(function(index)
		{
			if( index > 0 ) return;

			content.classList.add('wait');
			Ajax.delete('/api/contributor/database/' + encodeURIComponent(databaseId) + '/table', {data: {table: tableName}}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.delete.success'));
				currentTable = null;
				// Rebuild tree for this database
				var dbNode = tree.querySelector('.tree-node[data-type="database"][data-id="' + databaseId + '"]');
				if( dbNode ) expandDatabaseNode(dbNode, databaseId);
				navigateDatabase(databaseId, null);
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.delete.error'));
			});
		}, function() {});
	}

	// =========================================================
	// Database navigation
	// =========================================================

	function navigateDatabase(databaseId, tableName)
	{
		if( !currentDrive || currentDrive.id !== databaseId )
		{
			var name = '';
			databases.forEach(function(d) { if( d.id === databaseId ) name = d.name; });
			currentDrive = {type: 'database', id: databaseId, name: name};
		}
		currentPath = '';
		currentTable = tableName;
		updateBreadcrumb();
		updateToolbarButtons();

		while( content.firstChild ) content.firstChild.remove();

		if( !tableName )
		{
			content.classList.add('wait');
			Ajax.get('/api/contributor/database/' + encodeURIComponent(databaseId) + '/tables').then(function(response)
			{
				content.classList.remove('wait');
				var tables = response.response || [];
				if( tables.length === 0 )
				{
					content.append(Node.div({className: 'explorer-empty'},
					[
						Node.span({className: 'icon'}, 'database'),
						Node.p(Translator.get('explorer.empty'))
					]));
					return;
				}

				var grid = Node.div({className: 'file-grid'});
				tables.forEach(function(t)
				{
					var tableItem = Node.div({className: 'file-item', click: function()
					{
						currentTable = t.name;
						queryState = {orderBy: null, orderDir: 'ASC'};
						navigateDatabase(databaseId, t.name);
					}},
					[
						Node.span({className: 'icon'}, 'grid_on'),
						Node.span({className: 'name'}, safeHtml(t.name))
					]);
					tableItem.append(Node.span({className: 'icon delete', click: function(e)
					{
						e.stopPropagation();
						dropTable(databaseId, t.name);
					}}, 'delete'));
					grid.append(tableItem);
				});
				content.append(grid);
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.schema.error'));
			});
		}
		else
		{
			loadTableData(databaseId, tableName);
		}
	}

	function loadTableData(databaseId, tableName)
	{
		while( content.firstChild ) content.firstChild.remove();
		content.classList.add('wait');

		Ajax.get('/api/contributor/database/' + encodeURIComponent(databaseId) + '/columns', {data: {table: tableName}}).then(function(response)
		{
			content.classList.remove('wait');
			var columns = response.response || [];
			buildTableView(databaseId, tableName, columns);
		}, function(error)
		{
			content.classList.remove('wait');
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.schema.error'));
		});
	}

	function buildTableView(databaseId, tableName, columns)
	{
		var primaryColumns = columns.filter(function(c) { return c.primary; });

		// Build header row
		var headerCells = columns.map(function(col)
		{
			var sortIcon = Node.span({className: 'icon sort-icon'});
			if( queryState.orderBy === col.name )
				sortIcon.textContent = queryState.orderDir === 'ASC' ? 'arrow_upward' : 'arrow_downward';

			return Node.th({click: function()
			{
				if( queryState.orderBy === col.name )
					queryState.orderDir = queryState.orderDir === 'ASC' ? 'DESC' : 'ASC';
				else
				{
					queryState.orderBy = col.name;
					queryState.orderDir = 'ASC';
				}
				fetchData(databaseId, tableName);
			}}, [Node.span(safeHtml(col.name)), sortIcon]);
		});
		headerCells.push(Node.th());

		// Build filter row
		var numericTypes = ['INTEGER', 'BIGINT', 'SMALLINT', 'TINYINT', 'REAL', 'FLOAT', 'DOUBLE', 'DECIMAL', 'NUMERIC'];
		var filterInputs = [];
		var filterCells = columns.map(function(col)
		{
			var isNumeric = numericTypes.indexOf((col.type || '').toUpperCase()) >= 0;
			var input = Node.input({type: 'text', placeholder: col.type || Translator.get('explorer.filter.placeholder')});
			filterInputs.push({input: input, column: col.name, numeric: isNumeric});

			input.addEventListener('keydown', function(e)
			{
				if( e.key === 'Enter' )
				{
					queryState.offset = 0;
					fetchData(databaseId, tableName);
				}
			});

			return Node.th({}, [input]);
		});
		filterCells.push(Node.th());

		var thead = Node.thead({},
		[
			Node.tr({}, headerCells),
			Node.tr({className: 'filter-row'}, filterCells)
		]);
		var tbody = Node.tbody();
		var table = Node.table({className: 'data-grid'}, [thead, tbody]);

		content.append(table);

		if( primaryColumns.length === 0 )
			content.append(Node.p({className: 'nopk-hint'}, Translator.get('explorer.nopk')));

		// Execute initial query
		fetchData(databaseId, tableName);

		function renderRows(rows)
		{
			while( tbody.firstChild ) tbody.firstChild.remove();

			rows.forEach(function(row)
			{
				var cells = columns.map(function(col)
				{
					var val = row[col.name];
					return Node.td({}, [Node.span(val === null || val === undefined ? '' : safeHtml(String(val)))]);
				});
				var hasPK = primaryColumns.length > 0;
				cells.push(Node.td({className: 'row-actions'}, [
					Node.span({className: 'icon' + (hasPK ? '' : ' disabled'), click: function() { if( hasPK ) editRow(databaseId, tableName, columns, row); }}, 'edit'),
					Node.span({className: 'icon' + (hasPK ? '' : ' disabled'), click: function() { if( hasPK ) deleteRow(databaseId, tableName, columns, row); }}, 'delete')
				]));
				tbody.append(Node.tr({}, cells));
			});

			// Update sort icons
			thead.querySelectorAll('tr:first-child th .sort-icon').forEach(function(icon, idx)
			{
				if( columns[idx] && queryState.orderBy === columns[idx].name )
					icon.textContent = queryState.orderDir === 'ASC' ? 'arrow_upward' : 'arrow_downward';
				else
					icon.textContent = '';
			});
		}

		function fetchData(dbId, tblName)
		{
			var filters = [];
			filterInputs.forEach(function(f)
			{
				var val = f.input.value.trim();
				if( !val ) return;

				var op = '=';
				if( val.indexOf('>=') === 0 ) { op = '>='; val = val.substring(2).trim(); }
				else if( val.indexOf('<=') === 0 ) { op = '<='; val = val.substring(2).trim(); }
				else if( val.indexOf('!=') === 0 ) { op = '!='; val = val.substring(2).trim(); }
				else if( val.indexOf('>') === 0 ) { op = '>'; val = val.substring(1).trim(); }
				else if( val.indexOf('<') === 0 ) { op = '<'; val = val.substring(1).trim(); }
				else if( val.indexOf('%') >= 0 ) { op = 'LIKE'; }
				else if( !f.numeric ) { op = 'LIKE'; val = '%' + val + '%'; }

				if( val ) filters.push({column: f.column, op: op, value: val});
			});

			content.classList.add('wait');
			Ajax.post('/api/contributor/database/' + encodeURIComponent(dbId) + '/query', {data:
			{
				table: tblName,
				filters: JSON.stringify(filters),
				orderBy: queryState.orderBy || '',
				orderDir: queryState.orderDir
			}}).then(function(response)
			{
				content.classList.remove('wait');
				renderRows(response.response.rows || []);
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.query.error'));
			});
		}
	}

	// =========================================================
	// Row operations
	// =========================================================

	function showRowForm(columns, row, title, callback)
	{
		var fields = columns.map(function(col)
		{
			var val = row ? (row[col.name] === null || row[col.name] === undefined ? '' : String(row[col.name])) : '';
			var hint = col.type + (col.primary ? ', PK' : '') + (col.auto ? ', auto' : '') + (col['null'] ? ', nullable' : '');
			return Node.fieldset([
				Node.label(safeHtml(col.name) + ' '),
				Node.span({style: {fontSize: '0.8rem', color: '#0006'}}, hint),
				Node.input({type: 'text', name: col.name, value: val, placeholder: col.type || ''})
			]);
		});

		Modal.prompt(
			Node.h2(title),
			Node.form({className: 'row-form'}, fields)
		).then(function(form)
		{
			var values = {};
			columns.forEach(function(col)
			{
				var input = form.elements[col.name];
				if( input && input.value !== '' ) values[col.name] = input.value;
				else if( input && input.value === '' && col['null'] ) values[col.name] = null;
			});
			callback(values);
		}, function() {});
	}

	function addRow(databaseId, tableName)
	{
		content.classList.add('wait');
		Ajax.get('/api/contributor/database/' + encodeURIComponent(databaseId) + '/columns', {data: {table: tableName}}).then(function(response)
		{
			content.classList.remove('wait');
			var columns = response.response || [];

			showRowForm(columns, null, Translator.get('explorer.addrow'), function(values)
		{
			content.classList.add('wait');
			Ajax.post('/api/contributor/database/' + encodeURIComponent(databaseId) + '/row', {data:
			{
				table: tableName,
				values: JSON.stringify(values)
			}}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.addrow.success'));
				refresh();
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.addrow.error'));
			});
		});
		}, function(error)
		{
			content.classList.remove('wait');
			if( error.response && error.response.error && error.response.error.message )
				Notify.error(safeHtml(error.response.error.message));
			else
				Notify.error(Translator.get('explorer.schema.error'));
		});
	}

	function editRow(databaseId, tableName, columns, row)
	{
		var primaryColumns = columns.filter(function(c) { return c.primary; });

		showRowForm(columns, row, Translator.get('explorer.editrow'), function(values)
		{
			var keys = {};
			primaryColumns.forEach(function(col) { keys[col.name] = row[col.name]; });

			content.classList.add('wait');
			Ajax.put('/api/contributor/database/' + encodeURIComponent(databaseId) + '/row', {data:
			{
				table: tableName,
				values: JSON.stringify(values),
				keys: JSON.stringify(keys)
			}}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.editrow.success'));
				refresh();
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.editrow.error'));
			});
		});
	}

	function deleteRow(databaseId, tableName, columns, row)
	{
		var primaryColumns = columns.filter(function(c) { return c.primary; });
		var keys = {};
		primaryColumns.forEach(function(col) { keys[col.name] = row[col.name]; });

		var keyDisplay = primaryColumns.map(function(col) { return col.name + '=' + row[col.name]; }).join(', ');
		Modal.confirm(Translator.get('explorer.delete.confirm', keyDisplay), [
			Translator.get('remove'),
			Translator.get('cancel')
		]).then(function(index)
		{
			if( index > 0 ) return;

			content.classList.add('wait');
			Ajax.delete('/api/contributor/database/' + encodeURIComponent(databaseId) + '/row', {data:
			{
				table: tableName,
				keys: JSON.stringify(keys)
			}}).then(function()
			{
				content.classList.remove('wait');
				Notify.success(Translator.get('explorer.delete.success'));
				refresh();
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Notify.error(safeHtml(error.response.error.message));
				else
					Notify.error(Translator.get('explorer.delete.error'));
			});
		}, function() {});
	}

	// =========================================================
	// SQL dialog
	// =========================================================

	function showSqlDialog(databaseId)
	{
		Modal.prompt(
			Translator.get('explorer.sql.title'),
			Node.textarea({className: 'sql-input', placeholder: 'SELECT * FROM ...'})
		).then(function(form)
		{
			var sql = (form.value || '').trim();
			if( !sql ) return;

			content.classList.add('wait');
			Ajax.post('/api/contributor/database/' + encodeURIComponent(databaseId) + '/execute', {data: {sql: sql}}).then(function(response)
			{
				content.classList.remove('wait');

				if( response.response.error )
				{
					Modal.alert(Node.pre({className: 'sql-error'}, safeHtml(response.response.error)));
					return;
				}

				var result = response.response.result;
				if( Array.isArray(result) && result.length > 0 )
				{
					Modal.alert(Node.pre(safeHtml(JSON.stringify(result, null, 2))));
				}
				else if( Array.isArray(result) && result.length === 0 )
				{
					Notify.success(Translator.get('explorer.sql.rows', 0));
				}
				else
				{
					Notify.success(Translator.get('explorer.sql.success'));
				}

				// Always refresh tree after SQL execution (tables may have changed)
				var dbNode = tree.querySelector('.tree-node[data-type="database"][data-id="' + databaseId + '"]');
				if( dbNode ) expandDatabaseNode(dbNode, databaseId);
			}, function(error)
			{
				content.classList.remove('wait');
				if( error.response && error.response.error && error.response.error.message )
					Modal.alert(Node.pre({className: 'sql-error'}, safeHtml(error.response.error.message)));
				else
					Modal.alert(Node.pre({className: 'sql-error'}, Translator.get('explorer.sql.error')));
			});
		}, function() {});
	}

	// =========================================================
	// Breadcrumb
	// =========================================================

	function updateBreadcrumb()
	{
		while( breadcrumb.firstChild ) breadcrumb.firstChild.remove();

		if( !currentDrive )
		{
			breadcrumb.append(Node.span('Explorer'));
			return;
		}

		breadcrumb.append(Node.span({click: function()
		{
			if( currentDrive.type === 'storage' )
			{
				currentPath = '';
				navigateStorage(currentDrive.id, '');
			}
			else
			{
				currentTable = null;
				navigateDatabase(currentDrive.id, null);
			}
		}}, safeHtml(currentDrive.name)));

		if( currentDrive.type === 'storage' && currentPath )
		{
			var parts = currentPath.split('/');
			for( var i = 0; i < parts.length; i++ )
			{
				breadcrumb.append(Node.span({className: 'separator'}, '/'));
				(function(idx)
				{
					var subPath = parts.slice(0, idx + 1).join('/');
					var isLast = idx === parts.length - 1;
					breadcrumb.append(Node.span(isLast ? {} : {click: function()
					{
						currentPath = subPath;
						navigateStorage(currentDrive.id, subPath);
					}}, safeHtml(parts[idx])));
				})(i);
			}
		}
		else if( currentDrive.type === 'database' && currentTable )
		{
			breadcrumb.append(Node.span({className: 'separator'}, '/'));
			breadcrumb.append(Node.span(safeHtml(currentTable)));
		}
	}

	// =========================================================
	// Toolbar
	// =========================================================

	function updateToolbarButtons()
	{
		var isStorage = currentDrive && currentDrive.type === 'storage';
		var isDatabase = currentDrive && currentDrive.type === 'database';

		btnUpload.style.display = isStorage ? '' : 'none';
		btnNewFolder.style.display = isStorage ? '' : 'none';
		btnSql.style.display = isDatabase ? '' : 'none';
		btnAddRow.style.display = isDatabase && currentTable ? '' : 'none';
	}

	function refreshTree()
	{
		if( !currentDrive ) return;

		if( currentDrive.type === 'storage' )
		{
			// Find the tree node for currentPath and re-expand it
			var selector = '.tree-node[data-type="storage"][data-id="' + currentDrive.id + '"]';
			var driveNode = tree.querySelector(selector);
			if( !driveNode ) return;

			if( !currentPath )
			{
				expandStorageNode(driveNode, currentDrive.id, '');
			}
			else
			{
				// Find the node matching currentPath
				var pathNode = driveNode.querySelector('.tree-node[data-path="' + currentPath + '"]');
				if( pathNode )
					expandStorageNode(pathNode, currentDrive.id, currentPath);
				else
					expandStorageNode(driveNode, currentDrive.id, '');
			}
		}
		else if( currentDrive.type === 'database' )
		{
			var dbNode = tree.querySelector('.tree-node[data-type="database"][data-id="' + currentDrive.id + '"]');
			if( dbNode ) expandDatabaseNode(dbNode, currentDrive.id);
		}
	}

	function refresh()
	{
		if( !currentDrive ) return;

		if( currentDrive.type === 'storage' )
			navigateStorage(currentDrive.id, currentPath);
		else if( currentDrive.type === 'database' )
			navigateDatabase(currentDrive.id, currentTable);

		refreshTree();
	}
}
