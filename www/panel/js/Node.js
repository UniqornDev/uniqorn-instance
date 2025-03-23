
class Node
{
	static p(a, c) { 		return Node.create('p', a, c); }
	static div(a, c) { 		return Node.create('div', a, c); }
	static span(a, c) { 	return Node.create('span', a, c); }
	static h1(a, c) { 		return Node.create('h1', a, c); }
	static h2(a, c) { 		return Node.create('h2', a, c); }
	static h3(a, c) { 		return Node.create('h3', a, c); }
	static h4(a, c) { 		return Node.create('h4', a, c); }
	static main(a, c) { 	return Node.create('main', a, c); }
	static header(a, c) { 	return Node.create('header', a, c); }
	static footer(a, c) { 	return Node.create('footer', a, c); }
	static section(a, c) { 	return Node.create('section', a, c); }
	static nav(a, c) { 		return Node.create('nav', a, c); }
	static em(a, c) { 		return Node.create('em', a, c); }
	static strong(a, c) { 	return Node.create('strong', a, c); }
	static br(a, c) { 		return Node.create('br', a, c); }
	static img(a, c) { 		return Node.create('img', a, c); }
	static a(a, c) { 		return Node.create('a', a, c); }
	static hr(a, c) { 		return Node.create('hr', a, c); }
	static ol(a, c) { 		return Node.create('ol', a, c); }
	static ul(a, c) { 		return Node.create('ul', a, c); }
	static li(a, c) { 		return Node.create('li', a, c); }
	static aside(a, c) { 	return Node.create('aside', a, c); }
	static canvas(a, c) { 	return Node.create('canvas', a, c); }
	
	static table(a, c) { 	return Node.create('table', a, c); }
	static thead(a, c) { 	return Node.create('thead', a, c); }
	static tbody(a, c) { 	return Node.create('tbody', a, c); }
	static tr(a, c) { 		return Node.create('tr', a, c); }
	static td(a, c) { 		return Node.create('td', a, c); }
	static th(a, c) { 		return Node.create('th', a, c); }
	
	static form(a, c) { 	return Node.create('form', a, c); }
	static fieldset(a, c) { return Node.create('fieldset', a, c); }
	static input(a, c) { 	return Node.create('input', a, c); }
	static select(a, c) { 	return Node.create('select', a, c); }
	static option(a, c) { 	return Node.create('option', a, c); }
	static textarea(a, c) { return Node.create('textarea', a, c); }
	static radio(a, c) { 	return Node.create('radio', a, c); }
	static checkbox(a, c) { return Node.create('checkbox', a, c); }
	static button(a, c) { 	return Node.create('button', a, c); }
	static label(a, c) { 	return Node.create('label', a, c); }
	static pre(a, c) { 		return Node.create('pre', a, c); }
	static code(a, c) { 	return Node.create('code', a, c); }
	
	static svg(a, c) { 		return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'svg'), a, c); }
	static circle(a, c) { 	return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'circle'), a, c); }
	static path(a, c) { 	return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'path'), a, c); }
	static defs(a, c) { 	return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'defs'), a, c); }
	static linearGradient(a, c) { return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'linearGradient'), a, c); }
	static stop(a, c) { 	return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'stop'), a, c); }
	static text(a, c) { 	return Node.create(document.createElementNS('http://www.w3.org/2000/svg', 'text'), a, c); }
	
	static create(tag, attributes, content)
	{
		var n = tag instanceof HTMLElement || tag instanceof SVGElement ? tag : document.createElement(tag);
		
		// reorder attributes and content
		if( typeof attributes == 'string' || attributes instanceof HTMLElement || Array.isArray(attributes) )
		{
			var c = content;
			content = attributes;
			attributes = c; 
		}
		
		if( content )
			Node.append(n, content);
		
		if( attributes )
		{
			Object.keys(attributes).forEach((key) =>
			{
				if( typeof attributes[key] == 'function' )
					n.addEventListener(key, attributes[key]);
				else if( key == 'dataset' )
					Object.keys(attributes.dataset).forEach((k) => { n.dataset[k] = attributes.dataset[k]; });
				else if( key == 'style' )
					Object.keys(attributes.style).forEach((k) => { if( k.startsWith('--') ) n.style.setProperty(k, attributes.style[k]); else n.style[k] = attributes.style[k]; });
				else
				{
					if( n instanceof SVGElement ) n.setAttribute(key, attributes[key]);
					else n[key] = attributes[key];
				}
			});
		}
		
		return n;
	}
	
	static append(node, content)
	{
		if( typeof content == 'string' )
			node.innerHTML = content;
		else
		{
			if( !Array.isArray(content) ) content = [content];
			for( var i = 0; i < content.length; i++ )
			{
				if( Array.isArray(content[i]) )
				{
					content.splice.apply(content, [i,1].concat(content[i]));
					i--;
				}
			}
			
			for( const c of content)
			{
				if( c === null ) continue;
				if( typeof c === "string")
				{
					const temp = document.createElement("template");
					temp.innerHTML = c;
					node.append(...temp.content.childNodes);
				}
				else
					node.append(c);
			}
		}
		
		return node;
	}
}

export { Node as default };