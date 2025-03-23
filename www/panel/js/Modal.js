
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Node', 'Translator', 'ae.modal.css').then(([Node, Translator]) =>
	{
		class Modal
		{
			static alert(message)
			{
				var p = Modal.custom(
				[
					Node.p(message),
					Node.button({click: function(e)
					{
						e.preventDefault();
						p.ok(0);
					}}, Translator.get('ok'))
				], false);
				
				p.dom.classList.add('promptable');
				return p;
			}
			
			static confirm(message, buttons, escapable)
			{
				if( !buttons || !Array.isArray(buttons) || buttons.length == 0 )
					buttons = [Translator.get('ok'), Translator.get('cancel')];
				
				var p;
				var nodes = [ Node.p(message) ];
				var l = buttons.length - 1;
				// add buttons in reverse order
				buttons.slice().reverse().forEach((b, i) =>
				{
					nodes.push( Node.button({click: function(e) { e.preventDefault(); p.ok(l - i); }}, typeof b == "string" ? ae.safeHtml(b) : b) );
				});
				
				p = Modal.custom(nodes, escapable||false);
				p.dom.classList.add('promptable');
				return p;
			}
			
			static prompt(message, form)
			{
				var p;
				if( !form || !(form instanceof HTMLElement) )
					form = Node.form(Node.input({type: 'text', name: 'value', value: ""+(form||""), keydown: function(e) { if( e.keyCode === 13 ) { p.ok(form); e.stopImmediatePropagation(); e.preventDefault(); return false; } }}));
				
				p = Modal.custom(
				[
					Node.p(message),
					form,
					Node.button({click: function(e) { e.preventDefault(); try { p.nok("canceled"); } catch(x) { } }}, Translator.get('cancel')),
					Node.button({click: function(e) { e.preventDefault(); p.ok(form); }}, Translator.get('ok'))
				], true);
				
				var escHandler = document.addEventListener("escape", function() { p.nok('escaped') });
				var enterHandler = form.addEventListener("submit", function(e)
				{
					e.preventDefault(); 
					e.stopImmediatePropagation();
					p.ok(form);
					return false;
				});
				
				p.finally(() =>
				{
					document.removeEventListener('escape', escHandler);
					form.removeEventListener('submit', enterHandler);
				}).catch(() => {});
				
				p.dom.classList.add('promptable');
				return p;
			}
			
			/**
			 * Returns a Promise with the following properties added :
			 *    - ok : the original resolve function
			 *    - nok : the original reject function
			 *    - dom : the top-level dom node
			 */
			static custom(nodes, escapable)
			{
				var ok, nok;
				var p  = new Promise((_ok, _nok) => { ok = _ok; nok = _nok; });
				p.ok = ok;
				p.nok = nok;
				p.dom = Node.div({className: 'modal'}, Node.div(nodes));
				
				if( escapable )
				{
					p.dom.firstChild.appendChild(Node.aside({className: 'close', click: function() { p.nok("closed"); }}, "&times;"));
					p._handler_1 = function(){ var n = document.querySelectorAll('body > div.modal'); if( n && p.dom == n[n.length-1] ) p.nok("escaped"); };
					p._handler_2 = function(e) { if( e.target !== p.dom ) return; p._handler_1(); };
					document.addEventListener('escape', p._handler_1);
					document.body.addEventListener('click', p._handler_2);
				}
				
				document.body.appendChild(p.dom);
				
				p.finally(() =>
				{
					p.dom.remove();
					if( p._handler_1 ) document.removeEventListener('escape', p._handler_1);
					if( p._handler_2 ) document.body.removeEventListener('click', p._handler_2);
				}).catch(() => {});
				
				return p;
			}
		}
		
		ok(Modal);
	}, (e) => { nok(e); });
});

export { x as default };