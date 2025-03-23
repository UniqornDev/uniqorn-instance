
let ae = globalThis.ae;
var x = new Promise((ok, nok) =>
{
	ae.require('Cookie').then(([Cookie]) =>
	{
		class Ajax
		{
			static post(url, options) { if( !options ) options = {}; options.method = 'POST'; return Ajax.fetch(url, options); }
			static get(url, options) { if( !options ) options = {}; options.method = 'GET'; return Ajax.fetch(url, options); }
			static fetch(url, options)
			{
				if( !url || !options ) throw new RangeError('Invalid url or options for Ajax request');
				return new Promise((ok, nok) =>
				{
					let x = new XMLHttpRequest();
					if( options.timeout ) x.timeout = options.timeout;
					if( options.withCredentials ) x.withCredentials = options.withCredentials;
					if( options.mimeType ) x.overrideMimeType(options.overrideMimeType);
					if( options.responseType ) x.responseType = options.responseType;
					if( !options.data ) options.data = {};
					if( options.method == 'GET' && options.noCache ) options.data.v = '_' + Date.now();
					if( !options.headers ) options.headers = {};
					if( !options.headers.hasOwnProperty('Authorization') && Ajax.authorization ) options.headers.Authorization = Ajax.authorization;
					if( !options.headers.hasOwnProperty('Authorization') && options.user && options.password ) options.headers.Authorization = "Basic " + btoa(unescape(encodeURIComponent(options.user + ":" + options.password)));
					if( options.headers.hasOwnProperty('Authorization') && !options.headers.Authorization ) delete options.headers.Authorization;
					
					x.addEventListener('error', function(e)
					{
						if( !e ) e = {};
						try { e.status = x.status; } catch(e) {}
						try { e.response = x.response; } catch(e) {}
						try
						{
							e.headers = {};
							x.getAllResponseHeaders().split("\r\n").forEach((line) =>
							{
								if( line )
								{
									let s = line.split(':');
									e.headers[s[0].trim()] = (s[1]||'').trim();
								}
							});
						} catch(e) {}
						
						nok(e);
					}, {once: true});
					
					x.addEventListener('timeout', function(e)
					{
						if( !e ) e = {};
						e.status = 504;
						e.response = null;
						e.headers = {};
						nok(e);
					}, {once: true});
					
					x.addEventListener('load', function(e)
					{
						if( !e ) e = {};
						try { e.status = x.status; } catch(e) {}
						try { e.rawResponse = x.response; } catch(e) {}
						try { e.response = JSON.parse(x.response); } catch(ex) { e.response = x.response; }
						try
						{
							e.headers = {};
							x.getAllResponseHeaders().split("\r\n").forEach((line) =>
							{
								if( line )
								{
									let s = line.split(':');
									e.headers[s[0].trim()] = (s[1]||'').trim();
								}
							});
						} catch(e) {}
						
						// check for auth issue
						if( e.status == 403 )
						{
							if( !/^(?:[a-z]+:)?\/\//i.test(url) && Ajax.authorization )
							{
								delete Ajax.authorization;
								Cookie.unset('token');
								location.reload();
								return;
							}
						}
						
						if( e.status >= 400 ) nok(e);
						else ok(e);
					}, {once: true});
					
					if( options.data && options.method == 'GET' )
					{
						let querystring = "";
						Object.keys(options.data).forEach((key) => { querystring += encodeURIComponent(key) + "=" + encodeURIComponent(options.data[key]) + "&"; });
						url += (url.includes('?') ? '&' : '?') + querystring.slice(0, -1);
					}
				
					x.open(options.method, url, true);
					Object.keys(options.headers).forEach((h) => { x.setRequestHeader(h, options.headers[h]); });
					
					if( options.method != 'GET' && !options.data ) x.send();
					else
					{
						if( options.data instanceof HTMLFormElement ) options.data = new FormData(options.data);
						if( typeof options.data != 'string' && !(options.data instanceof FormData) )
						{
							let data = new FormData();
							Object.keys(options.data).forEach((key) => { data.append(key, options.data[key]); });
							options.data = data;
						}
						x.send(options.data);
					}
				}, (e) => { nok(e); });
			}
		}
		
		ok(Ajax);
	}, (e) => { nok(e); });
});

export { x as default };