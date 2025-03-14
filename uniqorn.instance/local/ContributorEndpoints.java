package local;

import java.util.concurrent.atomic.AtomicInteger;

import aeonics.data.Data;
import aeonics.entity.*;
import aeonics.entity.security.Multifactor;
import aeonics.entity.security.Provider;
import aeonics.entity.security.User;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.manager.Config;
import aeonics.manager.Manager;
import aeonics.manager.Security;
import aeonics.http.Endpoint.Rest;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Tuple;
import uniqorn.*;

/**
 * This class regroups all the local-instance apis
 */
@SuppressWarnings("unused")
public class ContributorEndpoints
{
	static final String ROOT = "/api/contributor";
	
	private ContributorEndpoints() { /* no instances */ }
	
	public static void register()
	{
		_Self.register();
		_Workspace.register();
		_Endpoint.register();
		_Version.register();
		_Env.register();
	}
	
	// ========================================
	//
	// SELF
	//
	// ========================================
	
	private static class _Self
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type selfPassword = new Endpoint.Rest() { }
			.template()
			.summary("Change password")
			.description("This endpoint can be used to change the current user password. All user tokens are invalidated.")
			.add(new Parameter("password")
				.summary("Password")
				.description("The new user password")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				Provider.Type provider = Registry.of(Provider.class).get(p -> p.type().equals(StringUtils.toLowerCase(Provider.Local.class)));
				if( provider == null ) throw new HttpException(500, "Security provider unavailable");
				
				// leave and rejoin
				provider.leave(user);
				provider.join(Data.map().put("password", data.asString("password")), user);
				
				// invalidate all tokens
				Manager.of(Security.class).clearTokens(user);
				
				return Data.map().put("success", true);
			})
			.url(ROOT + "/self")
			.method("POST")
			;
			
		private static final Endpoint.Rest.Type selfReset = new Endpoint.Rest() { }
			.template()
			.summary("Reset OTP")
			.description("This endpoint can be used to reset the OTP of the current user.")
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				for( Multifactor.Type m : Registry.of(Multifactor.class) )
					m.forget(user);
				
				return Data.map().put("success", true);
			})
			.url(ROOT + "/self/otp")
			.method("DELETE")
			;
	}
	
	// ========================================
	//
	// WORKSPACE
	//
	// ========================================
	
	private static class _Workspace
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type workspaceCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create a workspace")
			.description("This endpoint creates a workspace")
			.add(new Parameter("name")
				.summary("Name")
				.description("The workspace name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.add(new Parameter("path")
				.summary("Path")
				.description("The url path prefix")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(Workspace.class)
				{
					if( Registry.of(Workspace.class).size() >= Manager.of(Config.class).get("workspaces").asInt() )
						throw new HttpException(429, "Maximum number of workspaces reached");
					
					if( Registry.of(Workspace.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate workspace name");
					
					Workspace.Type workspace = Factory.of(Workspace.class).get(Workspace.class).create()
						.name(data.asString("name"))
						.parameter("prefix", data.get("path"));
					
					return Data.map().put("id", workspace.id());
				}
			})
			.url(ROOT + "/workspace")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type workspaceUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update a workspace")
			.description("This endpoint updates a workspace")
			.add(new Parameter("id")
				.summary("Id")
				.description("The workspace id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("Name")
				.description("The workspace name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.add(new Parameter("path")
				.summary("Path")
				.description("The url path prefix")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(Workspace.class)
				{
					Workspace.Type workspace = Registry.of(Workspace.class).get(data.asString("id"));
					if( workspace == null ) throw new HttpException(404, "Unknown workspace");
					
					if( !data.isNull("name") )
					{
						if( Registry.of(Workspace.class).get(data.asString("name")) != null &&  Registry.of(Workspace.class).get(data.asString("name")) != workspace )
							throw new HttpException(400, "Duplicate workspace name");
						workspace.name(data.asString("name"));
					}
					if( !data.isNull("path") )
						workspace.parameter("prefix", data.get("path"));
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/workspace/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type workspaceDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove a workspace")
			.description("This endpoint removes a workspace")
			.add(new Parameter("id")
				.summary("Id")
				.description("The workspace id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(Workspace.class)
				{
					Registry.of(Workspace.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/workspace/{id}")
			.method("DELETE")
			;
		
		private static final Endpoint.Rest.Type workspaceList = new Endpoint.Rest() { }
			.template()
			.summary("List workspaces")
			.description("This endpoint lists all workspaces")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Workspace.Type w : Registry.of(Workspace.class) )
					list.add(Data.map().put("id", w.id()).put("name", w.name()).put("path", w.valueOf("prefix")));
				return list;
			})
			.url(ROOT + "/workspaces")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type workspaceDetails = new Endpoint.Rest() { }
			.template()
			.summary("Fetch workspace")
			.description("This endpoint fetches the details of a workspace")
			.add(new Parameter("id")
				.summary("Id")
				.description("The workspace id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Workspace.Type w = Registry.of(Workspace.class).get(data.asString("id"));
				if( w == null ) throw new HttpException(404, "Unknown workspace");
				
				Data endpoints = Data.list();
				for( Tuple<Entity, Data> e : w.relations("endpoints") )
					endpoints.add(e.a.export());
					
				return Data.map().put("id", w.id()).put("name", w.name()).put("path", w.valueOf("prefix")).put("endpoints", endpoints);
			})
			.url(ROOT + "/workspace/{id}")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// ENDPOINT
	//
	// ========================================
	
	private static class _Endpoint
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type endpointCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create an endpoint")
			.description("This endpoint creates a custom endpoint")
			.add(new Parameter("workspace")
				.summary("Workspace")
				.description("The target workspace id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("code")
				.summary("code")
				.description("The head version code of the endpoint")
				.format(Parameter.Format.CODE)
				.optional(false)
				.max(1024*512))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(uniqorn.Endpoint.class)
				{
					if( Registry.of(uniqorn.Endpoint.class).size() >= Manager.of(Config.class).get("endpoints").asInt() )
						throw new HttpException(429, "Maximum number of endpoints reached");
					
					uniqorn.Endpoint.Type endpoint = Factory.of(uniqorn.Endpoint.class).get(uniqorn.Endpoint.class).create();
					
					return Data.map().put("id", endpoint.id());
				}
			})
			.url(ROOT + "/endpoint")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type endpointUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update an endpoint")
			.description("This endpoint updates the head version of an endpoint and/or relocate the endpoint in a workspace (if provided)")
			.add(new Parameter("id")
				.summary("Id")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("workspace")
				.summary("Workspace")
				.description("The target workspace id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(true))
			.add(new Parameter("code")
				.summary("Code")
				.description("The head version code of the endpoint")
				.format(Parameter.Format.CODE)
				.optional(true)
				.max(1024*512))
			.add(new Parameter("enabled")
				.summary("Enabled")
				.description("The whether or not this endpoint is enabled")
				.format(Parameter.Format.BOOLEAN)
				.rule(Parameter.Rule.BOOLEAN)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("id"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				if( !data.isEmpty("workspace") )
				{
					Workspace.Type target = Registry.of(Workspace.class).get(data.asString("workspace"));
					if( target == null ) throw new HttpException(404, "Unknown workspace");
					
					for( Workspace.Type w : Registry.of(Workspace.class) )
						if( w.hasRelation("endpoints", e) )
							w.removeRelation("endpoints", e);
					target.addRelation("endpoints", e);
				}
				
				if( !data.isEmpty("enabled") )
					e.parameter("enabled", data.get("enabled"));
				
				if( !data.isEmpty("code") )
					e.updateHead(data.asString("code"));
				
				return Data.map().put("success", true);
			})
			.url(ROOT + "/endpoint")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type endpointDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove an endpoint")
			.description("This endpoint removes a custom endpoint")
			.add(new Parameter("id")
				.summary("Id")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(uniqorn.Endpoint.class)
				{
					Registry.of(uniqorn.Endpoint.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/endpoint/{id}")
			.method("DELETE")
			;
		
		private static final Endpoint.Rest.Type endpointList = new Endpoint.Rest() { }
			.template()
			.summary("List endpoints")
			.description("This endpoint lists all custom endpoints, eventually in the specified workspace")
			.add(new Parameter("workspace")
				.summary("Workspace")
				.description("Limit results to the specified workspace")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				
				if( !data.isNull("workspace") )
				{
					Workspace.Type w = Registry.of(Workspace.class).get(data.asString("id"));
					if( w == null ) throw new HttpException(404, "Unknown workspace");
					
					for( Tuple<Entity, Data> e : w.relations("endpoints") )
						if( e.a != null )
							list.add(e.a.export());
				}
				else
				{
					for( uniqorn.Endpoint.Type e : Registry.of(uniqorn.Endpoint.class) )
						list.add(e.export());
				}
				
				return list;
			})
			.url(ROOT + "/endpoints")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type endpointDetails = new Endpoint.Rest() { }
			.template()
			.summary("Fetch endpoint")
			.description("This endpoint fetches the details of an endpoint")
			.add(new Parameter("id")
				.summary("Id")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("id"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				Data versions = Data.list();
				for( Tuple<Entity, Data> v : e.relations("versions") )
					versions.add(v.a.export());
					
				return e.export().put("versions", versions);
			})
			.url(ROOT + "/endpoint/{id}")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// VERSION
	//
	// ========================================
	
	private static class _Version
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type versionCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create a version")
			.description("This endpoint tags the head of an endpoint as a new version")
			.add(new Parameter("endpoint")
				.summary("Endpoint")
				.description("The target endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("name")
				.description("The version name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("endpoint"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				synchronized(e)
				{
					if( e.countRelations("versions") >= Manager.of(Config.class).get("versions").asInt() )
						throw new HttpException(429, "Maximum number of versions reached");
					
					Version.Type v = e.tag(data.asString("name"));
					return Data.map().put("id", v.id());
				}
			})
			.url(ROOT + "/endpoint/{endpoint}/version")
			.method("POST")
			;
			
		private static final Endpoint.Rest.Type versionDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove a version")
			.description("This endpoint removes a version from an endpoint")
			.add(new Parameter("id")
				.summary("Id")
				.description("The version id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("endpoint")
				.summary("Endpoint")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("endpoint"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				Version.Type v = Registry.of(Version.class).get(data.asString("id"));
				if( v == null ) throw new HttpException(404, "Unknown version");
				
				synchronized(e)
				{
					if( !e.hasRelation("versions", v) )
						if( v == null ) throw new HttpException(404, "Unknown version in the specified endpoint");
					
					e.removeRelation("versions", v);
					Registry.of(Version.class).remove(v);
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/endpoint/{endpoint}/version/{id}")
			.method("DELETE")
			;
		
		private static final Endpoint.Rest.Type versionUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update a version")
			.description("This endpoint updates a version name")
			.add(new Parameter("id")
				.summary("Id")
				.description("The version id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("endpoint")
				.summary("Endpoint")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("name")
				.description("The version name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("endpoint"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				Version.Type v = Registry.of(Version.class).get(data.asString("id"));
				if( v == null ) throw new HttpException(404, "Unknown version");
				
				v.name(data.asString("name"));
				return Data.map().put("success", true);
			})
			.url(ROOT + "/endpoint/{endpoint}/version/{id}")
			.method("PUT")
			;
		
		private static final Endpoint.Rest.Type versionRestore = new Endpoint.Rest() { }
			.template()
			.summary("Restore version")
			.description("This endpoint restores a version as the head of an endpoint")
			.add(new Parameter("id")
				.summary("Id")
				.description("The version id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("endpoint")
				.summary("Endpoint")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("endpoint"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				Version.Type v = Registry.of(Version.class).get(data.asString("id"));
				if( v == null ) throw new HttpException(404, "Unknown version");
				
				if( !e.hasRelation("versions", v) )
					if( v == null ) throw new HttpException(404, "Unknown version in the specified endpoint");
					
				e.restore(v.id());
				return Data.map().put("success", true);
			})
			.url(ROOT + "/endpoint/{endpoint}/version/{id}")
			.method("PATCH")
			;
		
		private static final Endpoint.Rest.Type versionList = new Endpoint.Rest() { }
			.template()
			.summary("List versions")
			.description("This endpoint lists all versions of an endpoint")
			.add(new Parameter("endpoint")
				.summary("Endpoint")
				.description("The endpoint id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("endpoint"));
				if( e == null ) throw new HttpException(404, "Unknown endpoint");
				
				Data versions = Data.list();
				for( Tuple<Entity, Data> v : e.relations("versions") )
					versions.add(v.a.export());
					
				return e.export().put("versions", versions);
			})
			.url(ROOT + "/endpoint/{endpoint}/versions")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// ENV
	//
	// ========================================
	
	private static class _Env
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type envSet = new Endpoint.Rest() { }
			.template()
			.summary("Set environment parameter")
			.description("This endpoint sets the value of an environment parameter")
			.add(new Parameter("name")
				.summary("Name")
				.description("The environment parameter name")
				.format(Parameter.Format.TEXT)
				.rule("0123456789abcdefghijklmnopqrstuvwxyz_")
				.optional(false).min(1).max(50))
			.add(new Parameter("value")
				.summary("Value")
				.description("The environment parameter value")
				.format(Parameter.Format.TEXT)
				.max(256))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(Api.class)
				{
					if( !Manager.of(Config.class).contains(Api.class, "env." + data.asString("name")) )
					{
						long count = Manager.of(Config.class).all(Api.class).keySet().stream().filter(key -> key.startsWith("env.")).count();
						if( count >= Manager.of(Config.class).get(Api.class, "env").asLong() )
							throw new HttpException(429, "Too many env parameters");
					}
					
					Manager.of(Config.class).set(Api.class, "env." + data.asString("name"), data.get("value"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/env/{name}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type envDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove environment parameter")
			.description("This endpoint removes an environment parameter")
			.add(new Parameter("name")
				.summary("Name")
				.description("The environment parameter name")
				.format(Parameter.Format.TEXT)
				.rule("0123456789abcdefghijklmnopqrstuvwxyz_")
				.optional(false).min(1).max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(Api.class)
				{
					Manager.of(Config.class).remove(Api.class, "env." + data.asString("name"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/env/{name}")
			.method("DELETE")
			;
	}
}
