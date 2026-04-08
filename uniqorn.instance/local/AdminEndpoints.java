package local;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import aeonics.data.Data;
import aeonics.entity.Database;
import aeonics.entity.Registry;
import aeonics.entity.Step;
import aeonics.entity.Storage;
import aeonics.entity.Step.Origin;
import aeonics.entity.security.Group;
import aeonics.entity.security.Multifactor;
import aeonics.entity.security.Provider;
import aeonics.entity.security.Role;
import aeonics.entity.security.User;
import aeonics.http.HttpException;
import aeonics.http.Endpoint;
import aeonics.http.Endpoint.Rest;
import aeonics.manager.Config;
import aeonics.manager.Executor;
import aeonics.manager.Manager;
import aeonics.manager.Scheduler;
import aeonics.manager.Security;
import aeonics.manager.Snapshot;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.Http;
import aeonics.util.StringUtils;

@SuppressWarnings("unused")
public class AdminEndpoints
{
	public static void register()
	{
		/* nothing to do here */
	}
	
	private static void checkCredentials(String password, String mfa)
	{
		Provider.Type provider = Registry.of(Provider.class).get(p -> p.type().equals(StringUtils.toLowerCase(Provider.Local.class)));
		if( provider == null ) throw new HttpException(500, "Security provider unavailable");
		
		User.Type u = provider.authenticate(Data.map().put("username", Manager.of(Config.class).get(Security.class, "defaultadmin")).put("password", password));
		if( u == null || !u.hasRole(Role.SUPERADMIN) ) throw new HttpException(400, "Invalid credentials");
		
		boolean pass = false;
		for( Multifactor.Type m : Registry.of(Multifactor.class) )
		{
			if( m.check(u, Data.map().put("otp", mfa)) )
			{
				pass = true;
				break;
			}
		}
		if( !pass ) throw new HttpException(400, "Invalid multifactor confirmation");
	}
	
