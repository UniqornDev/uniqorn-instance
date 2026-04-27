package local;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import aeonics.Boot;
import aeonics.data.Data;
import aeonics.entity.Database;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.entity.Step;
import aeonics.entity.Step.Origin;
import aeonics.entity.Storage;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.http.Endpoint.Rest;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Scheduler;
import aeonics.manager.Security;
import aeonics.manager.Snapshot;
import aeonics.entity.security.*;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.StringUtils;
import aeonics.util.Http;
import aeonics.util.Json;
import aeonics.util.Snapshotable.SnapshotMode;
import aeonics.util.Tuples.Tuple;
import aeonics.git.GitRepo;
import aeonics.git.Operations;
import uniqorn.Api;
import uniqorn.Workspace;
import uniqorn.internal.UniqornGitRepo;

@SuppressWarnings("unused")
public class ManagerEndpoints 
{
	static final String ROOT = "/api/manager";
	
	private ManagerEndpoints() { /* no instances */ }
	
	public static void register()
	{
		_System.register();
		_Role.register();
		_Group.register();
		_User.register();
		_Metrics.register();
		_Logs.register();
		_Git.register();
	}
	
	// ========================================
	//
	// SYSTEM
	//
	// ========================================
	
	private static class _System
	{
		private static void register() { }
	}
	
	// ========================================
	//
	// ROLE
	//
	// ========================================
	
