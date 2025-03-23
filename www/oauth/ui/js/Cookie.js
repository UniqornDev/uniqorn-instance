
class Cookie
{
	static get(name)
	{
		let match;
		if( (match = new RegExp("(?:^|;)\\s*" + encodeURIComponent(name) + "\\s*=\\s*([^\\s;]*)").exec(document.cookie)) )
			return decodeURIComponent(match[1]);
		return null;
	}
	
	static set(name, value, path)
	{
		document.cookie = encodeURIComponent(name) + '=' + encodeURIComponent(value) + 
			';domain=' + location.hostname +
			';SameSite=Lax' +
			';max-age=31536000;expires=' + new Date(Date.now() + 31536000000).toUTCString() + 
			';path=' + (path ? path : '/' + location.pathname.split('/')[1]);
	}
	
	static unset(name, path)
	{
		document.cookie = encodeURIComponent(name) + '=' + 
			';domain=' + location.hostname +
			';SameSite=Lax' +
			';max-age=0;expires=' + new Date(0).toUTCString() + 
			';path=' + (path ? path : '/' + location.pathname.split('/')[1]);
	}
}

export { Cookie as default };