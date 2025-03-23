package local;

import java.time.temporal.ChronoUnit;

import aeonics.Plugin;
import aeonics.data.Data;
import aeonics.entity.Registry;
import aeonics.entity.Storage;
import aeonics.entity.security.Multifactor;
import aeonics.entity.security.Policy;
import aeonics.entity.security.Role;
import aeonics.entity.security.Rule;
import aeonics.manager.*;
import aeonics.manager.Lifecycle.Phase;
import aeonics.template.Factory;
import aeonics.template.Parameter;
import aeonics.util.Snapshotable.SnapshotMode;
import uniqorn.Api;
import uniqorn.Endpoint;
import uniqorn.Version;
import uniqorn.Workspace;

public class Main extends Plugin
{
	public String summary() { return "Uniqorn instance v0.1"; }
	public String description() { return "Uniqorn REST API instance"; }
	
	public void start()
	{
		Lifecycle.on(Phase.LOAD, this::onLoad);
		Lifecycle.on(Phase.CONFIG, this::onConfig);
		Lifecycle.on(Phase.RUN, this::onRun);
	}
	
	private void onLoad()
	{
		Factory.add(new Workspace());
		Factory.add(new Version());
		Factory.add(new Endpoint());
		Factory.add(new Router());
	
		Config config = Manager.of(Config.class);
		
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
		config.declare(Api.class, new Parameter("versions")
			.summary("Maximum number of versions")
			.description("The maximum number of versions allowed for each API.")
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
		config.declare(Api.class, new Parameter("initialized")
			.summary("Internal flag")
			.description("Internal flag to specify that defaults have been initialized.")
			.format(Parameter.Format.BOOLEAN)
			.rule(Parameter.Rule.BOOLEAN)
			.optional(true)
			.defaultValue(false));
	}
	
	private void onConfig()
	{
		new Router().template().create().<Router.Type>cast().prefix(Manager.of(Config.class).get(Api.class, "prefix").asString());
		Manager.of(Config.class).watch(Api.class, "rate", (name, data) -> { Router.limit = data.asLong(); });
		
		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", ManagerEndpoints.ROLE_MANAGER))
			.name("Uniqorn Manager").internal(true).snapshotMode(SnapshotMode.NONE);
		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", ManagerEndpoints.ROLE_CONTRIBUTOR))
			.name("Uniqorn Contributor").internal(true).snapshotMode(SnapshotMode.NONE);
		Factory.of(Role.class).get(Role.class).create(Data.map().put("id", ManagerEndpoints.ROLE_CONSUMER))
			.name("Uniqorn Consumer").internal(true).snapshotMode(SnapshotMode.NONE);
	}
	
	private void onRun()
	{
		ContributorEndpoints.register();
		ManagerEndpoints.register();
		
		
		// reset call counters
		Manager.of(Scheduler.class).every((now) -> 
		{
			for( Endpoint.Type e : Registry.of(Endpoint.class) )
				if( e != null )
					e.counter().set(0);
		}, 1, ChronoUnit.HOURS);
		
		setDefaultsIfNeeded();
	}
	
	private void setDefaultsIfNeeded()
	{
		Config c = Manager.of(Config.class);
		if( !c.get(Api.class, "initialized").asBool() )
		{
			Role.Type manager = Registry.of(Role.class).get(ManagerEndpoints.ROLE_MANAGER);
			Role.Type contributor = Registry.of(Role.class).get(ManagerEndpoints.ROLE_CONTRIBUTOR);
			
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
			
			// set and save the monitoring storage
			Storage.Type monitor = new Storage.File().template().create(Data.map().put("parameters", Data.map().put("root", "stats"))).name("Monitor statistics");
			c.set(Monitor.class, "storage", Data.of(monitor.id()));
			
			// set and save the security storage
			Storage.Type securityStorage = new Storage.File().template().create(Data.map().put("parameters", Data.map().put("root", "security"))).name("Security storage");
			c.set(Security.class, "token.storage", Data.of(securityStorage.id()));
			
			// set MFA groups
			for( Multifactor.Type m : Registry.of(Multifactor.class) )
				m.addRelation("roles", manager).addRelation("roles", contributor);
			
			// flag as initialized
			c.set(Api.class, "initialized", true);
		}
	}
}
