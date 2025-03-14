package local;

import java.util.Collection;

import aeonics.Boot;
import aeonics.data.Data;
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
import uniqorn.Api;

@SuppressWarnings("unused")
public class ManagerEndpoints 
{
	static final String ROOT = "/api/manager";
	static final String ROLE_MANAGER = "Uniqorn Manager";
	static final String ROLE_CONTRIBUTOR = "Uniqorn Contributor";
	static final String ROLE_CONSUMER = "Uniqorn API Consumer";
	
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
					if( role == null ) throw new HttpException(404, "Unknown role");
					
					if( !data.isNull("name") )
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
					Registry.of(Role.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/role/{id}")
			.method("DELETE")
			;
			
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
					if( group == null ) throw new HttpException(404, "Unknown group");
					
					if( !data.isNull("name") )
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
					Registry.of(Group.class).remove(data.asString("id"));
					return Data.map().put("success", true);
				}
			})
			.url(ROOT + "/group/{id}")
			.method("DELETE")
			;
			
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
			.add(new Parameter("firstname")
				.summary("Firstname")
				.description("The user firstname")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.add(new Parameter("lastname")
				.summary("Lastname")
				.description("The user lastname")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
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
						if( Registry.of(User.class).filter(u -> u.hasRole(ROLE_CONSUMER)).size() >= Manager.of(Config.class).get("keys").asInt() )
							throw new HttpException(429, "Maximum number of api users reached");
					}
					else
					{
						if( Registry.of(User.class).filter(u -> u.hasRole(ROLE_MANAGER) || u.hasRole(ROLE_CONTRIBUTOR)).size() >= Manager.of(Config.class).get("users").asInt() )
							throw new HttpException(429, "Maximum number of users reached");
					}
					
					if( Registry.of(User.class).get(u -> u.name().equals(data.asString("login")) || u.login().equals(data.asString("login"))) != null )
						throw new HttpException(400, "Duplicate user");
					
					User.Type user = Factory.of(User.class).get(User.class).create()
						.name(data.asString("login"));
					user.parameter("attributes", Data.map()
						.put("firstname", data.asString("firstname"))
						.put("lastname", data.asString("lastname")));
					
					// consumers cannot login, so leave their login empty
					if( !data.asString("type").equals("consumer") )
						user.parameter("login", data.asString("login"));
					
					if( data.asString("type").equals("consumer") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONSUMER));
					else if( data.asString("type").equals("contributor") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_CONTRIBUTOR));
					else if( data.asString("type").equals("manager") )
						user.addRelation("roles", Registry.of(Role.class).get(ROLE_MANAGER));
					
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
			.add(new Parameter("firstname")
				.summary("Firstname")
				.description("The user firstname")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.add(new Parameter("lastname")
				.summary("Lastname")
				.description("The user lastname")
				.format(Parameter.Format.TEXT)
				.optional(true)
				.max(50))
			.create()
			.<Rest.Type>cast()
			.process(data ->
			{
				synchronized(_User.class)
				{
					User.Type user = Registry.of(User.class).get(data.asString("id"));
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					
					if( !data.isNull("login") )
					{
						if( Registry.of(User.class).get(data.asString("login")) != null &&  Registry.of(User.class).get(data.asString("login")) != user )
							throw new HttpException(400, "Duplicate user login");
						
						user.name(data.asString("login"));
						if( user.hasRole(ROLE_MANAGER) || user.hasRole(ROLE_CONTRIBUTOR) )
							user.parameter("login", data.asString("login"));
					}
					
					if( !data.isNull("firstname") )
						user.attributes().put("firstname", data.asString("firstname"));
					if( !data.isNull("lastname") )
						user.attributes().put("lastname", data.asString("lastname"));
					
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
			.process(data ->
			{
				Data list = Data.list();
				for( User.Type u : Registry.of(User.class) )
				{
					if( u.internal() ) continue;
					if( data.isEmpty("type") ) list.add(u.export());
					else if( data.asString("type").equals("consumer") )
					{
						if( u.hasRole(ROLE_CONSUMER) )
							list.add(u.export());
					}
					else if( data.asString("type").equals("manager") )
					{
						if( u.hasRole(ROLE_MANAGER) )
							list.add(u.export());
					}
					else if( data.asString("type").equals("contributor") )
					{
						if( u.hasRole(ROLE_CONTRIBUTOR) )
							list.add(u.export());
					}
				}
				
				return list;
			})
			.url(ROOT + "/users")
			.method("GET")
			;
			
		private static final Endpoint.Rest.Type userGroups = new Endpoint.Rest() { }
			.template()
			.summary("Set user groups")
			.description("This endpoint sets the security groups associated with a user")
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
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					
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
			.description("This endpoint sets the security roles associated with a user")
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
					if( user == null || user.internal() ) throw new HttpException(404, "Unknown user");
					
					user.clearRelation("roles");
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
					provider.join(Data.map().put("password", password), user);
					
					// invalidate all tokens
					Manager.of(Security.class).clearTokens(user);
					
					// remove OTP
					for( Multifactor.Type m : Registry.of(Multifactor.class) )
						m.forget(user);
					
					return Data.map().put("password", password);
				}
			})
			.url(ROOT + "/user/{id}/key")
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
