
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Node').then(([Node]) =>
	{
		class Page
		{
			get dom() { if( !this._dom ) this._dom = Node.section({className: 'page'}); return this._dom; }
			show() { return Promise.resolve(null); }
			hide() { return Promise.resolve(null); }
		}
		
		ok(Page);
	}, (e) => { nok(e); });
});

export { x as default };