	private static final Endpoint.Rest.Type account = new Endpoint.Rest() { }
		.template()
		.summary("Create account")
		.description("This endpoint can be used to create a manager account.")
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
			.optional(false)
			.max(100))
		.add(new Parameter("mfa")
			.summary("Multifactor")
			.description("The multifactor check")
			.format(Parameter.Format.TEXT)
			.optional(false)
			.max(100))
		.create()
		.<Rest.Type>cast()
		.process((data, user, request) ->
		{
			String auth = request.content().get("headers").asString("authorization");
			if( !auth.startsWith("Bearer ") ) throw new HttpException(401, "Unauthorized");
			checkCredentials(auth.substring(7), data.asString("mfa"));
			
			for( User.Type u : Registry.of(User.class) )
			{
				if( u.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) )
					throw new HttpException(400, "Already initialized");
			}
			
			if( Registry.of(User.class).get(u -> u.name().equals(data.asString("login")) || u.login().equals(data.asString("login"))) != null )
				throw new HttpException(400, "Duplicate user");
			
			Provider.Type provider = Registry.of(Provider.class).get(p -> p.type().equals(StringUtils.toLowerCase(Provider.Local.class)));
			
			User.Type manager = Factory.of(User.class).get(User.class).create()
				.name(data.asString("name"))
				.parameter("login", data.asString("login"))
				.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_MANAGER))
				.addRelation("groups", Group.USERS)
				.cast()
				;
			
			String password = Manager.of(Security.class).randomHash();
			provider.join(Data.map().put("password", password).put("username", manager.login()), manager);
			
			return Data.map().put("success", true).put("password", password);
		})
		.url("/account")
		.method("POST")
		;
	
	private static final Endpoint.Rest.Type init = new Endpoint.Rest() { }
		.template()
		.summary("Initialize")
		.description("This endpoint can be used to create the initial user account.")
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
			.optional(false)
			.max(100))
		.add(new Parameter("mfa")
			.summary("Multifactor")
			.description("The multifactor check")
			.format(Parameter.Format.TEXT)
			.optional(false)
			.max(100))
		.create()
		.<Rest.Type>cast()
		.process((data, user, request) ->
		{
			String auth = request.content().get("headers").asString("authorization");
			if( !auth.startsWith("Bearer ") ) throw new HttpException(401, "Unauthorized");
			checkCredentials(auth.substring(7), data.asString("mfa"));
			
			for( User.Type u : Registry.of(User.class) )
			{
				if( u.hasRole(uniqorn.internal.Globals.ROLE_MANAGER) )
					throw new HttpException(400, "Already initialized");
			}
			
			if( Registry.of(User.class).get(u -> u.name().equals(data.asString("login")) || u.login().equals(data.asString("login"))) != null )
				throw new HttpException(400, "Duplicate user");
			
			Provider.Type provider = Registry.of(Provider.class).get(p -> p.type().equals(StringUtils.toLowerCase(Provider.Local.class)));
			
			User.Type manager = Factory.of(User.class).get(User.class).create()
				.name(data.asString("name"))
				.parameter("login", data.asString("login"))
				.addRelation("roles", Registry.of(Role.class).get(uniqorn.internal.Globals.ROLE_MANAGER))
				.addRelation("groups", Group.USERS)
				.cast()
				;
			
			String password = Manager.of(Security.class).randomHash();
			provider.join(Data.map().put("password", password).put("username", manager.login()), manager);
			
			// just make sure everything is clean
			Path root = Path.of("/storage");
			try( Stream<Path> files = Files.walk(root) )
			{
				files
					.filter((p) -> !p.equals(root))
			        .map(Path::toFile)
			        .sorted(Comparator.reverseOrder())
			        .forEach(java.io.File::delete);
			}
			
			// do a snapshot now
			Manager.of(Snapshot.class).create("auto");
			
			return Data.map().put("success", true).put("password", password);
		})
		.url("/init")
		.method("POST")
		;
		
	private static final Endpoint.Rest.Type dns = new Endpoint.Rest() { }
		.template()
		.summary("Update DNS")
		.description("Update the OIDC issuer to match the new instance DNS and take a snapshot.")
		.add(new Parameter("dns")
			.summary("DNS")
			.description("The new instance DNS")
			.format(Parameter.Format.TEXT)
			.optional(false)
			.min(3)
			.max(200))
		.add(new Parameter("mfa")
			.summary("Multifactor")
			.description("The multifactor check")
			.format(Parameter.Format.TEXT)
			.optional(false)
			.max(100))
		.create()
		.<Rest.Type>cast()
		.process((data, user, request) ->
		{
			String auth = request.content().get("headers").asString("authorization");
			if( !auth.startsWith("Bearer ") ) throw new HttpException(401, "Unauthorized");
			checkCredentials(auth.substring(7), data.asString("mfa"));

			Manager.of(Config.class).set(Security.class, "oidcissuer", "https://" + data.asString("dns"));
			Manager.of(Snapshot.class).create("auto");

			return Data.map().put("success", true);
		})
		.url("/dns")
		.method("POST")
		;

	private static final Endpoint.Rest.Type destroy = new Endpoint.Rest() { }
		.template()
		.summary("Clear instance")
		.description("Completely clear this instance and all local data.")
		.add(new Parameter("mfa")
			.summary("Multifactor")
			.description("The multifactor check")
			.format(Parameter.Format.TEXT)
			.optional(false)
			.max(100))
		.create()
		.<Rest.Type>cast()
		.process((data, user, request) ->
		{
			String auth = request.content().get("headers").asString("authorization");
			if( !auth.startsWith("Bearer ") ) throw new HttpException(401, "Unauthorized");
			checkCredentials(auth.substring(7), data.asString("mfa"));
			
			// prevent snapshots
			Registry.of(Scheduler.Cron.class).clear();
			
			// clear the root storage
			Registry.of(Storage.class).get(Constants.ROOT_STORAGE).remove("");
			
			return Data.map().put("success", true);
		})
		.url("/clear")
		.method("DELETE")
		;
}
