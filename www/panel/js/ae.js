
let now = new Date().getTime();
let cssImport = function(url)
{
	for( const s of document.styleSheets )
		if( s.ownerNode.url == url )
			return Promise.resolve(s.ownerNode);
	
	return new Promise((ok, nok) =>
	{
		let node = document.createElement('link');
		node.type = 'text/css';
		node.rel = 'stylesheet';
		node.url = url;
		node.addEventListener('load', function() { ok(node); });
		node.addEventListener('error', function(e) { nok(e); });
		node.href = url;
		document.head.appendChild(node);
	});
};

document.addEventListener('keyup', function(e)
{
	if( e.key == "Enter" )
	{
		var ev = new Event('enter', {cancelable: true});
		ev.origin = e;
		this.dispatchEvent(ev);
	}
	else if( e.key == "Escape" )
	{
		var ev = new Event('escape', {cancelable: true});
		ev.origin = e;
		this.dispatchEvent(ev);
	}
	else if( e.key == "Delete" )
	{
		var ev = new Event('delete', {cancelable: true});
		ev.origin = e;
		this.dispatchEvent(ev);
	}
});

if( typeof Array.prototype.last == 'undefined' ) { Object.defineProperty(Array.prototype, 'last', { get: function() { return (this.length > 0 ? this[this.length-1] : null); } }); }

const ae = {
	ready: new Promise((ok, nok) =>
	{
		if( document.readyState == 'interactive' || document.readyState == 'complete' )
			ok();
		else
			document.addEventListener('DOMContentLoaded', ok, {once: true}); 
	}),
	require: function(...modules)
	{
		if( modules.length == 0 && Array.isArray(modules[0]) ) modules = modules[0];
		
		for( var i = 0; i < modules.length; i++ )
		{
			if( /\.css$/.test(modules[i]) )
			{
				if( !/^https?:\/\//.test(modules[i]) && !/^css\//.test(modules[i]) )
					modules[i] = "css/" + modules[i];
				modules[i] = cssImport(modules[i] + "?v=_" + now);
			}
			else
			{
				if( !/^https?:\/\//.test(modules[i]) && !/^\.*\//.test(modules[i]) )
					modules[i] = "./" + modules[i];
				if( !/\.js$/.test(modules[i]) )
					modules[i] = modules[i] + ".js";
				modules[i] = new Promise((ok, nok) => { import(modules[i] + "?v=_" + now).then((v) => 
				{ 
					if( v.default instanceof Promise )
						v.default.then((w) => { ok({default: w}); }, (e) => { nok(e); });
					else
						ok(v);
				}, (e) => { nok(e); }); });
			}
		}
		
		return new Promise((ok, nok) =>
		{
			Promise.all(modules).then((values) =>
			{
				let defaults = [];
				for( var i = 0; i < values.length; i++ )
					defaults.push(values[i].default);
				ok(defaults);
			},
			(error) =>
			{
				nok(error);
			});
		});
	},
	safeHtml: function(text)
	{
		if( !text ) return '';
		if( typeof text !== 'string' ) text = '' + text;
		
		return text.replace(/&/g, '&amp;')
			.replace(/</g, '&lt;')
			.replace(/>/g, '&gt;')
			.replace(/"/g, '&quot;');
	},
	urlValue: function(key)
	{
		var value = new URLSearchParams(location.search).get(key);
		return value || new URLSearchParams((/\?(.*)/.exec(location.hash||'')||['',''])[1]||'').get(key);
	}
}

globalThis.ae = ae;
export { ae as default };
