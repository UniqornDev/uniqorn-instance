
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Node', 'ae.notify.css').then(([Node]) =>
	{
		class Notify
		{
			static info(message) { Notify.show(message, 'info'); }
			static warning(message) { Notify.show(message, 'warning'); }
			static success(message) { Notify.show(message, 'success'); }
			static error(message) { Notify.show(message, 'error'); }
			
			static show(message, level)
			{
				if( !message ) return;
				if( Notify.dom.children.length > 3 ) { console.warn(message); return; }
				
				var timeout;
				var li = Notify.dom.appendChild(Node.li({
					className: level||'none',
					click: function() { this.classList.remove('show'); var self = this; setTimeout(() => { self.remove(); }, 400); },
					mouseover: function() { clearTimeout(timeout); },
					mouseout: function() { var self = this; timeout = setTimeout(() => { self.classList.remove('show'); setTimeout(() => { self.remove(); }, 400); }, 3000); }
					}, ae.safeHtml(message)));
				window.requestAnimationFrame(function() { li.classList.add('show'); });
				li.dispatchEvent(new Event('mouseout'));
			}
		}
		
		Notify.dom = document.body.appendChild(Node.ol({id: 'notifyArea'}));
		
		ok(Notify);
	}, (e) => { nok(e); });
});

export { x as default };