	private static class _Role
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type roleCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create role")
			.description("This endpoint creates a security role")
			.add(new Parameter("name")
				.summary("Name")
				.description("The role name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Role.class)
				{
					if( Registry.of(Role.class).size() >= Manager.of(Config.class).get(Api.class, "roles").asInt() )
						throw new HttpException(429, "Maximum number of roles reached");
					
					if( Registry.of(Role.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate role name");
					
					Role.Type role = Factory.of(Role.class).get(Role.class).create()
						.name(data.asString("name"));
					
					return Data.map().put("id", role.id());
				}
			})
			.url(ROOT + "/role")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type roleUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update role")
			.description("This endpoint updates a security role")
			.add(new Parameter("id")
				.summary("Id")
				.description("The role id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("Name")
				.description("The role name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Role.class)
				{
					Role.Type role = Registry.of(Role.class).get(data.asString("id"));
					if( role == null || role.internal() ) throw new HttpException(404, "Unknown role");
					
					if( !data.isEmpty("name") )
					{
						if( Registry.of(Role.class).get(data.asString("name")) != null &&  Registry.of(Role.class).get(data.asString("name")) != role )
							throw new HttpException(400, "Duplicate role name");
						role.name(data.asString("name"));
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/role/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type roleDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove role")
			.description("This endpoint removes a security role")
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
				synchronized(_Role.class)
				{
					Role.Type role = Registry.of(Role.class).get(data.asString("id"));
					if( role == null || role.internal() ) throw new HttpException(404, "Unknown role");
					
					Registry.of(Role.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/role/{id}")
			.method("DELETE")
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
		
		private static final Endpoint.Rest.Type groupCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create group")
			.description("This endpoint creates a security group")
			.add(new Parameter("name")
				.summary("Name")
				.description("The group name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Group.class)
				{
					if( Registry.of(Group.class).size() >= Manager.of(Config.class).get(Api.class, "groups").asInt() )
						throw new HttpException(429, "Maximum number of groups reached");
					
					if( Registry.of(Group.class).get(data.asString("name")) != null )
						throw new HttpException(400, "Duplicate group name");
					
					Group.Type group = Factory.of(Group.class).get(Group.class).create()
						.name(data.asString("name"));
					
					return Data.map().put("id", group.id());
				}
			})
			.url(ROOT + "/group")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type groupUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update group")
			.description("This endpoint updates a security group")
			.add(new Parameter("id")
				.summary("Id")
				.description("The group id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("name")
				.summary("Name")
				.description("The group name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_Group.class)
				{
					Group.Type group = Registry.of(Group.class).get(data.asString("id"));
					if( group == null || group.internal() ) throw new HttpException(404, "Unknown group");
					
					if( !data.isEmpty("name") )
					{
						if( Registry.of(Group.class).get(data.asString("name")) != null &&  Registry.of(Group.class).get(data.asString("name")) != group )
							throw new HttpException(400, "Duplicate group name");
						group.name(data.asString("name"));
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/group/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type groupDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove group")
			.description("This endpoint removes a security group")
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
				synchronized(_Group.class)
				{
					Group.Type group = Registry.of(Group.class).get(data.asString("id"));
					if( group == null || group.internal() ) throw new HttpException(404, "Unknown group");
					
					Registry.of(Group.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/group/{id}")
			.method("DELETE")
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
		
		private static final Endpoint.Rest.Type userCreate = new Endpoint.Rest() { }
			.template()
			.summary("Create user")
			.description("This endpoint creates a security user")
			.add(new Parameter("login")
				.summary("Login")
				.description("The user login name")
				.format(Parameter.Format.TEXT)
				.optional(false)
				.min(3)
				.max(50))
			.add(new Parameter("name")
				.summary("Display name")
				.description("The user display name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(100))
			.add(new Parameter("type")
				.summary("Type")
				.description("The user type")
				.format(Parameter.Format.TEXT)
				.rule((v) -> v.equals("consumer") || v.equals("manager") || v.equals("contributor") )
				.values("consumer", "manager", "contributor")
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					if( data.asString("type").equals("consumer") )
					{
						if( Registry.of(User.class).filter(u -> u.hasRole(uniqorn.internal.Globals.ROLE_CONSUMER)).size() >= Manager.of(Config.class).get(Api.class, "consumers").asInt() )
							throw new HttpException(429, "Maximum number of consumer users reached");
						
						// consumer have random login
						data.put("login", Manager.of(Security.class).randomHash());
					}
					else
					{
						if( Registry.of(User.class).filter(u -> !u.hasRole(Role.SUPERADMIN) && (u.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) || u.hasRole(uniqorn.internal.Globals.ROLE_CONTRIBUTOR))).size() >= Manager.of(Config.class).get(Api.class, "users").asInt() )
							throw new HttpException(429, "Maximum number of users reached");
					}
					
					if( Registry.of(User.class).get(u -> u.name().equals(data.asString("login")) || u.login().equals(data.asString("login"))) != null )
						throw new HttpException(400, "Duplicate user");
					
					User.Type user = Factory.of(User.class).get(User.class).create()
						.name(data.asString("name"))
						.parameter("login", data.asString("login"));
					
					if( data.asString("type").equals("consumer") )
						user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_CONSUMER));
					else if( data.asString("type").equals("contributor") )
						user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_CONTRIBUTOR)).addRelation("groups", Group.USERS);
					else if( data.asString("type").equals("manager") )
						user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_MANAGER)).addRelation("groups", Group.USERS);
					
					return Data.map().put("id", user.id());
				}
			})
			.url(ROOT + "/user")
			.method("POST")
			;
		
		private static final Endpoint.Rest.Type userUpdate = new Endpoint.Rest() { }
			.template()
			.summary("Update user")
			.description("This endpoint updates a security user")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("login")
				.summary("Login")
				.description("The user login name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.min(3)
				.max(50))
			.add(new Parameter("name")
				.summary("Display name")
				.description("The user display name")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(100))
			.add(new Parameter("type")
				.summary("Type")
				.description("The user type")
				.format(Parameter.Format.TEXT)
				.rule((v) -> v.isEmpty() || v.equals("manager") || v.equals("contributor") )
				.values("manager", "contributor")
				.optional(true))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					
					if( !data.isEmpty("login") && (user.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) || user.hasRole(uniqorn.internal.Globals.ROLE_CONTRIBUTOR)) )
					{
						if( Registry.of(User.class).get(data.asString("login")) != null &&  Registry.of(User.class).get(data.asString("login")) != user )
							throw new HttpException(400, "Duplicate user login");
						
						user.parameter("login", data.asString("login"));
					}
					
					if( !data.isEmpty("type") && (user.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) || user.hasRole(uniqorn.internal.Globals.ROLE_CONTRIBUTOR)) )
					{
						// check for last manager
						if( user.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) &&
							!data.asString("type").equals("manager") && 
							Registry.of(User.class).filter(u -> u.hasRole(uniqorn.internal.Globals.ROLE_MANAGER)).size() == 1 )
							throw new HttpException(400, "Cannot change level of last manager");
						
						user.removeRelation("roles", uniqorn.internal.Globals.ROLE_MANAGER);
						user.removeRelation("roles", uniqorn.internal.Globals.ROLE_CONTRIBUTOR);
						
						if( data.asString("type").equals("contributor") )
							user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_CONTRIBUTOR));
						else if( data.asString("type").equals("manager") )
							user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_MANAGER));
					}
					
					if( !data.isEmpty("name") )
						user.name(data.asString("name"));
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/user/{id}")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type userDelete = new Endpoint.Rest() { }
			.template()
			.summary("Remove user")
			.description("This endpoint removes a security user")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() ) return Data.map().put("success", true);
					
					if( user.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) && Registry.of(User.class).filter(u -> u.hasRole(uniqorn.internal.Globals.ROLE_MANAGER)).size() == 1 )
						throw new HttpException(400, "Cannot remove the last manager");
					
