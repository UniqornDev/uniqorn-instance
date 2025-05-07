package local;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import aeonics.Boot;
import aeonics.data.Data;
import aeonics.entity.*;
import aeonics.entity.security.Group;
import aeonics.entity.security.Multifactor;
import aeonics.entity.security.Provider;
import aeonics.entity.security.Role;
import aeonics.entity.security.Token;
import aeonics.entity.security.User;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Scheduler;
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
		_System.register();
		_Workspace.register();
		_Endpoint.register();
		_Version.register();
		_Env.register();
		_Role.register();
		_Group.register();
		_User.register();
		_Storage.register();
		_Database.register();
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
				provider.join(Data.map().put("password", data.asString("password")).put("username", user.login()), user);
				
				// invalidate all tokens
				Manager.of(Security.class).clearTokens(user);
				
				return Data.map().put("success", true);
			})
			.url(ROOT + "/self")
			.method("PATCH")
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
	// SYSTEM
	//
	// ========================================
	
	private static class _System
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type status = new Endpoint.Rest() { }
			.template()
			.summary("Instance status")
			.description("This endpoint returns the current limits and plan")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data info = Data.map()
					.put("plan", Manager.of(Config.class).get(Api.class, "plan"))
					.put("prefix", Manager.of(Config.class).get(Api.class, "prefix"))
					.put("limits", Data.list()
						.add(Data.map()
							.put("name", "workspaces")
							.put("max", Manager.of(Config.class).get(Api.class, "workspaces"))
							.put("current", Registry.of(Workspace.class).size()))
						.add(Data.map()
							.put("name", "endpoints")
							.put("max", Manager.of(Config.class).get(Api.class, "endpoints"))
							.put("current", Registry.of(uniqorn.Endpoint.class).size()))
						.add(Data.map()
							.put("name", "consumers")
							.put("max", Manager.of(Config.class).get(Api.class, "consumers"))
							.put("current", Registry.of(User.class).filter(u -> u.hasRole(Constants.ROLE_CONSUMER)).size()))
						.add(Data.map()
							.put("name", "users")
							.put("max", Manager.of(Config.class).get(Api.class, "users"))
							.put("current", Registry.of(User.class).filter(u -> u.hasRole(Constants.ROLE_CONTRIBUTOR) || u.hasRole(Constants.ROLE_MANAGER)).size()))
						.add(Data.map()
							.put("name", "groups")
							.put("max", Manager.of(Config.class).get(Api.class, "groups"))
							.put("current", Registry.of(Group.class).filter(g -> !g.internal()).size()))
						.add(Data.map()
							.put("name", "roles")
							.put("max", Manager.of(Config.class).get(Api.class, "roles"))
							.put("current", Registry.of(Role.class).filter(r -> !r.internal()).size()))
						.add(Data.map()
							.put("name", "storages")
							.put("max", Manager.of(Config.class).get(Api.class, "storages"))
							.put("current", Registry.of(Storage.class).filter(s -> s.type().startsWith("uniqorn.storage.")).size()))
						.add(Data.map()
							.put("name", "databases")
							.put("max", Manager.of(Config.class).get(Api.class, "databases"))
							.put("current", Registry.of(Database.class).filter(d -> d.type().startsWith("uniqorn.database.")).size()))
						.add(Data.map()
							.put("name", "env")
							.put("max", Manager.of(Config.class).get(Api.class, "env"))
							.put("current", Manager.of(Config.class).all(Api.class).keySet().stream().filter(key -> key.startsWith("env.")).count()))
						.add(Data.map()
							.put("name", "versions")
							.put("max", (Manager.of(Config.class).get(Api.class, "versions").asLong()+1) * Manager.of(Config.class).get(Api.class, "endpoints").asLong())
							.put("current", Registry.of(Version.class).size()))
						.add(Data.map()
							.put("name", "rate")
							.put("max", Manager.of(Config.class).get(Api.class, "rate").asLong() * Manager.of(Config.class).get(Api.class, "endpoints").asLong())
							.put("current", StreamSupport.stream(Registry.of(uniqorn.Endpoint.class).spliterator(), false).mapToLong(e -> e.counter().get()).sum()))
					);
				
				Data warnings = Data.list();
				int versions = Manager.of(Config.class).get(Api.class, "versions").asInt();
				int rate = Manager.of(Config.class).get(Api.class, "rate").asInt();
				for( uniqorn.Endpoint.Type e : Registry.of(uniqorn.Endpoint.class) )
				{
					int current = e.countRelations("versions");
					if( current >= versions )
					{
						warnings.add(Data.map()
							.put("type", "versions")
							.put("max", versions)
							.put("current", current)
							.put("endpoint", e.id()));
					}
					if( e.counter().get() >= (rate * 0.8) )
					{
						warnings.add(Data.map()
							.put("type", "rate")
							.put("max", rate)
							.put("current", e.counter().get())
							.put("endpoint", e.id()));
					}
				}
				
				return info.put("warnings", warnings);
			})
			.url(ROOT + "/status")
			.method("GET")
			;
			
		private static final Endpoint.Websocket.Type logs = new Endpoint.Websocket() { }
			.template()
			.summary("Stream logs")
			.description("This endpoint sets the log level globally and opens a websocket to stream logs. "
				+ "Upon disconnect, the log level is reset to 1000 (SEVERE). "
				+ "This endpoint can be used by multiple users at the same time, but the log level reset will interfere with other active users.")
			.clearParameters()
			.add(new Parameter("level")
				.summary("Level")
				.description("Sets the log level globally")
				.format(Parameter.Format.NUMBER)
				.rule(Parameter.Rule.INTEGER)
				.optional(false))
			.create()
			.<Endpoint.Websocket.Type>cast()
			.before((data, user) ->
			{
				if( !user.hasRole(Constants.ROLE_CONTRIBUTOR) && !user.hasRole(Constants.ROLE_MANAGER) )
					throw new HttpException(403);
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				data.put("subscribe", "10000000-1500000000000000")
					.put("output", "data")
					.put("filter", "*/uniqorn.Api#");
				
				Manager.of(Config.class).set(Logger.class, "level", Math.max(0, data.asInt("level")));
			})
			.cleanup(() -> 
			{
				Manager.of(Config.class).set(Logger.class, "level", Logger.SEVERE);
			})
			.url("/api/ws/logs")
			.method("GET")
			;
			
		private static final Endpoint.Websocket.Type debug = new Endpoint.Websocket() { }
			.template()
			.summary("Stream debug info")
			.description("This endpoint opens a websocket to stream debug information. "
				+ "This endpoint can be used by multiple users at the same time without side effects.")
			.clearParameters()
			.add(new Parameter("filter")
				.summary("Filter")
				.description("The debug key filter to use. '#' means everything.")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.defaultValue("#"))
			.create()
			.<Endpoint.Websocket.Type>cast()
			.before((data, user) ->
			{
				if( !user.hasRole(Constants.ROLE_CONTRIBUTOR) && !user.hasRole(Constants.ROLE_MANAGER) )
					throw new HttpException(403);
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				data.put("subscribe", "10000000-1400000000000000")
					.put("output", "data")
					.put("filter", data.asString("filter"));
			})
			.url("/api/ws/debug")
			.method("GET")
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
			.add(new Parameter("prefix")
				.summary("Prefix")
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
					if( Registry.of(Workspace.class).size() >= Manager.of(Config.class).get(Api.class, "workspaces").asInt() )
						throw new HttpException(429, "Maximum number of workspaces reached");
					
					if( Registry.of(Workspace.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate workspace name");
					
					// normalize prefix
					String prefix = data.asString("prefix");
					if( prefix.length() > 0 )
						prefix = "/" + Storage.normalize(prefix).replace('\\', '/');
					
					Workspace.Type workspace = Factory.of(Workspace.class).get(Workspace.class).create()
						.name(data.asString("name"))
						.parameter("prefix", prefix);
					
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
			.add(new Parameter("prefix")
				.summary("Prefix")
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
					
					if( !data.isEmpty("name") )
					{
						if( Registry.of(Workspace.class).get(data.asString("name")) != null &&  Registry.of(Workspace.class).get(data.asString("name")) != workspace )
							throw new HttpException(400, "Duplicate workspace name");
						workspace.name(data.asString("name"));
					}
					
					// normalize prefix
					String prefix = data.asString("prefix");
					if( prefix.length() > 0 )
						prefix = "/" + Storage.normalize(prefix).replace('\\', '/');
					
					workspace.parameter("prefix", prefix);
					
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
			.add(new Parameter("full")
				.summary("Full")
				.description("If true, returns the full listing of workspaces and their endpoints")
				.format(Parameter.Format.BOOLEAN)
				.rule(Parameter.Rule.BOOLEAN)
				.optional(true)
				.defaultValue(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( !data.asBool("full") )
				{
					Data list = Data.list();
					for( Workspace.Type w : Registry.of(Workspace.class) )
						list.add(Data.map().put("id", w.id()).put("name", w.name()).put("prefix", w.valueOf("prefix")));
					return list;
				}
				else
				{
					Data list = Data.map().put("prefix", Manager.of(Config.class).get(Api.class, "prefix"));
					Data workspaces = Data.list();
					
					for( Workspace.Type w : Registry.of(Workspace.class) )
					{
						Data workspace = Data.map().put("id", w.id()).put("name", w.name()).put("prefix", w.valueOf("prefix"));
						Data endpoints = Data.list();
						
						for( Tuple<Entity, Data> e : w.relations("endpoints") )
						{
							if( e.a == null ) continue;
							Data endpoint = Data.map().put("id", e.a.id()).put("enabled", e.a.valueOf("enabled").asBool());
							Endpoint.Rest.Type r = e.a.<uniqorn.Endpoint.Type>cast().api() != null ? e.a.<uniqorn.Endpoint.Type>cast().api().api() : null;
							if( r == null )
								endpoint.put("method", null).put("path", null);
							else
								endpoint.put("method", r.method()).put("path", r.url());
							
							endpoints.add(endpoint);
						}
						
						workspace.put("endpoints", endpoints);
						workspaces.add(workspace);
					}
					
					return list.put("workspaces", workspaces);
				}
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
					
				return Data.map().put("id", w.id()).put("name", w.name()).put("prefix", w.valueOf("prefix")).put("endpoints", endpoints);
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
					if( Registry.of(uniqorn.Endpoint.class).size() >= Manager.of(Config.class).get(Api.class, "endpoints").asInt() )
						throw new HttpException(429, "Maximum number of endpoints reached");
					Workspace.Type workspace = Registry.of(Workspace.class).get(data.asString("workspace"));
					if( workspace == null ) throw new HttpException(404, "Unknown workspace");
					
					String plan = Manager.of(Config.class).get(Api.class, "plan").asString();
					if( plan.equals(Constants.PLAN_TRIAL) || plan.equals(Constants.PLAN_PERSONAL) )
						uniqorn.Endpoint.Type.checkCode(data.asString("code"));
					
					uniqorn.Endpoint.Type endpoint = Factory.of(uniqorn.Endpoint.class).get(uniqorn.Endpoint.class).create();
					try
					{
						endpoint.updateHead(data.asString("code"));
						workspace.addRelation("endpoints", endpoint);
					}
					catch(Exception e)
					{
						Registry.of(uniqorn.Endpoint.class).remove(endpoint);
						throw e;
					}
					
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
				{
					String plan = Manager.of(Config.class).get(Api.class, "plan").asString();
					if( plan.equals(Constants.PLAN_TRIAL) || plan.equals(Constants.PLAN_PERSONAL) )
						uniqorn.Endpoint.Type.checkCode(data.asString("code"));
					
					e.updateHead(data.asString("code"));
				}
				
				return Data.map().put("success", true);
			})
			.url(ROOT + "/endpoint/{id}")
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
				
				if( !data.isEmpty("workspace") )
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
				
				Data endpoint = Data.map().put("id", e.id()).put("enabled", e.valueOf("enabled").asBool());
				
				Version.Type head = e.firstRelation("head");
				endpoint.put("head", Data.map().put("id", head.id()).put("code", head.valueOf("code")).put("date", head.date()));
				
				Api a = e.<uniqorn.Endpoint.Type>cast().api();
				Endpoint.Rest.Type r = a != null ? a.api() : null;
				if( r == null )
				{
					endpoint
						.put("method", null)
						.put("path", null)
						.put("summary", null)
						.put("description", null)
						.put("returns", null)
						.put("parameters", Data.map());
				}
				else
				{
					endpoint
						.put("method", r.method())
						.put("path", r.url())
						.put("summary", a.apitemplate().summary())
						.put("description", a.apitemplate().description())
						.put("returns", a.apitemplate().returns())
						.put("parameters", a.apitemplate().parameters().stream().collect(Collectors.toMap(Parameter::name, p -> p.description() != null ? p.description() : "")));
				}
				
				Data versions = Data.list();
				for( Tuple<Entity, Data> v : e.relations("versions") )
				{
					if( v.a == null ) continue;
					Data version = Data.map().put("id", v.a.id()).put("name", v.a.name()).put("date", v.a.<Version.Type>cast().date());
					versions.add(version);
				}
				endpoint.put("versions", versions);
					
				return endpoint;
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
					if( e.countRelations("versions") >= Manager.of(Config.class).get(Api.class, "versions").asInt() )
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
				{
					if( v.a == null ) continue;
					Data version = Data.map().put("id", v.a.id()).put("name", v.a.name()).put("date", v.a.<Version.Type>cast().date());
					versions.add(version);
				}
					
				return versions;
			})
			.url(ROOT + "/endpoint/{endpoint}/versions")
			.method("GET")
			;
			
		private static final Endpoint.Rest.Type versionDetail = new Endpoint.Rest() { }
			.template()
			.summary("Fetch versions")
			.description("This endpoint returns the code of a version")
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
				
				return Data.map()
					.put("id", v.id())
					.put("name", v.name())
					.put("date", v.date())
					.put("code", v.valueOf("code"));
			})
			.url(ROOT + "/endpoint/{endpoint}/version/{id}")
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
		
		private static final Endpoint.Rest.Type envList = new Endpoint.Rest() { }
			.template()
			.summary("List environment parameter")
			.description("This endpoint lists all environment parameters")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Map.Entry<String, Data> c : Manager.of(Config.class).all(Api.class).entrySet() )
				{
					if( !c.getKey().startsWith("env.") ) continue;
					list.add(Data.map()
						.put("name", c.getKey().substring(4))
						.put("value", c.getValue())
						.put("description", Manager.of(Config.class).definition(Api.class, c.getKey()).description()));
				}
				return list;
			})
			.url(ROOT + "/env")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type envSet = new Endpoint.Rest() { }
			.template()
			.summary("Set environment parameter")
			.description("This endpoint sets the value of an environment parameter")
			.add(new Parameter("name")
				.summary("Name")
				.description("The environment parameter name")
				.format(Parameter.Format.TEXT)
				.rule("0123456789abcdefghijklmnopqrstuvwxyz.")
				.optional(false).min(1).max(50))
			.add(new Parameter("description")
				.summary("Description")
				.description("The environment parameter description")
				.format(Parameter.Format.TEXT)
				.optional(true).max(2048))
			.add(new Parameter("value")
				.summary("Value")
				.description("The environment parameter value")
				.format(Parameter.Format.TEXT)
				.max(20000))
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
					if( !data.isEmpty("description") )
						Manager.of(Config.class).definition(Api.class, "env." + data.asString("name")).description(data.asString("description"));
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
				.rule("0123456789abcdefghijklmnopqrstuvwxyz.")
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
	
	// ========================================
	//
	// ROLE
	//
	// ========================================
	
	private static class _Role
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type roleList = new Endpoint.Rest() { }
			.template()
			.summary("List roles")
			.description("This endpoint lists all security roles")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Role.Type r : Registry.of(Role.class) )
					if( !r.internal() )
						list.add(r.export());
				return list;
			})
			.url(ROOT + "/roles")
			.method("GET")
			;
			
		private static final Endpoint.Rest.Type roleDetails = new Endpoint.Rest() { }
			.template()
			.summary("Role details")
			.description("This endpoint returns the role name and all concerned consumer users")
			.add(new Parameter("id")
				.summary("Id")
				.description("The role id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Role.Type role = Registry.of(Role.class).get(data.asString("id"));
				if( role == null || role.internal() ) throw new HttpException(404, "Unknown role");
				
				Data list = Data.list();
				for( User.Type u : Registry.of(User.class) )
					if( u.hasRole(Constants.ROLE_CONSUMER) && u.hasRole(role) )
						list.add(Data.map().put("name", u.name()).put("id", u.id()));
				return Data.map().put("name", role.name()).put("users", list);
			})
			.url(ROOT + "/role/{id}")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// GROUP
	//
	// ========================================
	
	private static class _Group
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type groupList = new Endpoint.Rest() { }
			.template()
			.summary("List groups")
			.description("This endpoint lists all security groups")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Group.Type g : Registry.of(Group.class) )
					if( !g.internal() )
						list.add(g.export());
				return list;
			})
			.url(ROOT + "/groups")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type groupDetails = new Endpoint.Rest() { }
			.template()
			.summary("Group details")
			.description("This endpoint returns the group name and all concerned consumer users")
			.add(new Parameter("id")
				.summary("Id")
				.description("The group id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Group.Type group = Registry.of(Group.class).get(data.asString("id"));
				if( group == null || group.internal() ) throw new HttpException(404, "Unknown group");
				
				Data list = Data.list();
				for( User.Type u : Registry.of(User.class) )
					if( u.hasRole(Constants.ROLE_CONSUMER) && u.isMemberOf(group) )
						list.add(Data.map().put("name", u.name()).put("id", u.id()));
				return Data.map().put("name", group.name()).put("users", list);
			})
			.url(ROOT + "/group/{id}")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// USER
	//
	// ========================================
	
	private static class _User
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type userList = new Endpoint.Rest() { }
			.template()
			.summary("List users")
			.description("This endpoint lists all security users")
			.add(new Parameter("type")
				.summary("Type")
				.description("The user type")
				.format(Parameter.Format.TEXT)
				.rule((v) -> v.isEmpty() || v.equals("consumer") || v.equals("manager") || v.equals("contributor") )
				.values("consumer", "manager", "contributor")
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, currentUser) ->
			{
				// force consumers for Contributor users. Manager users can choose.
				if( currentUser.hasRole(Constants.ROLE_CONTRIBUTOR) )
					data.put("type", "consumer");
					
				Data list = Data.list();
				for( User.Type u : Registry.of(User.class) )
				{
					if( u.internal() ) continue;
					Data current = Data.map()
						.put("id", u.id())
						.put("name", u.name())
						.put("login", u.login())
						.put("type", 
							u.hasRole(Constants.ROLE_CONSUMER) ? "consumer" :
							u.hasRole(Constants.ROLE_CONTRIBUTOR) ? "contributor" : 
							u.hasRole(Constants.ROLE_MANAGER) ? "manager" : null);
					
					if( data.isEmpty("type") )
					{
						if( u.hasRole(Constants.ROLE_CONSUMER) || u.hasRole(Constants.ROLE_MANAGER) || u.hasRole(Constants.ROLE_CONTRIBUTOR) )
							list.add(current);
					}
					else if( data.asString("type").equals("consumer") )
					{
						if( u.hasRole(Constants.ROLE_CONSUMER) )
							list.add(current);
					}
					else if( data.asString("type").equals("manager") )
					{
						if( u.hasRole(Constants.ROLE_MANAGER) )
							list.add(current);
					}
					else if( data.asString("type").equals("contributor") )
					{
						if( u.hasRole(Constants.ROLE_CONTRIBUTOR) )
							list.add(current);
					}
				}
				
				return list;
			})
			.url(ROOT + "/users")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type userDetails = new Endpoint.Rest() { }
			.template()
			.summary("User details")
			.description("This endpoint returns the user name, login, type and all groups and roles")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process((data, currentUser) ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					
					// Contributor users can only see Consumers
					if( currentUser.hasRole(Constants.ROLE_CONTRIBUTOR) && !user.hasRole(Constants.ROLE_CONSUMER) )
						throw new HttpException(404, "Unknown user");
					
					Data roles = Data.list();
					for( Tuple<Entity, Data> r : user.relations("roles") )
					{
						if( r.a != null && !r.a.internal() )
							roles.add(Data.map().put("name", r.a.name()).put("id", r.a.id()));
					}
					
					Data groups = Data.list();
					for( Tuple<Entity, Data> g : user.relations("groups") )
					{
						if( g.a != null && !g.a.internal()  )
							groups.add(Data.map().put("name", g.a.name()).put("id", g.a.id()));
					}
					
					return Data.map()
						.put("id", user.id())
						.put("name", user.name())
						.put("login", user.login())
						.put("type", 
							user.hasRole(Constants.ROLE_CONSUMER) ? "consumer" :
							user.hasRole(Constants.ROLE_CONTRIBUTOR) ? "contributor" : 
							user.hasRole(Constants.ROLE_MANAGER) ? "manager" : null)
						.put("roles", roles)
						.put("groups", groups)
						;
				}
			})
			.url(ROOT + "/user/{id}")
			.method("GET")
			;
	}
	
	// ========================================
	//
	// STORAGE
	//
	// ========================================
	
	private static class _Storage
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type storageList = new Endpoint.Rest() { }
			.template()
			.summary("List storages")
			.description("This endpoint lists all storage spaces")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Storage.Type s : Registry.of(Storage.class) )
				{
					if( !s.type().startsWith("uniqorn.storage.") )
						continue;
					
					list.add(s.export());
				}
				return list;
			})
			.url(ROOT + "/storages")
			.method("GET")
			;
			
		private static final Endpoint.Rest.Type storageCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create storage")
			.description("This endpoint creates a new storage")
			.add(new Parameter("name")
				.summary("Name")
				.description("The storage name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.add(new Parameter("type")
				.summary("Type")
				.description("The storage type")
				.format(Parameter.Format.TEXT)
				.rule((v) -> Constants.STORAGES.containsKey(v))
				.values(Constants.STORAGES.keySet().toArray(new String[Constants.STORAGES.size()]))
				.optional(false))
			.add(new Parameter("parameters")
				.summary("Parameters")
				.description("The specific storage parameters for the selected type")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Class<? extends Storage> c = Constants.STORAGES.get(data.asString("type"));
				if( c == null )
					throw new HttpException(400, "Storage type not available");
				
				if( data.asString("type").equals("file") )
				{
					// normalize path
					String path = Storage.normalize(data.get("parameters").asString("root"));
					String root = Manager.of(Config.class).get(Api.class, "storage").asString();
					if( !path.startsWith(root) ) path = Storage.resolve(root, path);
					data.get("parameters").put("root", path);
				}
				
				synchronized(_Storage.class)
				{
					if( Registry.of(Storage.class).filter(s -> s.type().startsWith("uniqorn.storage.")).size() >= Manager.of(Config.class).get(Api.class, "storages").asInt() )
						throw new HttpException(429, "Maximum number of storages reached");
					
					if( Registry.of(Storage.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate storage name");
					
					Storage.Type s = Factory.of(Storage.class).get(c).create(Data.map().put("parameters", data.get("parameters")))
						.name(data.asString("name"));
					
					return Data.map().put("id", s.id());
				}
			})
			.url(ROOT + "/storage")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type storageUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update storage")
			.description("This endpoint renames a storage. Storages cannot be reconfigured because of side effects, delete and create a new one if needed.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("Name")
				.description("The storage name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( data.asString("id").equals(Constants.LOCAL_STORAGE) )
					throw new HttpException(400, "Cannot update default local storage");
				
				synchronized(_Storage.class)
				{
					Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
					if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");
					
					if( !data.isEmpty("name") )
					{
						if( Registry.of(Storage.class).get(data.asString("name")) != null && Registry.of(Storage.class).get(data.asString("name")) != s )
							throw new HttpException(400, "Duplicate storage name");
						s.name(data.asString("name"));
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/storage/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type storageDelete = new Endpoint.Rest() { }
			.template()
			.summary("Delete storage")
			.description("This endpoint removes a storage")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( data.asString("id").equals(Constants.LOCAL_STORAGE) )
					throw new HttpException(400, "Cannot remove default local storage");
					
				synchronized(_Storage.class)
				{
					Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
					if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");
					
					Registry.of(Storage.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/storage/{id}")
			.method("DELETE")
			;
	}
	
	// ========================================
	//
	// DATABASE
	//
	// ========================================
	
	private static class _Database
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type databaseList = new Endpoint.Rest() { }
			.template()
			.summary("List databases")
			.description("This endpoint lists all database connections")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Data list = Data.list();
				for( Database.Type d : Registry.of(Database.class) )
				{
					if( !d.type().startsWith("uniqorn.database.") )
						continue;
					
					list.add(d.export());
				}
				return list;
			})
			.url(ROOT + "/databases")
			.method("GET")
			;
			
		private static final Endpoint.Rest.Type databaseCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create database")
			.description("This endpoint creates a new database")
			.add(new Parameter("name")
				.summary("Name")
				.description("The database name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.add(new Parameter("type")
				.summary("Type")
				.description("The database type")
				.format(Parameter.Format.TEXT)
				.rule((v) -> Constants.DATABASES.containsKey(v))
				.values(Constants.DATABASES.keySet().toArray(new String[Constants.DATABASES.size()]))
				.optional(false))
			.add(new Parameter("parameters")
				.summary("Parameters")
				.description("The specific database parameters for the selected type")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Class<? extends Database> c = Constants.DATABASES.get(data.asString("type"));
				if( c == null )
					throw new HttpException(400, "Database type not available");
				
				synchronized(_Database.class)
				{
					if( Registry.of(Database.class).filter(d -> d.type().startsWith("uniqorn.database.")).size() >= Manager.of(Config.class).get(Api.class, "databases").asInt() )
						throw new HttpException(429, "Maximum number of databases reached");
					
					if( Registry.of(Database.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate database name");
					
					Database.Type d = Factory.of(Database.class).get(c).create(Data.map().put("parameters", data.get("parameters")))
						.name(data.asString("name"));
					
					return Data.map().put("id", d.id());
				}
			})
			.url(ROOT + "/database")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type databaseUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update database")
			.description("This endpoint renames a database. Databases cannot be reconfigured because of side effects, delete and create a new one if needed.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("Name")
				.description("The database name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Database.class)
				{
					Database.Type d = Registry.of(Database.class).get(data.asString("id"));
					if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");
					
					if( !data.isEmpty("name") )
					{
						if( Registry.of(Database.class).get(data.asString("name")) != null && Registry.of(Database.class).get(data.asString("name")) != d )
							throw new HttpException(400, "Duplicate database name");
						d.name(data.asString("name"));
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/database/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type databaseDelete = new Endpoint.Rest() { }
			.template()
			.summary("Delete database")
			.description("This endpoint removes a database")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Database.class)
				{
					Database.Type d = Registry.of(Database.class).get(data.asString("id"));
					if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");
					
					Registry.of(Database.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/database/{id}")
			.method("DELETE")
			;
	}
}
