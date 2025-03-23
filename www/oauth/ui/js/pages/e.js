
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Page', 'Node', 'Translator', 'page.e.css').then(([Page, Node, Translator]) =>
	{
		Translator.load('default').then(() =>
		{
			ok(Object.assign(new Page(), 
			{
				show: function()
				{
					const error = new URLSearchParams(window.location.search).get('error');
					const description = new URLSearchParams(window.location.search).get('error_description');
					
					this.dom.classList.add('error');
					this.dom.appendChild(
						Node.div({id: 'error_panel'}, [
							Node.p({className: 't1'}, Translator.get('error.title')),
							Node.p({className: 't2'}, Translator.get('error.class.' + error)),
							Node.p({className: 't3'}, ae.safeHtml(description))
						])
					);
					return Promise.resolve();
				}
			}));
		});
	}, (e) => { nok(e); });
});

export { x as default };