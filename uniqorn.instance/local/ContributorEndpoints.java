package local;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import aeonics.util.Tuples.Quadruple;
import aeonics.util.Tuples.Tuple;
import uniqorn.*;
import uniqorn.internal.GitSync;
import uniqorn.internal.Globals;
import aeonics.git.Bare;
import aeonics.git.Git;
import aeonics.git.GitRepo;
import aeonics.git.Operations;

/**
 * This class regroups all the local-instance apis
 */
@SuppressWarnings("unused")
public class ContributorEndpoints
{
	static final String ROOT = "/api/contributor";

	private ContributorEndpoints() { /* no instances */ }

	private static GitRepo.Type repo() { return Registry.of(GitRepo.class).get("uniqorn"); }
	
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
		_Explorer.register();
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
			.add(new Parameter("current_password")
				.summary("Current password")
				.description("The current user password for verification")
				.format(Parameter.Format.TEXT)
				.optional(false))
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

				// verify current password
				User.Type check = provider.authenticate(Data.map().put("username", user.login()).put("password", data.asString("current_password")));
				if( check == null || !check.id().equals(user.id()) ) throw new HttpException(403, "Invalid current password");

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
			.add(new Parameter("otp")
				.summary("OTP")
				.description("The current OTP code is required as proof of ownership.")
				.optional(false)
				.format(Parameter.Format.TEXT))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				if( !Multifactor.check(user, data) )
					throw new HttpException(403, "OTP code mismatch");

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
				java.io.File disk = new java.io.File(Manager.of(Config.class).get(Api.class, "rootstorage").asString());
				