					Registry.of(User.class).remove(user);
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/user/{id}")
			.method("DELETE")
			;
			
		private static final Endpoint.Rest.Type userGroups = new Endpoint.Rest() { }
			.template()
			.summary("Set user groups")
			.description("This endpoint sets the security groups associated with a consumer user")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("groups")
				.summary("Groups")
				.description("The list of groups")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_LIST)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() || !user.hasRole(uniqorn.internal.Globals.ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					user.clearRelation("groups");
					for( Data name : data.get("groups") )
					{
						Group.Type g = Registry.of(Group.class).get(name.asString());
						if( g != null && !g.internal() )
							user.addRelation("groups", g);
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/user/{id}/groups")
			.method("PUT")
			;
		
		private static final Endpoint.Rest.Type userRoles = new Endpoint.Rest() { }
			.template()
			.summary("Set user roles")
			.description("This endpoint sets the security roles associated with a consumer user")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.add(new Parameter("roles")
				.summary("Roles")
				.description("The list of roles")
				.format(Parameter.Format.JSON)
				.rule(Parameter.Rule.JSON_LIST)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() || !user.hasRole(uniqorn.internal.Globals.ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					user.clearRelation("roles");
					
					// CAUTION : we need to re-set the ROLE_CONSUMER !
					user.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_CONSUMER));
					
					for( Data name : data.get("roles") )
					{
						Role.Type r = Registry.of(Role.class).get(name.asString());
						if( r != null && !r.internal() )
							user.addRelation("roles", r);
					}
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/user/{id}/roles")
			.method("PUT")
			;
			
		private static final Endpoint.Rest.Type userKey = new Endpoint.Rest() { }
			.template()
			.summary("Get user key")
			.description("This endpoint returns the bearer token of a consumer user")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() || !user.hasRole(uniqorn.internal.Globals.ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					Collection<Token> tokens = Manager.of(Security.class).listTokens(user);
					String value = tokens.stream().findFirst().map(t -> t.value()).orElse(null);
					
					return Data.map().put("token", value);
				}
			})
			.url(ROOT + "/user/{id}/key")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type userReset = new Endpoint.Rest() { }
			.template()
			.summary("Password reset")
			.description("This endpoint performs a password reset and OTP reset on the target user. All user tokens are invalidated.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					if( !user.hasRole(uniqorn.internal.Globals.ROLE_CONTRIBUTOR) && !user.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) ) throw new HttpException(404, "Unknown user");

					// find the local provider
					Provider.Type provider = Registry.of(Provider.class).get(p -> p.type().equals(StringUtils.toLowerCase(Provider.Local.class)));
					if( provider == null ) throw new HttpException(500, "Security provider unavailable");
					
					// leave and rejoin
					provider.leave(user);
					String password = Manager.of(Security.class).randomHash();
					provider.join(Data.map().put("password", password).put("username", user.login()), user);
					
					// invalidate all tokens
					Manager.of(Security.class).clearTokens(user);
					
					// remove OTP
					for( Multifactor.Type m : Registry.of(Multifactor.class) )
						m.forget(user);
					
					return Data.map().put("password", password);
				}
			})
			.url(ROOT + "/user/{id}/password")
			.method("PATCH")
			;
			
		private static final Endpoint.Rest.Type userRotate = new Endpoint.Rest() { }
			.template()
			.summary("Rotate user key")
			.description("This endpoint rotates the bearer token of a consumer user. All previous keys are invalidated.")
			.add(new Parameter("id")
				.summary("Id")
				.description("The user id")
				.format(Parameter.Format.TEXT)
				.rule(Parameter.Rule.ID)
				.optional(false))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() || !user.hasRole(uniqorn.internal.Globals.ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					Manager.of(Security.class).clearTokens(user);
					Token token = Manager.of(Security.class).generateToken(user, -1, true, "http", "topic", "mcp");
					
					return Data.map().put("token", token.value());
				}
			})
			.url(ROOT + "/user/{id}/key")
			.method("PATCH")
			;
	}
	
	// ========================================
	//
	// METRICS
	//
	// ========================================
	
	private static class _Metrics
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type metricsClear = new Endpoint.Rest() { }
			.template()
			.summary("Clear metrics")
			.description("This endpoint removes all metrics before a given date.")
			.add(new Parameter("until")
				.summary("Date")
				.description("The date timestamp in milliseconds (exclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				synchronized(_Metrics.class)
				{
					long from = data.asLong("until");
					ZonedDateTime time = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
					int year = time.getYear();
					int month = time.getMonthValue();
					int day = time.getDayOfMonth();
					
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					
					Collection<String> years = s.tree(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/");
					for( String y : years )
					{
						if( !y.endsWith("/") || y.length() != 5 || !StringUtils.isInteger(y.substring(0, 4)) ) continue;
						int sy = Integer.parseInt(y.substring(0, 4));
						
						if( sy < year )
							s.remove(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + y);
						else if( sy == year )
						{
							Collection<String> months = s.tree(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + y);
							for( String m : months )
							{
								if( !m.endsWith("/") || m.length() != 3 || !StringUtils.isInteger(m.substring(0, 2)) ) continue;
								int sm = Integer.parseInt(m.substring(0, 2));
								
								if( sm < month )
									s.remove(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + y + "/" + m);
								else if( sm == month )
								{
									Collection<String> days = s.tree(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + y + "/" + m);
									for( String d : days )
									{
										if( !d.endsWith(".jz") || d.length() != 5 || !StringUtils.isInteger(d.substring(0, 2)) ) continue;
										int sd = Integer.parseInt(d.substring(0, 2));
										
										if( sd < day )
											s.remove(Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + y + "/" + m + "/" + d);
									}
								}
							}
						}
					}
					Manager.of(Logger.class).info(Api.class, "Metrics cleared by " + user.login());
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/metrics")
			.method("DELETE")
			;
		
		private static final Endpoint.Rest.Type metricsDownload = new Endpoint.Rest() { }
			.template()
			.summary("Download metrics")
			.description("This endpoint returns a zip file of metrics between two dates.")
			.add(new Parameter("from")
				.summary("From")
				.description("The start date timestamp in milliseconds (inclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.add(new Parameter("to")
				.summary("To")
				.description("The end date timestamp in milliseconds (inclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				synchronized(_Metrics.class)
				{
					long from = data.asLong("from");
					ZonedDateTime time_from = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
					int year_from = time_from.getYear();
					int month_from = time_from.getMonthValue();
					int day_from = time_from.getDayOfMonth();
					
					long to = data.asLong("to");
					ZonedDateTime time_to = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC);
					int year_to = time_to.getYear();
					int month_to = time_to.getMonthValue();
					int day_to = time_to.getDayOfMonth();
					
					if( from > to ) throw new HttpException(400, "Invalid date range");
					
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					
					ByteArrayOutputStream file = new ByteArrayOutputStream();
					try( ZipOutputStream zip = new ZipOutputStream(file) )
					{
						for( int y = year_from; y <= year_to; y++ )
						{
							int mf = y == year_from ? month_from : 1;
							int mt = y == year_to ? month_to : 12;
							for( int m = mf; m <= mt; m++ )
							{
								int df = y == year_from && m == month_from ? day_from : 1;
								int dt = y == year_to && m == month_to ? day_to : 31;
								for( int d = df; d <= dt; d++ )
								{
									String path = Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + 
										String.format("%04d", y) + "/" + 
										String.format("%02d", m) + "/" + 
										String.format("%02d", d) + ".jz";
									byte[] raw = s.get(path);
									if( raw == null ) continue;
									
									zip.putNextEntry(new ZipEntry(String.format("%04d", y)+"-"+String.format("%02d", m)+"-"+String.format("%02d", d)+".json"));
									zip.write(StringUtils.decompress(raw).getBytes(StandardCharsets.UTF_8));
								}
							}
						}
						zip.finish();
						
						return Data.map()
							.put("isHttpResponse", true)
							.put("code", 200)
							.put("body", new String(file.toByteArray(), StandardCharsets.ISO_8859_1))
							.put("mime", "application/zip")
							.put("headers", Data.map().put("Content-Disposition", "attachment; filename=\"metrics.zip\""));
					}
				}
			})
			.url(ROOT + "/metrics/download")
			.method("GET")
			;
		
		private static final Endpoint.Rest.Type metricsFetch = new Endpoint.Rest() { }
			.template()
			.summary("Fetch metrics")
			.description("This endpoint returns all metrics between two dates.")
			.add(new Parameter("from")
				.summary("From")
				.description("The start date timestamp in milliseconds (inclusive).")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.add(new Parameter("to")
				.summary("To")
				.description("The end date timestamp in milliseconds (inclusive).")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				synchronized(_Metrics.class)
				{
					long from = data.asLong("from");
					ZonedDateTime time_from = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
					int year_from = time_from.getYear();
					int month_from = time_from.getMonthValue();
					int day_from = time_from.getDayOfMonth();
					
					long to = data.asLong("to");
					ZonedDateTime time_to = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC);
					int year_to = time_to.getYear();
					int month_to = time_to.getMonthValue();
					int day_to = time_to.getDayOfMonth();
					
					if( from > to ) throw new HttpException(400, "Invalid date range");
					if( (to - from) > 8640000000L ) throw new HttpException(400, "Date range too large. Max 100 days.");
					
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					Data metrics = Data.list();
					
					for( int y = year_from; y <= year_to; y++ )
					{
						int mf = y == year_from ? month_from : 1;
						int mt = y == year_to ? month_to : 12;
						for( int m = mf; m <= mt; m++ )
						{
							int df = y == year_from && m == month_from ? day_from : 1;
							int dt = y == year_to && m == month_to ? day_to : 31;
							for( int d = df; d <= dt; d++ )
							{
								String path = Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + 
									String.format("%04d", y) + "/" + 
									String.format("%02d", m) + "/" + 
									String.format("%02d", d) + ".jz";
								byte[] zip = s.get(path);
								if( zip == null ) continue;
								Data daily = Json.decode(StringUtils.decompress(zip));
								for( Data x : daily )
								{
									if( x.asLong("_from") >= from && x.asLong("_from") <= to )
										metrics.add(x);
								}
							}
						}
					}
					
					return metrics;
				}
			})
			.url(ROOT + "/metrics")
			.method("GET")
			;
	}

	// ========================================
	//
	// LOGS
	//
	// ========================================
	
	private static class _Logs
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type logsClear = new Endpoint.Rest() { }
			.template()
			.summary("Clear logs")
			.description("This endpoint removes all logs before a given date.")
			.add(new Parameter("until")
				.summary("Date")
				.description("The date timestamp in milliseconds (exclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.create()
			.<Rest.Type>cast()
			.process((data, user) ->
			{
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				synchronized(_Logs.class)
				{
					long from = data.asLong("until");
					ZonedDateTime time = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
					String ref = String.format("%04d", time.getYear()) + "-" + 
						String.format("%02d", time.getMonthValue()) + "-" + 
						String.format("%02d", time.getDayOfMonth());
					
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					
					Collection<String> files = s.tree(Manager.of(Config.class).get(Api.class, "logs").asString() + "/");
					for( String file : files )
					{
						if( file.endsWith("/") ) continue;
						if( ref.compareTo(file.substring(0, file.indexOf("."))) >= 0 )
						{
							if( file.endsWith(".log") )
								s.put(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + file, "");
							else
								s.remove(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + file);
						}
					}
					Manager.of(Logger.class).info(Api.class, "Logs cleared by " + user.login());
					
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/logs")
			.method("DELETE")
			;
		
		private static final Endpoint.Rest.Type logsDownload = new Endpoint.Rest() { }
			.template()
			.summary("Download logs")
			.description("This endpoint returns a zip file of logs between two dates.")
			.add(new Parameter("from")
				.summary("From")
				.description("The start date timestamp in milliseconds (inclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.add(new Parameter("to")
				.summary("To")
				.description("The end date timestamp in milliseconds (inclusive). Hours and smaller units are ignored as if they were zero.")
				.format(Parameter.Format.NUMBER)
				.optional(false)
				.max(20))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
					throw new HttpException(429, "Not available in current plan");
				
				synchronized(_Logs.class)
				{
					long from = data.asLong("from");
					ZonedDateTime time_from = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
					String ref_from = String.format("%04d", time_from.getYear()) + "-" + 
						String.format("%02d", time_from.getMonthValue()) + "-" + 
						String.format("%02d", time_from.getDayOfMonth());
					
					long to = data.asLong("to");
					ZonedDateTime time_to = Instant.ofEpochMilli(to).atZone(ZoneOffset.UTC);
					String ref_to = String.format("%04d", time_to.getYear()) + "-" + 
						String.format("%02d", time_to.getMonthValue()) + "-" + 
						String.format("%02d", time_to.getDayOfMonth());
					
					if( from > to ) throw new HttpException(400, "Invalid date range");
					
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					
					ByteArrayOutputStream file = new ByteArrayOutputStream();
					try( ZipOutputStream zip = new ZipOutputStream(file) )
					{
						Collection<String> files = s.tree(Manager.of(Config.class).get(Api.class, "logs").asString() + "/");
						for( String f : files )
						{
							if( f.endsWith("/") ) continue;
							int dot = f.indexOf(".");
							if( dot <= 0 ) continue;
							String date = f.substring(0, dot);
							if( ref_from.compareTo(date) > 0 ) continue;
							if( ref_to.compareTo(date) < 0 ) continue;
							
							if( f.endsWith(".log") )
							{
								zip.putNextEntry(new ZipEntry(f));
								zip.write(s.get(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + f));
							}
							else if( f.endsWith(".zip") )
							{
								try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(s.get(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + f))))
								{
									ZipEntry e = zin.getNextEntry();
									zip.putNextEntry(new ZipEntry(date + ".log"));
									zin.transferTo(zip);
								}
							}
						}
						zip.finish();
						
						return Data.map()
							.put("isHttpResponse", true)
							.put("code", 200)
							.put("body", new String(file.toByteArray(), StandardCharsets.ISO_8859_1))
							.put("mime", "application/zip")
							.put("headers", Data.map().put("Content-Disposition", "attachment; filename=\"logs.zip\""));
					}
				}
			})
			.url(ROOT + "/logs/download")
			.method("GET")
			;
	}

	// ========================================
	//
	// GIT
	//
	// ========================================

	private static class _Git
	{
		private static void register() { }

		private static final Endpoint.Rest.Type flatten = new Endpoint.Rest() { }
			.template()
			.summary("Flatten git history")
			.description("This endpoint squashes the entire git history into a single commit. The working tree is unchanged.")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				try
				{
					GitRepo.Type repo = Registry.of(GitRepo.class).get(Constants.GIT_REPO);
					Operations.flatten(repo.store(), repo.root(), "tmp/" + repo.root());
					return Data.map().put("success", true);
				}
				catch(Exception e)
				{
					throw new HttpException(500, e.getMessage());
				}
			})
			.url(ROOT + "/git/flatten")
			.method("POST")
			;

		private static final Endpoint.Rest.Type reset = new Endpoint.Rest() { }
			.template()
			.summary("Reset git repository")
			.description("This endpoint wipes and reinitializes the git repository. All endpoint code is permanently deleted.")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				try
				{
					UniqornGitRepo.Type repo = Registry.of(GitRepo.class).get(Constants.GIT_REPO).<UniqornGitRepo.Type>cast();
					Operations.reset(repo.store(), repo.root());
					repo.seed();

					Registry.of(uniqorn.Endpoint.class).clear();
					Registry.of(Workspace.class).clear();

					Storage.Type appStore = Registry.of(Storage.class).get(Constants.APP_STORAGE);
					if( appStore != null )
					{
						appStore.remove("");
						// Re-extract www files from the re-initialized repo
						for( String path : aeonics.git.Bare.list(repo.store(), repo.root(), null) )
						{
							if( path.startsWith("/www/") && !path.endsWith("/") )
							{
								byte[] content = aeonics.git.Bare.file(repo.store(), repo.root(), path);
								if( content != null ) appStore.put(path.substring(5), content);
							}
						}
					}

					return Data.map().put("success", true);
				}
				catch(Exception e)
				{
					throw new HttpException(500, e.getMessage());
				}
			})
			.url(ROOT + "/git/reset")
			.method("POST")
			;
	}
}
