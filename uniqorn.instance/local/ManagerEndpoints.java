package local;

import java.util.Collection;

import aeonics.Boot;
import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Registry;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.http.Endpoint.Rest;
import aeonics.manager.Config;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.manager.Scheduler;
import aeonics.manager.Security;
import aeonics.entity.security.*;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Tuple;
import uniqorn.Api;

@SuppressWarnings("unused")
public class ManagerEndpoints 
{
	static final String ROOT = "/api/manager";
	static final String ROLE_MANAGER = "22200000-2100000000000000";
	static final String ROLE_CONTRIBUTOR = "22200000-2200000000000000";
	static final String ROLE_CONSUMER = "22200000-2300000000000000";
	
	private ManagerEndpoints() { /* no instances */ }
	
	public static void register()
	{
		_System.register();
		_Role.register();
		_Group.register();
		_User.register();
	}
	
	// ========================================
	//
	// SYSTEM
	//
	// ========================================
	
	private static class _System
	{
		private static void register() { }
		
		private static final Endpoint.Rest.Type restart = new Endpoint.Rest() { }
			.template()
			.summary("Reboot instance")
			.description("This endpoint initiates an instance reboot")
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				Manager.of(Scheduler.class).in((time) -> { Boot.MAIN.interrupt(); }, 100);
				return Data.map().put("success", true);
			})
			.url(ROOT + "/reboot")
			.method("GET")
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
					if( Registry.of(Role.class).size() >= 200 )
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
					if( Registry.of(Group.class).size() >= 200 )
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
						if( Registry.of(User.class).filter(u -> u.hasRole(ROLE_CONSUMER)).size() >= Manager.of(Config.class).get(Api.class, "consumers").asInt() )
							throw new HttpException(429, "Maximum number of consumer users reached");
						
						// consumer have random login
						data.put("login", Manager.of(Security.class).randomHash());
					}
					else
					{
						if( Registry.of(User.class).filter(u -> u.hasRole(ROLE_MANAGER) || u.hasRole(ROLE_CONTRIBUTOR)).size() >= Manager.of(Config.class).get(Api.class, "users").asInt() )
							throw new HttpException(429, "Maximum number of users reached");
					}
					
					if( Registry.of(User.class).get(u -> u.name().equals(data.asString("login")) || u.login().equals(data.asString("login"))) != null )
						throw new HttpException(400, "Duplicate user");
					
					User.Type user = Factory.of(User.class).get(User.class).create()
						.name(data.asString("name"))
						.parameter("login", data.asString("login"));
					
					if( data.asString("type").equals("consumer") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONSUMER));
					else if( data.asString("type").equals("contributor") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONTRIBUTOR)).addRelation("groups", Group.USERS);
					else if( data.asString("type").equals("manager") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_MANAGER)).addRelation("groups", Group.USERS);;
					
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
					
					if( !data.isEmpty("login") && (user.hasRole(ROLE_MANAGER) || user.hasRole(ROLE_CONTRIBUTOR)) )
					{
						if( Registry.of(User.class).get(data.asString("login")) != null &&  Registry.of(User.class).get(data.asString("login")) != user )
							throw new HttpException(400, "Duplicate user login");
						
						user.parameter("login", data.asString("login"));
					}
					
					if( !data.isEmpty("type") && (user.hasRole(ROLE_MANAGER) || user.hasRole(ROLE_CONTRIBUTOR)) )
					{
						// check for last manager
						if( user.hasRole(ROLE_MANAGER) &&
							!data.asString("type").equals("manager") && 
							Registry.of(User.class).filter(u -> u.hasRole(ROLE_MANAGER)).size() == 1 )
							throw new HttpException(400, "Cannot change level of last manager");
						
						user.removeRelation("roles", ROLE_MANAGER);
						user.removeRelation("roles", ROLE_CONTRIBUTOR);
						
						if( data.asString("type").equals("contributor") )
							user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONTRIBUTOR));
						else if( data.asString("type").equals("manager") )
							user.addRelation("roles", Registry.of(Role.class).get(ROLE_MANAGER));
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
					
					if( user.hasRole(ROLE_MANAGER) && Registry.of(User.class).filter(u -> u.hasRole(ROLE_MANAGER)).size() == 1 )
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
					if( user == null || user.internal() || !user.hasRole(ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
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
					if( user == null || user.internal() || !user.hasRole(ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					user.clearRelation("roles");
					
					// CAUTION : we need to re-set the ROLE_CONSUMER !
					user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONSUMER));
					
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
					if( user == null || user.internal() || !user.hasRole(ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
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
					if( !user.hasRole(ROLE_CONTRIBUTOR) && !user.hasRole(ROLE_MANAGER) ) throw new HttpException(404, "Unknown user");

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
					if( user == null || user.internal() || !user.hasRole(ROLE_CONSUMER) ) throw new HttpException(404, "Unknown user");
					
					Manager.of(Security.class).clearTokens(user);
					Token token = Manager.of(Security.class).generateToken(user, -1, true, "http", "topic");
					
					return Data.map().put("token", token.value());
				}
			})
			.url(ROOT + "/user/{id}/key")
			.method("PATCH")
			;
	}
}
