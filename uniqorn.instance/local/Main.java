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
				.name("Local Storage").internal(true).snapshotMode(SnapshotMode.NONE);
		} catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }
		
		try {
			Factory.of(Database.class).get(uniqorn.database.Sqlite.class).create(Data.map().put("id", Constants.LOCAL_DATABASE).put("parameters",
				Data.map().put("path", Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "database").asString())))
				.name("Local Database").internal(true).snapshotMode(SnapshotMode.NONE);
		} catch (Exception e) { Manager.of(Logger.class).severe(Api.class, e); }

		try {
			Factory.of(Storage.class).get(uniqorn.storage.File.class).create(Data.map().put("id", Constants.APP_STORAGE).put("parameters",
				Data.map().put("root", Manager.of(Config.class).get(Api.class, "rootstorage").asString() + "/" + Manager.of(Config.class).get(Api.class, "apps").asString())))
				.name("App Storage").internal(true).snapshotMode(SnapshotMode.NONE);
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

		new aeonics.http.Endpoint.File().template().create()
			.url("/app/")
			.internal(true).snapshotMode(SnapshotMode.NONE)
			.addRelation("storage", Registry.of(Storage.class).get(Constants.APP_STORAGE));

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
		
		AdminEndpoints.register();
		
		User.Type admin = Registry.of(User.class).get((u) -> u.login().equals(Manager.of(Config.class).get(Security.class, "defaultadmin").asString()));
		if( admin != null && !admin.hasRole(Globals.ROLE_MANAGER) )
			admin.addRelation("roles", Globals.ROLE_MANAGER);
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
	
	private void recompile()
	{
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
							s.put(zname, out.toByteArray());
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
