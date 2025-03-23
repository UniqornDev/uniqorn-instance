
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('App', 'Page', 'Node').then(([App, Page, Node]) =>
	{
		ok(Object.assign(new Page(), 
		{
			show: function()
			{
				var container = Node.main({id: "main_container"});
				
				document.body.append(
					container
				);
				
				App.container = container;
				return Promise.resolve(null);
			}
		}));
	}, (e) => { nok(e); });
});

export { x as default };