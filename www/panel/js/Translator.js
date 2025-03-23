
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Cookie').then(({0: Cookie}) =>
	{
		class Translator
		{
			static get locale()
			{
				return (Cookie.get('locale') || navigator.language || navigator.userLanguage || 'en').substring(0,2).toLowerCase();
			}
			
			static load(name)
			{
				return new Promise((ok, nok) =>
				{
					if( !Array.isArray(name) ) name = [name];
					var files = []
					for( var i = 0; i < name.length; i++ ) files[i] = 'locale/' + Translator.locale + '/' + name[i];
					
					ae.require(files).then((t) =>{ t.forEach((x) => { Translator.append(x); }); ok(); }, () =>
					{
						files = [];
						for( var i = 0; i < name.length; i++ ) files[i] = 'locale/en/' + name[i];
						ae.require(files).then((t) => { t.forEach((x) => { Translator.append(x); }); ok(); }, (e) => { nok(e); });
					});
				});
			}
			
			static append(translations)
			{
				Translator.cache = Object.assign(Translator.cache, translations);
			}
			
			static get(key)
			{
				if( !Translator.cache[key] )
				{
					console.warn("Undefined translation of " + key);
					return '';
				}
				let i = 0, a = arguments;
				return Translator.cache[key].replace(/\{\}/g, function() { var v = a[++i]; return (typeof v != 'undefined'&&v!==null?v:''); });
			}
			
			static change(locale)
			{
				Cookie.set('language', (locale || 'en').substring(0,2).toLowerCase());
				location.reload(true);
			}
		}

		Translator.cache = {};
		
		ok(Translator);
	}, (e) => { nok(e); });
});

export { x as default };