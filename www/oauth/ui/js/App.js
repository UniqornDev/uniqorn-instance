
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Notify', 'ae.app.css').then(([Page, Notify]) =>
	{
		function template()
		{
			ae.require('pages/template').then(([template]) =>
			{
				template.show().then(() =>
				{
					App.navigate(location.hash || '#');
				}, (e) =>
				{
					// template failed to show
					console.warn(e);
					App.navigate(location.hash || '#');
				});
			}, () =>
			{
				// no template
				App.navigate(location.hash || '#');
			});
		};

		function login()
		{
			ae.require('pages/login').then(([login]) => 
			{
				login.show().then(() =>
				{
					template();
				}, (e) =>
				{
					// login failed
					console.warn(e);
				});
			}, (e) =>
			{
				console.warn(e);
				// no login
				template();
			});
		};

		class App
		{
			static setup()
			{
				window.addEventListener('hashchange', function(e) { App.navigate(location.hash || '#'); });
				login();
			}
			
			static navigate(hash)
			{
				var name = (/^#([^/]*)/.exec(hash||'#')||['','home'])[1]||'home';
				(App.current ? App.current.hide() : Promise.resolve(null)).then(() =>
				{
					if( App.current ) App.current.dom.remove();
					
					App.container.classList.add('wait');
					ae.require('pages/' + name).then(([page]) =>
					{
						App.current = page;
						page.show().then(() => 
						{
							App.container.classList.remove('wait');	
							App.container.appendChild(page.dom);
						}, (e) => { Notify.error('' + e); });
					}, (e) =>
					{
						Notify.warning('Page "' + name + '" not found');
						console.log(e);
					});
				}, (e) => { Notify.error('' + e); });
			}
		}
		
		App.current = null;
		App.container = document.body;
		
		App.setup();
		ok(App);
	}, (e) => { nok(e); });
});

export { x as default };