				Data info = Data.map()
					.put("plan", Manager.of(Config.class).get(Api.class, "plan"))
					.put("prefix", Manager.of(Config.class).get(Api.class, "prefix"))
					.put("limits", Data.list()
						.add(Data.map()
							.put("name", "disk")
							.put("max", disk.getTotalSpace())
							.put("current", disk.getTotalSpace() - disk.getFreeSpace()))
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
							.put("current", Registry.of(User.class).filter(u -> u.hasRole(Globals.ROLE_CONSUMER)).size()))
						.add(Data.map()
							.put("name", "users")
							.put("max", Manager.of(Config.class).get(Api.class, "users"))
							.put("current", Registry.of(User.class).filter(u -> !u.hasRole(Role.SUPERADMIN) && (u.hasRole(Globals.ROLE_CONTRIBUTOR) || u.hasRole(Globals.ROLE_MANAGER))).size()))
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
							.put("name", "rate")
							.put("max", Manager.of(Config.class).get(Api.class, "rate").asLong() * Manager.of(Config.class).get(Api.class, "endpoints").asLong())
							.put("current", StreamSupport.stream(Registry.of(uniqorn.Endpoint.class).spliterator(), false).mapToLong(e -> e.counter().get()).sum()))
					);
				
				Data warnings = Data.list();
				int rate = Manager.of(Config.class).get(Api.class, "rate").asInt();
				for( uniqorn.Endpoint.Type e : Registry.of(uniqorn.Endpoint.class) )
				{
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
				if( !user.hasRole(Globals.ROLE_CONTRIBUTOR) && !user.hasRole(Globals.ROLE_MANAGER) )
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
				if( !user.hasRole(Globals.ROLE_CONTRIBUTOR) && !user.hasRole(Globals.ROLE_MANAGER) )
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
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				// creating a workspace does not mean we create a GIT folder.
				// it will be there already present when needed.
				
				synchronized(Workspace.class)
				{
					if( !StringUtils.isComposedOf(data.asString("name"), "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.") )
						throw new HttpException(400, "Invalid workspace name. Only alphanumeric and '-_.' is allowed");
					
					if( Registry.of(Workspace.class).size() >= Manager.of(Config.class).get(Api.class, "workspaces").asInt() )
						throw new HttpException(429, "Maximum number of workspaces reached");
					
					if( Registry.of(Workspace.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate workspace name");
					
					// normalize prefix
					String prefix = Storage.normalize(data.asString("name"));
					
					Workspace.Type workspace = Factory.of(Workspace.class).get(Workspace.class).create()
						.name(data.asString("name"))
						.parameter("prefix", "/" + prefix);
					
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
				.optional(false)
				.max(50))
			.add(new Parameter("message")
				.summary("Message")
				.description("The reason for this action")
				.format(Parameter.Format.TEXT)
				.max(8192)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				if( !StringUtils.isComposedOf(data.asString("name"), "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-_.") )
					throw new HttpException(400, "Invalid workspace name. Only alphanumeric and '-_.' is allowed");
				
				synchronized(Workspace.class)
				{
					Workspace.Type workspace = Registry.of(Workspace.class).get(data.asString("id"));
					if( workspace == null ) throw new HttpException(404, "Unknown workspace");
					
					if( Registry.of(Workspace.class).get(data.asString("name")) != null &&  Registry.of(Workspace.class).get(data.asString("name")) != workspace )
						throw new HttpException(400, "Duplicate workspace name");
					
					String oldPath = GitSync.src + "/" + workspace.name();
					String newPath = GitSync.src + "/" + data.asString("name");
					Bare.rename(repo().store(), repo().root(), oldPath, newPath, user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Rename workspace" : data.asString("message"), null);
					
					workspace.name(data.asString("name"));
					workspace.parameter("prefix", "/" + data.asString("name"));
					
					for( Tuple<Entity, Data> e : workspace.relations("endpoints") )
					{
						if( !(e.a instanceof uniqorn.Endpoint.Type) ) continue;
						 
						oldPath = e.a.valueOf("path").asString();
						newPath = GitSync.src + "/" + workspace.name() + "/" + Paths.get(oldPath).getFileName().toString();
						e.a.parameter("path", newPath);
					}
					
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
			.add(new Parameter("message")
				.summary("Message")
				.description("The reason for this action")
				.format(Parameter.Format.TEXT)
				.max(8192)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				synchronized(Workspace.class)
				{
					Workspace.Type workspace = Registry.of(Workspace.class).get(data.asString("id"));
					if( workspace == null ) throw new HttpException(404, "Unknown workspace");
					
					Bare.remove(repo().store(), repo().root(),GitSync.src + "/" + workspace.name(), user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Remove workspace" : data.asString("message"), null);
					
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
			.add(new Parameter("message")
				.summary("Message")
				.description("The reason for this action")
				.format(Parameter.Format.TEXT)
				.max(8192)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
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
					
					String path = GitSync.src + "/" + workspace.name() + "/endpoint_" + System.nanoTime() + ".java";
					Bare.createFile(repo().store(), repo().root(),path, data.asString("code").getBytes(StandardCharsets.ISO_8859_1), user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Create endpoint" : data.asString("message"), null);
					
					uniqorn.Endpoint.Type endpoint = Factory.of(uniqorn.Endpoint.class).get(uniqorn.Endpoint.class).create(Data.map().put("parameters", Data.map()
						.put("enabled", true)
						.put("path", path)
						.put("sha", "0")
						))
						.name("Git endpoint " + path)
						.internal(true)
						.<uniqorn.Endpoint.Type>cast();
					
					workspace.addRelation("endpoints", endpoint);
					
					Data result = Data.map().put("id", endpoint.id());
					try
					{
						endpoint.updateHead();
						result.put("error", null);
					}
					catch(Exception e)
					{
						result.put("error", (e instanceof HttpException ? ((HttpException)e).data : e.getMessage()));
					}
					
					return result;
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
			.add(new Parameter("message")
				.summary("Message")
				.description("The reason for this action")
				.format(Parameter.Format.TEXT)
				.max(8192)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("id"));
				if( e == null || !(e instanceof uniqorn.Endpoint.Type)) throw new HttpException(404, "Unknown endpoint");
				
				if( !data.isEmpty("workspace") )
				{
					Workspace.Type target = Registry.of(Workspace.class).get(data.asString("workspace"));
					if( target == null ) throw new HttpException(404, "Unknown workspace");
					if( !target.hasRelation("endpoints", e) )
					{
						for( Workspace.Type w : Registry.of(Workspace.class) )
							if( w.hasRelation("endpoints", e) )
								w.removeRelation("endpoints", e);
						target.addRelation("endpoints", e);
						
						String oldPath = e.valueOf("path").asString();
						String newPath = GitSync.src + "/" + target.name() + "/" + Paths.get(oldPath).getFileName().toString();
						Bare.rename(repo().store(), repo().root(), oldPath, newPath, user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Move endpoint" : data.asString("message"), null);
						e.parameter("path", newPath);
					}
				}
				
				if( !data.isEmpty("enabled") && e.valueOf("enabled").asBool() != data.asBool("enabled") )
				{
					e.parameter("enabled", data.get("enabled"));
					String path = e.<uniqorn.Endpoint.Type>cast().fullPath();
					String state = data.asBool("enabled") ? "enabled" : "disabled";
					if( path.isBlank() )
						Manager.of(Logger.class).config(Api.class, "Endpoint with source {} (not deployed) state changed to {}", e.valueOf("path"), state);
					else
						Manager.of(Logger.class).config(Api.class, "Endpoint {} state changed to {}", path, state);
				}
				
				if( !data.isEmpty("code") )
				{
					String plan = Manager.of(Config.class).get(Api.class, "plan").asString();
					if( plan.equals(Constants.PLAN_TRIAL) || plan.equals(Constants.PLAN_PERSONAL) )
						uniqorn.Endpoint.Type.checkCode(data.asString("code"));
					
					Bare.createFile(repo().store(), repo().root(), e.valueOf("path").asString(), data.asString("code").getBytes(StandardCharsets.ISO_8859_1), user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Update endpoint" : data.asString("message"), null);
					e.updateHead();
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
			.add(new Parameter("message")
				.summary("Message")
				.description("The reason for this action")
				.format(Parameter.Format.TEXT)
				.max(8192)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				synchronized(uniqorn.Endpoint.class)
				{
					uniqorn.Endpoint.Type e = Registry.of(uniqorn.Endpoint.class).get(data.asString("id"));
					if( e == null || !(e instanceof uniqorn.Endpoint.Type) ) throw new HttpException(404, "Unknown endpoint");
					
					Bare.remove(repo().store(), repo().root(), e.valueOf("path").asString(), user.name() + " <" + user.login() + ">", data.isEmpty("message") ? "Remove endpoint" : data.asString("message"), null);
					e.internal(false);
					Registry.of(uniqorn.Endpoint.class).remove(e);
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
				if( e == null || !(e instanceof uniqorn.Endpoint.Type) ) throw new HttpException(404, "Unknown endpoint");
				
				Data endpoint = Data.map().put("id", e.id()).put("enabled", e.valueOf("enabled").asBool());
				
				endpoint.put("head", Data.map()
					.put("id", e.valueOf("sha").asString())
					.put("code", Bare.object(repo().store(), repo().root(),e.valueOf("sha").asString()).b)
					);
				
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
				Bare.history(repo().store(), repo().root(), null, e.valueOf("path").asString()).forEach((h) ->
				{
					versions.add(Data.map()
						.put("author", h.a)
						.put("date", h.b)
						.put("message", h.c)
						.put("id", h.d)
						);
				});
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
				if( e == null || !(e instanceof uniqorn.Endpoint.Type) ) throw new HttpException(404, "Unknown endpoint");
				
				Data versions = Data.list();
				Bare.history(repo().store(), repo().root(), null, e.valueOf("path").asString()).forEach((h) ->
				{
					versions.add(Data.map()
						.put("author", h.a)
						.put("date", h.b)
						.put("message", h.c)
						.put("id", h.d)
						);
				});
					
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
				.rule(Parameter.Rule.ALPHANUM)
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
				if( e == null || !(e instanceof uniqorn.Endpoint.Type) ) throw new HttpException(404, "Unknown endpoint");
				
				Quadruple<String, Long, String, String> version = null;
				for( Quadruple<String, Long, String, String> h : Bare.history(repo().store(), repo().root(), null, e.valueOf("path").asString()) )
				{
					if( h.d.equals(data.asString("id")) )
					{
						version = h;
						break;
					}
				}
				if( version == null ) throw new HttpException(404, "Unknown version");
				
				return Data.map()
					.put("author", version.a)
					.put("date", version.b)
					.put("message", version.c)
					.put("id", version.d)
					.put("code", Bare.object(repo().store(), repo().root(),version.d).b);
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
					if( u.hasRole(Globals.ROLE_CONSUMER) && u.hasRole(role) )
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
					if( u.hasRole(Globals.ROLE_CONSUMER) && u.isMemberOf(group) )
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
				if( currentUser.hasRole(Globals.ROLE_CONTRIBUTOR) )
					data.put("type", "consumer");
					
				Data list = Data.list();
				for( User.Type u : Registry.of(User.class) )
				{
					if( u.internal() ) continue;
					if( u.hasRole(Role.SUPERADMIN) ) continue;
					Data current = Data.map()
						.put("id", u.id())
						.put("name", u.name())
						.put("login", u.login())
						.put("type", 
							u.hasRole(Globals.ROLE_CONSUMER) ? "consumer" :
							u.hasRole(Globals.ROLE_CONTRIBUTOR) ? "contributor" : 
							u.hasRole(Globals.ROLE_MANAGER) ? "manager" : null);
					
					if( data.isEmpty("type") )
					{
						if( u.hasRole(Globals.ROLE_CONSUMER) || u.hasRole(Globals.ROLE_MANAGER) || u.hasRole(Globals.ROLE_CONTRIBUTOR) )
							list.add(current);
					}
					else if( data.asString("type").equals("consumer") )
					{
						if( u.hasRole(Globals.ROLE_CONSUMER) )
							list.add(current);
					}
					else if( data.asString("type").equals("manager") )
					{
						if( u.hasRole(Globals.ROLE_MANAGER) )
							list.add(current);
					}
					else if( data.asString("type").equals("contributor") )
					{
						if( u.hasRole(Globals.ROLE_CONTRIBUTOR) )
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
					if( currentUser.hasRole(Globals.ROLE_CONTRIBUTOR) && !user.hasRole(Globals.ROLE_CONSUMER) )
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
							user.hasRole(Globals.ROLE_CONSUMER) ? "consumer" :
							user.hasRole(Globals.ROLE_CONTRIBUTOR) ? "contributor" : 
							user.hasRole(Globals.ROLE_MANAGER) ? "manager" : null)
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
					String root = Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "storage").asString();
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
				if( data.asString("id").equals(Constants.APP_STORAGE) )
					throw new HttpException(400, "Cannot update app storage");
				
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
				if( data.asString("id").equals(Constants.APP_STORAGE) )
					throw new HttpException(400, "Cannot remove app storage");
					
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
				
				if( data.asString("type").equals("sqlite") )
				{
					// normalize path
					String path = Storage.normalize(data.get("parameters").asString("path"));
					String root = Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "storage").asString();
					if( !path.startsWith(root) ) path = Storage.resolve(root, path);
					data.get("parameters").put("path", path);
				}
				
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
				if( data.asString("id").equals(Constants.LOCAL_DATABASE) )
					throw new HttpException(400, "Cannot update default local database");
				
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
				if( data.asString("id").equals(Constants.LOCAL_DATABASE) )
					throw new HttpException(400, "Cannot remove default local database");
				
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

	// ========================================
	//
	// EXPLORER
	//
	// ========================================

	private static class _Explorer
	{
		private static void register() { }

		// --- Storage browsing ---

		private static final Endpoint.Rest.Type storageTree = new Endpoint.Rest() { }
			.template()
			.summary("Browse storage tree")
			.description("This endpoint lists the contents of a storage directory")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("path")
				.summary("Path")
				.description("The directory path to list")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.defaultValue(""))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
				if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");

				Collection<String> entries = s.tree(Storage.normalize(data.asString("path")));
				Data list = Data.list();
				for( String entry : entries )
				{
					list.add(entry);
				}
				return list;
			})
			.url(ROOT + "/storage/{id}/tree")
			.method("GET")
			;

		private static final Endpoint.Rest.Type storageFileGet = new Endpoint.Rest() { }
			.template()
			.summary("Get storage file")
			.description("This endpoint downloads a file from a storage")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("path")
				.summary("Path")
				.description("The file path")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
				if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");

				String normalized = Storage.normalize(data.asString("path"));
				byte[] bytes = s.get(normalized);
				if( bytes == null ) throw new HttpException(404, "File not found");

				String filename = normalized.contains("/") ? normalized.substring(normalized.lastIndexOf('/') + 1) : normalized;
				filename = filename.replaceAll("[\"\\\\\\r\\n]", "_");
				return Data.map()
					.put("isHttpResponse", true)
					.put("code", 200)
					.put("body", new String(bytes, StandardCharsets.ISO_8859_1))
					.put("mime", "application/octet-stream")
					.put("headers", Data.map()
						.put("Content-Disposition", "attachment; filename=\"" + filename + "\"")
						.put("content-encoding", null));
			})
			.url(ROOT + "/storage/{id}/file")
			.method("GET")
			;

		private static final Endpoint.Rest.Type storageFilePut = new Endpoint.Rest() { }
			.template()
			.summary("Upload storage file")
			.description("This endpoint uploads a file to a storage")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("path")
				.summary("Path")
				.description("The file path")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("file")
				.summary("File")
				.description("The file content")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
				if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");

				byte[] content;
				if( data.isMap("file") )
					content = data.get("file").asString("content").getBytes(StandardCharsets.ISO_8859_1);
				else
					content = data.asString("file").getBytes(StandardCharsets.ISO_8859_1);
				s.put(Storage.normalize(data.asString("path")), content);

				if( data.asString("id").equals(Constants.APP_STORAGE) )
				{
					Bare.createFile(repo().store(), repo().root(),
						"www/" + Storage.normalize(data.asString("path")),
						content, user.name() + " <" + user.login() + ">",
						"Update " + data.asString("path"), null);
				}

				return Data.map().put("success", true);
			})
			.url(ROOT + "/storage/{id}/file")
			.method("POST")
			;

		private static final Endpoint.Rest.Type storageFileDelete = new Endpoint.Rest() { }
			.template()
			.summary("Delete storage file")
			.description("This endpoint removes a file from a storage")
			.add(new Parameter("id")
				.summary("Id")
				.description("The storage id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("path")
				.summary("Path")
				.description("The file path")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				Storage.Type s = Registry.of(Storage.class).get(data.asString("id"));
				if( s == null || !s.type().startsWith("uniqorn.storage.") ) throw new HttpException(404, "Unknown storage");

				s.remove(Storage.normalize(data.asString("path")));

				if( data.asString("id").equals(Constants.APP_STORAGE) )
				{
					Bare.remove(repo().store(), repo().root(),
						"www/" + Storage.normalize(data.asString("path")),
						user.name() + " <" + user.login() + ">",
						"Remove " + data.asString("path"), null);
				}

				return Data.map().put("success", true);
			})
			.url(ROOT + "/storage/{id}/file")
			.method("DELETE")
			;

		// --- Database browsing ---

		private static final Endpoint.Rest.Type databaseTables = new Endpoint.Rest() { }
			.template()
			.summary("List database tables")
			.description("This endpoint returns the list of tables in a database")
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
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				return d.tables();
			})
			.url(ROOT + "/database/{id}/tables")
			.method("GET")
			;

		private static final Endpoint.Rest.Type databaseColumns = new Endpoint.Rest() { }
			.template()
			.summary("Get table columns")
			.description("This endpoint returns the column definitions for a specific table")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				return d.columns(data.asString("table"));
			})
			.url(ROOT + "/database/{id}/columns")
			.method("GET")
			;

		private static final Endpoint.Rest.Type databaseQuery = new Endpoint.Rest() { }
			.template()
			.summary("Query database table")
			.description("This endpoint queries a database table with optional filters, sorting and pagination")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name to query")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("filters")
				.summary("Filters")
				.description("A list of filter objects with column, op, and value properties")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_LIST)
				.optional(true))
			.add(new Parameter("orderBy")
				.summary("Order by")
				.description("The column name to sort by")
				.format(Parameter.Format.TEXT)
				.optional(true))
			.add(new Parameter("orderDir")
				.summary("Order direction")
				.description("The sort direction: ASC or DESC")
				.format(Parameter.Format.TEXT)
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				// get schema and validate table (case-insensitive)
				Data schema = d.schema();
				String requestedTable = data.asString("table");
				String tableName = null;
				Data tableSchema = null;
				for( Data t : schema )
				{
					if( t.asString("name").equalsIgnoreCase(requestedTable) )
					{
						tableName = t.asString("name");
						tableSchema = t;
						break;
					}
				}
				if( tableSchema == null ) throw new HttpException(400, "Unknown table: " + requestedTable);

				// collect valid column names (lowercase key -> canonical name)
				java.util.Map<String, String> columnMap = new java.util.HashMap<>();
				for( Data col : tableSchema.get("columns") )
					columnMap.put(col.asString("name").toLowerCase(), col.asString("name"));

				// build WHERE clause
				StringBuilder where = new StringBuilder();
				List<Object> params = new ArrayList<>();

				if( !data.isEmpty("filters") )
				{
					Set<String> allowedOps = Set.of("=", "!=", "<", ">", "<=", ">=", "LIKE", "IS NULL", "IS NOT NULL");
					for( Data filter : data.get("filters") )
					{
						String canonical = columnMap.get(filter.asString("column").toLowerCase());
						String op = filter.asString("op");

						if( canonical == null ) throw new HttpException(400, "Unknown column: " + filter.asString("column"));
						if( !allowedOps.contains(op) ) throw new HttpException(400, "Invalid operator: " + op);

						if( where.length() > 0 ) where.append(" AND ");

						if( "IS NULL".equals(op) || "IS NOT NULL".equals(op) )
						{
							where.append(canonical).append(" ").append(op);
						}
						else
						{
							where.append(canonical).append(" ").append(op).append(" ?");
							params.add(filter.asString("value"));
						}
					}
				}

				String whereClause = where.length() > 0 ? " WHERE " + where.toString() : "";

				// build ORDER BY
				String orderClause = "";
				if( !data.isEmpty("orderBy") )
				{
					String canonical = columnMap.get(data.asString("orderBy").toLowerCase());
					if( canonical == null ) throw new HttpException(400, "Unknown order column: " + data.asString("orderBy"));
					String dir = data.isEmpty("orderDir") ? "ASC" : data.asString("orderDir");
					if( !"ASC".equals(dir) && !"DESC".equals(dir) ) dir = "ASC";
					orderClause = " ORDER BY " + canonical + " " + dir;
				}

				// data query
				Data rows = d.query("SELECT * FROM " + tableName + whereClause + orderClause, params.toArray());

				return Data.map().put("rows", rows).put("total", rows.size());
			})
			.url(ROOT + "/database/{id}/query")
			.method("POST")
			;

		private static final Endpoint.Rest.Type databaseInsertRow = new Endpoint.Rest() { }
			.template()
			.summary("Insert row")
			.description("This endpoint inserts a row into a database table")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("values")
				.summary("Values")
				.description("The column values as a JSON map")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				Data schema = d.schema();
				String requestedTable = data.asString("table");
				String tableName = null;
				Data tableSchema = null;
				for( Data t : schema )
				{
					if( t.asString("name").equalsIgnoreCase(requestedTable) ) { tableName = t.asString("name"); tableSchema = t; break; }
				}
				if( tableSchema == null ) throw new HttpException(400, "Unknown table: " + requestedTable);

				java.util.Map<String, String> columnMap = new java.util.HashMap<>();
				for( Data col : tableSchema.get("columns") )
					columnMap.put(col.asString("name").toLowerCase(), col.asString("name"));

				Data values = data.get("values");
				StringBuilder cols = new StringBuilder();
				StringBuilder placeholders = new StringBuilder();
				List<Object> params = new ArrayList<>();

				for( Map.Entry<String, Data> entry : values.entrySet() )
				{
					String canonical = columnMap.get(entry.getKey().toLowerCase());
					if( canonical == null ) throw new HttpException(400, "Unknown column: " + entry.getKey());
					if( cols.length() > 0 ) { cols.append(", "); placeholders.append(", "); }
					cols.append(canonical);
					placeholders.append("?");
					params.add(entry.getValue().isNull() ? null : entry.getValue().asString());
				}

				if( params.isEmpty() ) throw new HttpException(400, "No values provided");

				d.query("INSERT INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")", params.toArray());
				return Data.map().put("success", true);
			})
			.url(ROOT + "/database/{id}/row")
			.method("POST")
			;

		private static final Endpoint.Rest.Type databaseUpdateRow = new Endpoint.Rest() { }
			.template()
			.summary("Update row")
			.description("This endpoint updates a row in a database table by primary key")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("values")
				.summary("Values")
				.description("The column values to update as a JSON map")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.add(new Parameter("keys")
				.summary("Keys")
				.description("The primary key values to identify the row as a JSON map")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				Data schema = d.schema();
				String requestedTable = data.asString("table");
				String tableName = null;
				Data tableSchema = null;
				for( Data t : schema )
				{
					if( t.asString("name").equalsIgnoreCase(requestedTable) ) { tableName = t.asString("name"); tableSchema = t; break; }
				}
				if( tableSchema == null ) throw new HttpException(400, "Unknown table: " + requestedTable);

				java.util.Map<String, String> columnMap = new java.util.HashMap<>();
				java.util.Map<String, String> primaryMap = new java.util.HashMap<>();
				for( Data col : tableSchema.get("columns") )
				{
					columnMap.put(col.asString("name").toLowerCase(), col.asString("name"));
					if( col.asBool("primary") ) primaryMap.put(col.asString("name").toLowerCase(), col.asString("name"));
				}

				// Build SET clause
				Data values = data.get("values");
				StringBuilder set = new StringBuilder();
				List<Object> params = new ArrayList<>();

				for( Map.Entry<String, Data> entry : values.entrySet() )
				{
					String canonical = columnMap.get(entry.getKey().toLowerCase());
					if( canonical == null ) throw new HttpException(400, "Unknown column: " + entry.getKey());
					if( set.length() > 0 ) set.append(", ");
					set.append(canonical).append(" = ?");
					params.add(entry.getValue().isNull() ? null : entry.getValue().asString());
				}

				if( params.isEmpty() ) throw new HttpException(400, "No values provided");

				// Build WHERE clause from primary keys
				Data keys = data.get("keys");
				StringBuilder where = new StringBuilder();

				for( Map.Entry<String, Data> entry : keys.entrySet() )
				{
					String canonical = primaryMap.get(entry.getKey().toLowerCase());
					if( canonical == null ) throw new HttpException(400, "Invalid key column: " + entry.getKey());
					if( where.length() > 0 ) where.append(" AND ");
					where.append(canonical).append(" = ?");
					params.add(entry.getValue().isNull() ? null : entry.getValue().asString());
				}

				if( where.length() == 0 ) throw new HttpException(400, "No primary key provided");

				d.query("UPDATE " + tableName + " SET " + set + " WHERE " + where, params.toArray());
				return Data.map().put("success", true);
			})
			.url(ROOT + "/database/{id}/row")
			.method("PUT")
			;

		private static final Endpoint.Rest.Type databaseDeleteRow = new Endpoint.Rest() { }
			.template()
			.summary("Delete row")
			.description("This endpoint deletes a row from a database table by primary key")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.add(new Parameter("keys")
				.summary("Keys")
				.description("The primary key values to identify the row as a JSON map")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_MAP)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				Data schema = d.schema();
				String requestedTable = data.asString("table");
				String tableName = null;
				Data tableSchema = null;
				for( Data t : schema )
				{
					if( t.asString("name").equalsIgnoreCase(requestedTable) ) { tableName = t.asString("name"); tableSchema = t; break; }
				}
				if( tableSchema == null ) throw new HttpException(400, "Unknown table: " + requestedTable);

				java.util.Map<String, String> primaryMap = new java.util.HashMap<>();
				for( Data col : tableSchema.get("columns") )
					if( col.asBool("primary") ) primaryMap.put(col.asString("name").toLowerCase(), col.asString("name"));

				Data keys = data.get("keys");
				StringBuilder where = new StringBuilder();
				List<Object> params = new ArrayList<>();

				for( Map.Entry<String, Data> entry : keys.entrySet() )
				{
					String canonical = primaryMap.get(entry.getKey().toLowerCase());
					if( canonical == null ) throw new HttpException(400, "Invalid key column: " + entry.getKey());
					if( where.length() > 0 ) where.append(" AND ");
					where.append(canonical).append(" = ?");
					params.add(entry.getValue().isNull() ? null : entry.getValue().asString());
				}

				if( where.length() == 0 ) throw new HttpException(400, "No primary key provided");

				d.query("DELETE FROM " + tableName + " WHERE " + where, params.toArray());
				return Data.map().put("success", true);
			})
			.url(ROOT + "/database/{id}/row")
			.method("DELETE")
			;

		private static final Endpoint.Rest.Type databaseExecute = new Endpoint.Rest() { }
			.template()
			.summary("Execute SQL")
			.description("This endpoint executes a raw SQL query on a database. Requires manager role.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("sql")
				.summary("SQL")
				.description("The SQL query to execute")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(10000))
			.create()
			.<Rest.Type>cast()
			.before((data, user) ->
			{
				if( !user.hasRole(Globals.ROLE_MANAGER) )
					throw new HttpException(403, "Manager role required");
			})
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				try
				{
					Data result = d.query(data.asString("sql"));
					return Data.map().put("result", result);
				}
				catch(Exception e)
				{
					return Data.map().put("error", e.getMessage());
				}
			})
			.url(ROOT + "/database/{id}/execute")
			.method("POST")
			;

		private static final Endpoint.Rest.Type databaseDropTable = new Endpoint.Rest() { }
			.template()
			.summary("Drop table")
			.description("This endpoint drops a table from a database. Requires manager role.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The database id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("table")
				.summary("Table")
				.description("The table name to drop")
				.format(Parameter.Format.TEXT)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.before((data, user) ->
			{
				if( !user.hasRole(Globals.ROLE_MANAGER) )
					throw new HttpException(403, "Manager role required");
			})
			.process(data ->
			{
				Database.Type d = Registry.of(Database.class).get(data.asString("id"));
				if( d == null || !d.type().startsWith("uniqorn.database.") ) throw new HttpException(404, "Unknown database");

				// Validate table exists in schema (case-insensitive)
				Data schema = d.schema();
				String requestedTable = data.asString("table");
				String tableName = null;
				for( Data t : schema )
				{
					if( t.asString("name").equalsIgnoreCase(requestedTable) )
					{
						tableName = t.asString("name");
						break;
					}
				}
				if( tableName == null ) throw new HttpException(400, "Unknown table: " + requestedTable);

				try
				{
					d.query("DROP TABLE " + tableName);
					return Data.map().put("success", true);
				}
				catch(Exception e)
				{
					throw new HttpException(500, e.getMessage());
				}
			})
			.url(ROOT + "/database/{id}/table")
			.method("DELETE")
			;
	}
}
