
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page').then(([Page]) =>
	{
		ok(Object.assign(new Page(), 
		{
			show: function()
			{
				return Promise.resolve(null);
			}
		}));
	}, (e) => { nok(e); });
});

export { x as default };