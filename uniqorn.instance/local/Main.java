package local;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import aeonics.Plugin;
import aeonics.data.Data;
import aeonics.entity.Database;
import aeonics.entity.Registry;
import aeonics.entity.Step;
import aeonics.entity.Storage;
import aeonics.entity.Step.Destination;
import aeonics.entity.security.Multifactor;
import aeonics.entity.security.Policy;
import aeonics.entity.security.Role;
import aeonics.entity.security.Rule;
import aeonics.entity.security.User;
import aeonics.manager.*;
import aeonics.manager.Lifecycle.Phase;
import aeonics.manager.Scheduler.Task;
import aeonics.template.Channel;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.Json;
import aeonics.util.StringUtils;
import aeonics.util.Tuples.Tuple;
import aeonics.util.Snapshotable.SnapshotMode;
import aeonics.git.Git;
import aeonics.git.GitRepo;
import aeonics.git.Operations;
import aeonics.http.HttpException;
import aeonics.http.Router;
import aeonics.mcp.Mcp;
import uniqorn.Api;
import uniqorn.Endpoint;
import uniqorn.internal.GitSync;
import uniqorn.internal.Globals;
import uniqorn.internal.UniqornGitRepo;
import uniqorn.internal.UniqornMcp;

public class Main extends Plugin
{
	public String summary() { return "Uniqorn Instance v1.0.0"; }
	public String description() { return "Uniqorn Instance"; }
	
	public void start()
	{
		Lifecycle.on(Phase.LOAD, this::onLoad);
		Lifecycle.on(Phase.CONFIG, this::onConfig);
		Lifecycle.on(Phase.RUN, this::onRun);
		Lifecycle.after(Phase.RUN, this::setupMetrics);
		Lifecycle.after(Phase.RUN, this::setupLogs);
		Lifecycle.after(Phase.RUN, this::recompile);
		Lifecycle.after(Phase.RUN, this::registerApps);
	}
	
