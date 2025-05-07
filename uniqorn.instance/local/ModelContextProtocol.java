package local;

import java.util.Map;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Message;
import aeonics.entity.Registry;
import aeonics.entity.security.User;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.http.Endpoint.Rest;
import aeonics.manager.Config;
import aeonics.manager.Manager;
import aeonics.template.Parameter;
import aeonics.util.Json;
import aeonics.util.Tuples.Tuple;
import uniqorn.Api;
import uniqorn.Workspace;

@SuppressWarnings("unused")
public class ModelContextProtocol
{
	private ModelContextProtocol() { /* no instances */ }
	
	public static void register()
	{
		/* initialize other static members */
	}
	
	private static final Endpoint.Rest.Type sse = new Endpoint.Rest() { }
		.template()
		.summary("MCP SSE Stream")
		.description("Initiates an SSE stream for MCP. This operation is not supported and returns a 405 error.")
		.create()
		.<Rest.Type>cast()
		.process((data, user) ->
		{
			throw new HttpException(405);
		})
		.url("/mcp")
		.method("GET")
		;
		
	private static final Endpoint.Rest.Type mcp = new Endpoint.Rest() { }
		.template()
		.summary("MCP Requests")
		.description("Hanles MCP requests in JSON-RPC format.")
		.create()
		.<Rest.Type>cast()
		.process((data, user, request) ->
		{
			Data rpc = request.content().isMap("post") ? request.content().get("post") : Json.decode(request.content().asString("body"));
			if( rpc.isList() )
				return Data.map().put("jsonrpc", "2.0").put("id", null).put("error", Data.map().put("code", -32600).put("message", "Batch Requests Not Supported"));
			if( !rpc.isMap() || !rpc.asString("jsonrpc").equals("2.0") )
				return Data.map().put("jsonrpc", "2.0").put("id", null).put("error", Data.map().put("code", -32600).put("message", "Invalid Request"));
			
			String id = rpc.asString("id");
			try
			{
				switch( rpc.asString("method") )
				{
					case "initialize":
						return mcp_initialize(id, rpc.get("params"));
					case "notifications/initialized":
						throw new HttpException(202);
					case "tools/list":
						if( user == User.ANONYMOUS ) throw new HttpException(403);
						return mcp_tool_list(id, rpc.get("params"));
					case "tools/call":
						if( user == User.ANONYMOUS ) throw new HttpException(403);
						return mcp_tool_call(id, rpc.get("params"), request);
					case "ping":
						return Data.map().put("jsonrpc", "2.0").put("id", id).put("result", Data.map());
					default:
						return Data.map().put("jsonrpc", "2.0").put("id", null).put("error", Data.map().put("code", -32601).put("message", "Method Not Found"));
				}
			}
			catch(HttpException he)
			{
				if( he.code >= 500 )
					return Data.map().put("jsonrpc", "2.0").put("id", id).put("error", Data.map().put("code", -32603).put("message", he.data == null || he.data.isEmpty() ? "" : he.data.asString("message")).put("data", he.data));
				if( he.code == 401 || he.code == 403 )
					throw new HttpException(403, Data.map().put("jsonrpc", "2.0").put("id", null).put("error", Data.map().put("code", -32001).put("message", "Unauthorized")));
				if( he.code < 300 )
					throw he; // normal response
					
				return Data.map().put("jsonrpc", "2.0").put("id", id).put("result", Data.map()
					.put("isError", true)
					.put("content", Data.map().put("type", "text").put("text", he.data == null ? "Unspecified error" : he.data.toString()))
				);
			}
			catch(Exception e)
			{
				return Data.map().put("jsonrpc", "2.0").put("id", id).put("error", Data.map().put("code", -32603).put("message", e.getMessage()));
			}
		})
		.url("/mcp")
		.method("POST")
		;
		
	private static Data mcp_initialize(String id, Data params)
	{
		if( !params.asString("protocolVersion").equals("2025-03-26") )
			return Data.map().put("jsonrpc", "2.0").put("id", null).put("error", Data.map().put("code", -32602).put("message", "Unsupported protocol version")
				.put("data", Data.map().put("supported",  Data.list().add("2025-03-26")).put("requested",  params.asString("protocolVersion"))));
		
		return Data.map().put("jsonrpc", "2.0").put("id", id).put("result", Data.map()
			.put("protocolVersion", "2025-03-26")
			.put("capabilities", Data.map().put("tools", Data.map()))
			.put("serverInfo", Data.map().put("name", "Uniqorn").put("version", "1.0.0"))
			.put("instructions", "You should use a valid bearer token provided by the user for all subsequent tools requests. If you do not have this token already, you should prompt the user.")
		);
	}
	
	private static Data mcp_tool_list(String id, Data params)
	{
		Data list = Data.list();
		
		for( Workspace.Type w : Registry.of(Workspace.class) )
		{
			for( Tuple<Entity, Data> e : w.relations("endpoints") )
			{
				if( e.a == null || !e.a.valueOf("enabled").asBool() ) continue;
				Endpoint.Rest.Type r = e.a.<uniqorn.Endpoint.Type>cast().api() != null ? e.a.<uniqorn.Endpoint.Type>cast().api().api() : null;
				Endpoint.Template t = e.a.<uniqorn.Endpoint.Type>cast().api() != null ? e.a.<uniqorn.Endpoint.Type>cast().api().apitemplate() : null;
				if( r == null || t == null ) continue;
				
				Data parameters = Data.map();
				Data required = Data.list();
				
				for( Parameter p : t.parameters() )
				{
					parameters.put(p.name(), Data.map()
						.put("description", p.description()));
				}
				
				list.add(Data.map()
					.put("name", r.method() + " " + w.valueOf("prefix").asString() + r.url())
					.put("description", "## Summary: " + t.summary() + "\n## Description: " + t.description() + "\n## Return value: " + t.returns())
					.put("inputSchema", Data.map()
						.put("type", "object")
						.put("properties", parameters)
						.put("required", required)
					)
				);
			}
		}
		
		return Data.map().put("jsonrpc", "2.0").put("id", id).put("result", Data.map().put("tools", list));
	}
	
	private static Data mcp_tool_call(String id, Data params, Message request) throws Exception
	{
		String name = params.asString("name");
		int space = name.indexOf(' ');
		if( space <= 0 )
			return Data.map().put("jsonrpc", "2.0").put("id", id).put("error", Data.map().put("code", -32602).put("message", "Unknown tool: " + name));
		
		String method = name.substring(0, space);
		String path = Manager.of(Config.class).get(Api.class, "prefix") + name.substring(space + 1);
		if( params.get("arguments").isMap() ) params.put("arguments", Data.map());
		
		for( Endpoint.Type e : Registry.of(Endpoint.class) )
		{
			if( e instanceof Router.Type )
			{
				Data response = e.process(new Message(name)
					.content(Data.map().put("method", method).put("path", path).put("get", params.get("arguments")))
					.user(request.user())
					.connection(request.connection())
				);
				
				return Data.map().put("jsonrpc", "2.0").put("id", id).put("result", Data.map()
					.put("isError", false)
					.put("content", Data.map().put("type", "text").put("text", response.toString()))
				);
			}
		}
		
		return Data.map().put("jsonrpc", "2.0").put("id", id).put("error", Data.map().put("code", -32602).put("message", "Unknown tool: " + name));
	}
}
