
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Translator', 'Ajax', 'Notify').then(([Page, Node, Translator, Ajax, Notify]) =>
	{
		Translator.load('default').then(() =>
		{
			ok(Object.assign(new Page(), 
			{
				show: function()
				{
					this.dom.append(Node.p("hello"));
					return Promise.resolve();
				},
				
				hide: function()
				{
					return Promise.resolve(); 
				}
			}));
		});
	}, (e) => { nok(e); });
});

export { x as default };