	private void onLoad()
	{
	
		Config config = Manager.of(Config.class);
		
		config.declare(Api.class, new Parameter("uid")
			.summary("Instance id")
			.description("The internal instance id.")
			.format(Parameter.Format.TEXT)
			.defaultValue(""));
		config.declare(Api.class, new Parameter("workspaces")
			.summary("Maximum number of workspaces")
			.description("The maximum number of workspaces allowed in this instance.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("endpoints")
			.summary("Maximum number of endpoints")
			.description("The maximum number of endpoints allowed in this instance.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("consumers")
			.summary("Maximum number of consumers")
			.description("The maximum number of consumer users.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("users")
			.summary("Maximum number of panel users")
			.description("The maximum number of controbutor and manager users.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("rate")
			.summary("Maximum call rate")
			.description("The maximum number of API calls per hour, per API.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("env")
			.summary("Maximum env parameters")
			.description("The maximum number of environment parameters.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(0));
		config.declare(Api.class, new Parameter("plan")
			.summary("Subscription name")
			.description("The name of the active subscription for this instance.")
			.format(Parameter.Format.TEXT)
			.defaultValue(""));
		config.declare(Api.class, new Parameter("prefix")
			.summary("User API prefix")
			.description("The prefix to reach user custom APIs.")
			.format(Parameter.Format.TEXT)
			.rule(Parameter.Rule.PATH)
			.defaultValue("/upi"));
		config.declare(Api.class, new Parameter("storage")
			.summary("Local storage path")
			.description("The path to the default local storage location, relative to the root storage.")
			.format(Parameter.Format.TEXT)
			.rule(Parameter.Rule.PATH)
			.defaultValue("storage"));
		config.declare(Api.class, new Parameter("rootstorage")
			.summary("Internal root storage path")
			.description("The full path to the root local storage location.")
			.format(Parameter.Format.TEXT)
			.rule(Parameter.Rule.PATH)
			.defaultValue("storage"));
		config.declare(Api.class, new Parameter("groups")
			.summary("Maximum groups")
			.description("The maximum number of security groups.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(200));
		config.declare(Api.class, new Parameter("roles")
			.summary("Maximum roles")
			.description("The maximum number of security roles.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(200));
		config.declare(Api.class, new Parameter("storages")
			.summary("Maximum storages")
			.description("The maximum number of storages.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(5));
		config.declare(Api.class, new Parameter("databases")
			.summary("Maximum databases")
			.description("The maximum number of databases.")
			.format(Parameter.Format.NUMBER)
			.defaultValue(5));
		config.declare(Api.class, new Parameter("initialized")
			.summary("Internal flag")
			.description("Internal flag to specify that defaults have been initialized.")
			.format(Parameter.Format.BOOLEAN)
			.rule(Parameter.Rule.BOOLEAN)
			.defaultValue(false));
		config.declare(Api.class, new Parameter("safecode")
			.summary("Safe code execution")
			.description("When true, code restrictions apply and published code is checked against a denylist of types "
				+ "at compile time. When false, restrictions are lifted and published code may use any type.")
			.format(Parameter.Format.BOOLEAN)
			.rule(Parameter.Rule.BOOLEAN)
			.defaultValue(true));
		config.declare(Api.class, new Parameter("git")
			.summary("Git root directory")
			.description("Root path of the git repository in the root storage.")
			.format(Parameter.Format.TEXT)
			.defaultValue("git"));
		config.declare(Api.class, new Parameter("apps")
			.summary("Apps root directory")
			.description("Root path of the apps folder in the root storage.")
			.format(Parameter.Format.TEXT)
			.defaultValue("www"));
		config.declare(Api.class, new Parameter("metrics")
			.summary("Metrics directory")
			.description("Root path of the metrics folder in the root storage.")
			.format(Parameter.Format.TEXT)
			.defaultValue("metrics"));
		config.declare(Api.class, new Parameter("logs")
			.summary("Logs directory")
			.description("Root path of the logs folder in the root storage.")
			.format(Parameter.Format.TEXT)
			.defaultValue("logs"));
		config.declare(Api.class, new Parameter("database")
			.summary("Local database path")
			.description("The path to the default local database.")
			.format(Parameter.Format.TEXT)
			.defaultValue("local.db"));
	}
	
	private void onConfig()
	{
		new uniqorn.Router().template().create().<uniqorn.Router.Type>cast().prefix(Manager.of(Config.class).get(Api.class, "prefix").asString());
		Manager.of(Config.class).watch(Api.class, "rate", (name, data) -> { uniqorn.Router.limit = data.asLong(); });

		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", Globals.ROLE_MANAGER))
			.name("Uniqorn Manager").internal(true).snapshotMode(SnapshotMode.NONE);
		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", Globals.ROLE_CONTRIBUTOR))
			.name("Uniqorn Contributor").internal(true).snapshotMode(SnapshotMode.NONE);
		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", Globals.ROLE_CONSUMER))
			.name("Uniqorn Consumer").internal(true).snapshotMode(SnapshotMode.NONE);
		
		try {
			Factory.of(Storage.class).get(aeonics.entity.Storage.File.class).create(Data.map().put("id", Constants.ROOT_STORAGE).put("parameters", 
				Data.map().put("root", Manager.of(Config.class).get(Api.class, "rootstorage").asString())))
				.name("Root Storage").internal(true).snapshotMode(SnapshotMode.NONE);
		} catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }
		
		try {
			Factory.of(Storage.class).get(uniqorn.storage.File.class).create(Data.map().put("id", Constants.LOCAL_STORAGE).put("parameters", 
				Data.map().put("root", Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "storage").asString())))
				.name("Local Storage").internal(true).snapshotMode(SnapshotMode.NONE)
				.<Storage.Type>cast().put(".empty", "");
		} catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }
		
		try {
			Class.forName("org.sqlite.JDBC"); 
			Factory.of(Database.class).get(uniqorn.database.Sqlite.class).create(Data.map().put("id", Constants.LOCAL_DATABASE).put("parameters",
				Data.map().put("path", Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "database").asString())))
				.name("Local Database").internal(true).snapshotMode(SnapshotMode.NONE);
		}
		catch (ClassNotFoundException e) { /* skip the local database */ }
		catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }

		try {
			Factory.of(Storage.class).get(uniqorn.storage.File.class).create(Data.map().put("id", Constants.APP_STORAGE).put("parameters",
				Data.map().put("root", Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "apps").asString())))
				.name("www").internal(true).snapshotMode(SnapshotMode.NONE);
		} catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }
	}
	
	private void onRun()
	{
		ContributorEndpoints.register();
		ManagerEndpoints.register();

		// Create the Uniqorn git repository entity
		Storage.Type gitStorage = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
		String gitRoot = Manager.of(Config.class).get(Api.class, "git").asString();
		if( !gitStorage.containsPath(gitRoot) )
		{
			Operations.init(gitStorage, gitRoot);
			Manager.of(Logger.class).warning(Git.class, "Initialized bare Git repository");
		}
		Factory.of(GitRepo.class).get(UniqornGitRepo.class).create(Data.map()
			.put("id", Constants.GIT_REPO)
			.put("parameters", Data.map().put("root", gitRoot)))
			.name("uniqorn")
			.internal(true)
			.snapshotMode(SnapshotMode.NONE)
			.addRelation("storage", gitStorage);
		Registry.of(GitRepo.class).get(Constants.GIT_REPO).<UniqornGitRepo.Type>cast().seed();

		// Create the Uniqorn MCP provider entity
		Factory.of(Mcp.class).get(UniqornMcp.class).create(Data.map()
			.put("id", Constants.MCP))
			.name("uniqorn")
			.internal(true)
			.snapshotMode(SnapshotMode.NONE);

		// Set up App Storage for static assets
		GitSync.appStorage = Registry.of(Storage.class).get(Constants.APP_STORAGE);

		// Initial sync of apps files from git to disk
		try
		{
			String www = Manager.of(Config.class).get(Api.class, "apps").asString();
			for( String path : aeonics.git.Bare.list(gitStorage, gitRoot, null) )
			{
				if( path.startsWith("/" + www + "/") && !path.endsWith("/") )
				{
					byte[] content = aeonics.git.Bare.file(gitStorage, gitRoot, path);
					if( content != null && GitSync.appStorage != null )
						GitSync.appStorage.put(path.substring(5), content);
				}
			}
		} catch (Exception e) { Manager.of(Logger.class).warning(Api.class, "Could not extract initial apps files: " + e.getMessage()); }

		// every 1h
		Manager.of(Scheduler.class).every(Task.of("Uniqorn Maintenance Ticker", (now) -> 
		{
			// reset call counters
			for( Endpoint.Type e : Registry.of(Endpoint.class) )
				if( e != null )
					e.counter().set(0);
			
			// remove old snapshots
			String last = Manager.of(Snapshot.class).latest();
			if( last != null )
			{
				for( String snapshot : Manager.of(Snapshot.class).list() )
				{
					if( !last.equals(snapshot) )
						Manager.of(Snapshot.class).remove(snapshot);
				}
			}
			
			// create snapshot
			Manager.of(Snapshot.class).create("auto");
		}), 1, ChronoUnit.HOURS);
		
		setDefaultsIfNeeded();
		setStaticAssetsRouting();
		AdminEndpoints.register();
		
		User.Type admin = Registry.of(User.class).get((u) -> u.login().equals(Manager.of(Config.class).get(Security.class, "defaultadmin").asString()));
		if( admin != null && !admin.hasRole(Globals.ROLE_MANAGER) )
			admin.addRelation("roles", Globals.ROLE_MANAGER);
	}
	
	private void registerApps()
	{
		aeonics.http.Endpoint.Type ep = Registry.of(aeonics.http.Endpoint.class).get("POST /api/admin/oidc/app");
		if( ep == null )
		{
			Manager.of(Logger.class).warning(Api.class, "App registration endpoint unavailable; /panel app not registered");
			return;
		}

		try
		{
			ep.<aeonics.http.Endpoint.Rest.Type>cast().process(Data.map()
				.put("client_id", Constants.PANEL_CLIENT_ID)
				.put("redirect_uri", "/panel")
				.put("name", "Instance Panel"));
		}
		catch(Exception e)
		{
			Manager.of(Logger.class).warning(Api.class, "Could not register /panel app: " + e.getMessage());
		}
	}

	private void setDefaultsIfNeeded()
	{
		Config c = Manager.of(Config.class);
		if( !c.get(Api.class, "initialized").asBool() )
		{
			Role.Type manager = Registry.of(Role.class).get(Globals.ROLE_MANAGER);
			Role.Type contributor = Registry.of(Role.class).get(Globals.ROLE_CONTRIBUTOR);
			
			// restrict access to contributor api
			Policy.Type policy = new Policy.Deny().template().create(Data.map().put("parameters", Data.map().put("scope", "http")));
			policy.name("Restrict contributor apis");
			policy.addRelation("rule", new Rule.And().template().create()
				.addRelation("rules", new Rule.MatchContext().template().create(Data.map().put("parameters", Data.map().put("property", "path").put("value", ContributorEndpoints.ROOT + "/#").put("wildcard", true))))
				.addRelation("rules", new Rule.Not().template().create().addRelation("rule", new Rule.Role().template().create(Data.map().put("parameters", Data.map().put("role", manager.id())))))
				.addRelation("rules", new Rule.Not().template().create().addRelation("rule", new Rule.Role().template().create(Data.map().put("parameters", Data.map().put("role", contributor.id()))))));
			
			// restrict access to manager api
			Policy.Type policy2 = new Policy.Deny().template().create(Data.map().put("parameters", Data.map().put("scope", "http")));
			policy2.name("Restrict manager apis");
			policy2.addRelation("rule", new Rule.And().template().create()
				.addRelation("rules", new Rule.MatchContext().template().create(Data.map().put("parameters", Data.map().put("property", "path").put("value", ManagerEndpoints.ROOT + "/#").put("wildcard", true))))
				.addRelation("rules", new Rule.Not().template().create().addRelation("rule", new Rule.Role().template().create(Data.map().put("parameters", Data.map().put("role", manager.id()))))));
			
			// set MFA groups
			for( Multifactor.Type m : Registry.of(Multifactor.class) )
				m.addRelation("roles", manager).addRelation("roles", contributor);
			
			// flag as initialized
			c.set(Api.class, "initialized", true);
		}
	}
	
	private void setStaticAssetsRouting()
	{
		aeonics.http.Endpoint.Type userAssets = new aeonics.http.Endpoint.File().template().create()
			.snapshotMode(SnapshotMode.NONE)
			.addRelation("storage", Registry.of(Storage.class).get(Constants.APP_STORAGE))
			.cast();
		Registry.of(aeonics.http.Endpoint.class).remove(userAssets);
		userAssets.internal(true);
		
		// get the default https router by known id
		Router.Type router = Registry.of(Step.class).get("10000000-1b00000000000000");
		if( router == null )
		{
			Manager.of(Logger.class).warning(Api.class, "Default router for user static assets could not be set.");
			return;
		}
		
		router.onNotFound(message ->
		{
			String path = message.content().asString("path");
			if( path.equals("/ae") || path.startsWith("/ae/") 
				|| path.equals("/oauth") || path.startsWith("/oauth/")
				|| path.equals("/panel") || path.startsWith("/panel/")
				|| path.equals("/.well-known") || path.startsWith("/.well-known") )
				return null;
			
			try
			{
				return userAssets.process(message);
			}
			catch(HttpException e)
			{
				// change the path to hardcoded default 404.html
				message.content().put("path", "/404.html");
				return userAssets.process(message).put("code", 404);
			}
		});
	}
	
	/**
	 * The packages safe code may reach. Everything else is refused by absence, which is what keeps a
	 * new JDK release or a newly exported plugin package from silently widening the surface.
	 * <p>
	 * Matching is on the exact package, never a prefix, so a subpackage is only reachable once it is
	 * listed here in its own right. The JDK entries are limited to value computation because
	 * {@code java.base} is the only JDK module the generated module reads. The platform entries are
	 * the ones {@link Endpoint.Type#IMPORTS} injects into every endpoint, minus those refused below.
	 */
	private static final Set<String> ALLOWED_PACKAGES = Set.of(
		"java.lang", "java.lang.annotation", "java.lang.runtime",
		// required by every lambda and every string concatenation, refined in DENIED_MEMBERS
		"java.lang.invoke",
		"java.math", "java.text", "java.nio.charset",
		"java.time", "java.time.chrono", "java.time.format", "java.time.temporal", "java.time.zone",
		"java.util", "java.util.concurrent.atomic", "java.util.function", "java.util.regex", "java.util.stream",
		"aeonics.data", "aeonics.entity", "aeonics.entity.security", "aeonics.util",
		"uniqorn"
	);

	/**
	 * The members refused inside an allowed package, matched by prefix on {@code package.Class.member}.
	 * <p>
	 * An allowed package is not uniformly safe: capability sits on individual members, and the types
	 * carrying it cannot simply be dropped from {@link #ALLOWED_PACKAGES} because the same types are
	 * needed for ordinary code. {@code java.lang.Class} is named by every {@code Registry.of(X.class)}
	 * and every {@code getClass()}, {@code java.lang.System} carries both {@code currentTimeMillis}
	 * and {@code load}, and {@code java.lang.invoke} is named by every lambda. An entry without a
	 * member denies a type outright, and denies its nested types with it since those are spelled with
	 * a dollar sign rather than a dot.
	 */
	private static final String[] DENIED_MEMBERS = {
		// reflection: the entry points that turn a Class token into a live member
		"java.lang.Class.forName", "java.lang.Class.newInstance",
		"java.lang.Class.getMethod", "java.lang.Class.getDeclaredMethod",
		"java.lang.Class.getField", "java.lang.Class.getDeclaredField",
		"java.lang.Class.getConstructor", "java.lang.Class.getDeclaredConstructor",
		"java.lang.Class.getClasses", "java.lang.Class.getDeclaredClasses",
		"java.lang.Class.getClassLoader", "java.lang.Class.getModule", "java.lang.Class.getProtectionDomain",
		"java.lang.Class.getNestHost", "java.lang.Class.getNestMembers", "java.lang.Class.getPermittedSubclasses",
		"java.lang.Class.getRecordComponents", "java.lang.Class.getDeclaringClass",
		"java.lang.Class.getEnclosingClass", "java.lang.Class.getEnclosingMethod", "java.lang.Class.getEnclosingConstructor",
		"java.lang.Class.getResource",
		// method handles: LambdaMetafactory and StringConcatFactory stay reachable, the lookup does not
		"java.lang.invoke.MethodHandles", "java.lang.invoke.MethodHandleProxies",
		"java.lang.invoke.MethodHandle.invoke", "java.lang.invoke.VarHandle",
		"java.lang.invoke.ConstantBootstraps", "java.lang.invoke.SerializedLambda",
		// native, process and runtime control
		"java.lang.System.load", "java.lang.System.exit", "java.lang.System.getenv",
		"java.lang.System.getProp", "java.lang.System.set",
		"java.lang.Runtime", "java.lang.Process",
		// class loading and module introspection
		"java.lang.ClassLoader", "java.lang.Module",
		"java.lang.SecurityManager", "java.lang.StackWalker",
		"java.lang.Throwable.getStackTrace", "java.lang.StackTraceElement",
		"java.util.ServiceLoader", "java.util.ResourceBundle",
		// threads
		"java.lang.Thread", "java.lang.ScopedValue", "java.util.Timer",
		// framework internals and the entities carrying file, network and thread capability
		"aeonics.entity.Registry", "aeonics.entity.Entity", "aeonics.entity.Step",
		"aeonics.entity.Storage$File", "aeonics.entity.Storage$Memory", "aeonics.entity.Storage$Database"
	};

	/**
	 * Tells whether safe code may reach the given type.
	 * @param type the type name in dot form
	 * @param module the generated module holding the compiled code, whose own types are always allowed
	 * @return whether the type is reachable
	 */
	private static boolean isAllowed(String type, String module)
	{
		int i = type.lastIndexOf('.');
		if( i < 0 ) return false;
		String container = type.substring(0, i);
		return container.equals(module) || ALLOWED_PACKAGES.contains(container);
	}

	private void recompile()
	{
		Config config = Manager.of(Config.class);
		String plan = config.get(Api.class, "plan").asString();
		boolean safe = config.get(Api.class, "safecode").asBool() || !Constants.DEDICATED_PLANS.contains(plan);
		if( safe )
		{
			aeonics.jit.policy.Policy.Type policy = new aeonics.jit.policy.Policy().template().create(Data.map())
				.internal(true)
				.<aeonics.jit.policy.Policy.Type>cast();
			policy.inspector(references ->
			{
				StringBuilder refused = new StringBuilder();

				// an interface leaves no trace among the invoked members: it has no constructor to chain
				// to, and a call to an inherited default method is compiled against the implementing class
				for( String type : references.interfaces() )
					if( !isAllowed(type, references.module()) )
						refused.append(refused.length() > 0 ? ", " : "").append(type);

				for( String member : references.invoked() )
				{
					String type = member.substring(0, member.lastIndexOf('.'));
					if( !isAllowed(type, references.module()) )
					{
						refused.append(refused.length() > 0 ? ", " : "").append(type);
						continue;
					}
					for( String denied : DENIED_MEMBERS )
						if( member.startsWith(denied) )
						{
							refused.append(refused.length() > 0 ? ", " : "").append(member);
							break;
						}
				}

				if( refused.length() > 0 )
					throw new HttpException(422, "Use of restricted type: " + refused.toString());
			});
			config.set(Api.class, "policy", policy.id());
		}
		else
			config.set(Api.class, "policy", "");

		// in case we boot from a restore point, then recompile all endpoints
		for( Endpoint.Type e : Registry.of(Endpoint.class) )
		{
			final Endpoint.Type x = e;
			Manager.of(Executor.class).normal(() -> 
			{
				try { x.updateHead(); }
				catch(Exception ex)
				{
					Manager.of(Logger.class).warning(Endpoint.class, "Recompile endpoint {} failed with {}", x.id(), e);
				}
			});
		}
		
		try { GitSync.resync(); }
		catch(Exception e) { /* ignore */ }
	}
	
	private void setupMetrics()
	{
		if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
			return;
		
		Step.Type previous = Registry.of(Step.class).get("10000000-2000000000000000"); // default monitor scheduled task
		if( previous == null )
		{
			Manager.of(Logger.class).warning(Api.class, "Metrics data producer is not available");
			return;
		}
		
		final Object lock = new Object();
		Destination.Type d = new Destination() { }
			.template()
			.summary("Metrics Aggregator")
			.<Destination.Template>cast()
			.input(new Channel("data").summary("Data"))
			.create()
			.internal(true)
			.snapshotMode(SnapshotMode.NONE)
			.<Destination.Type>cast()
			.processor((message, input) ->
			{
				try
				{
					synchronized(lock)
					{
						Data data = message.content();
						if( !data.isMap() || data.isEmpty() ) return;

						long from = data.asLong("_from");
						data = data.get(Globals.MONITOR_CATEGORY);
						if( data.isEmpty() ) return;
						data.put("_from", from);

						ZonedDateTime time = Instant.ofEpochMilli(from).atZone(ZoneOffset.UTC);
						String year = String.format("%04d", time.getYear());
						String month = String.format("%02d", time.getMonthValue());
						String day = String.format("%02d", time.getDayOfMonth());
						
						Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
						String path = Manager.of(Config.class).get(Api.class, "metrics").asString() + "/" + year + "/" + month + "/" + day + ".jz";
						byte[] zip = s.get(path);
						Data daily = zip == null ? Data.list() : Json.decode(StringUtils.decompress(zip));
						daily.add(data);
						zip = StringUtils.compress(daily.toString());
						s.put(path, zip);
					}
				}
				catch(Exception e)
				{
					Manager.of(Logger.class).warning(Monitor.class, e);
				}
			});
		previous.link("metrics", d, "data");
		
		Manager.of(Config.class).set(Monitor.class, "enabled", true);
	}
	
	private void setupLogs()
	{
		if( Manager.of(Config.class).get(Api.class, "plan").asString().equals(Constants.PLAN_TRIAL) )
			return;
		
		Step.Type previous = Registry.of(Step.class).get("10000000-1500000000000000"); // default logger origin
		if( previous == null )
		{
			Manager.of(Logger.class).warning(Api.class, "Logger origin is not available");
			return;
		}
		
		Tuple<String, BufferedWriter> output = Tuple.of(null, null);
		Destination.Type logToFile = new Destination() { }
			.template()
			.summary("Log to file")
			.<Destination.Template>cast()
			.input(new Channel("data").summary("Data"))
			.create()
			.internal(true)
			.snapshotMode(SnapshotMode.NONE)
			.<Destination.Type>cast()
			.processor((message, input) ->
			{
				try
				{
					if( message == null || !message.content().asString("type").equals(Api.class.getName()) )
						return;
					
					ZonedDateTime now = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneOffset.UTC);
					String name = String.format("%04d", now.getYear()) + "-" + 
							String.format("%02d", now.getMonthValue()) + "-" + 
							String.format("%02d", now.getDayOfMonth()) + ".log";
					
					synchronized(output)
					{	
						if( output.a == null || !output.a.equals(name) || output.b == null )
						{
							Files.createDirectories(
								Path.of(Manager.of(Config.class).get(Api.class, "rootstorage").asString(),
								Manager.of(Config.class).get(Api.class, "logs").asString())
								);
							if( output.b != null ) output.b.close();
							output.b = new BufferedWriter(new FileWriter(
								Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + 
								Manager.of(Config.class).get(Api.class, "logs").asString() + "/" +
								name, true
								));
							output.a = name;
						}
						
						try
						{
							output.b.write(message.content().toString());
							output.b.newLine();
							output.b.flush();
						}
						catch(IOException e)
						{
							// retry once
							Files.createDirectories(
								Path.of(Manager.of(Config.class).get(Api.class, "rootstorage").asString(),
								Manager.of(Config.class).get(Api.class, "logs").asString())
								);
							output.b = new BufferedWriter(new FileWriter(
								Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + 
								Manager.of(Config.class).get(Api.class, "logs").asString() + "/" +
								name, true
								));
							output.b.write(message.content().toString());
							output.b.newLine();
						}
					}
				}
				catch(Exception e)
				{
					e.printStackTrace();
				}
			});
		
		previous.link("data", logToFile, "data");
		
		Manager.of(Scheduler.class).every(Task.of("Uniqorn Log Rotate", (now) -> 
		{
			try
			{
				synchronized(output)
				{
					// rotate logs
					Storage.Type s = Registry.of(Storage.class).get(Constants.ROOT_STORAGE);
					Collection<String> files = s.tree(Manager.of(Config.class).get(Api.class, "logs").asString() + "/");
					for( String file : files )
					{
						String zname = StringUtils.substring(file, 0, -4) + ".zip";
						if( file.endsWith(".log") && !s.containsEntry(zname) )
						{
							if( output.b != null ) { output.b.close(); output.b = null; }
							
							ByteArrayOutputStream out = new ByteArrayOutputStream();
							try( ZipOutputStream zip = new ZipOutputStream(out) )
							{
								zip.putNextEntry(new ZipEntry(file));
								zip.write(s.get(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + file));
								zip.finish();
							}
							s.put(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + zname, out.toByteArray());
							s.remove(Manager.of(Config.class).get(Api.class, "logs").asString() + "/" + file);
						}
					}
				}
				Manager.of(Logger.class).info(Api.class, "Logs rotated");
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
		}), 1, ChronoUnit.DAYS, ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS));
		
		Manager.of(Config.class).set(Monitor.class, "enabled", true);
	}
}
