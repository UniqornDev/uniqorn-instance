package local;

import java.util.Map;

import aeonics.entity.Database;
import aeonics.entity.Storage;

public class Constants
{
	static final String PLAN_TRIAL = "trial";
	static final String PLAN_PERSONAL = "personal";
	static final String PLAN_TEAM = "team";
	static final String PLAN_ENTERPRISE = "enterprise";
	static final String PLAN_CUSTOM = "custom";
	
	static final String ROLE_MANAGER = "22200000-2100000000000000";
	static final String ROLE_CONTRIBUTOR = "22200000-2200000000000000";
	static final String ROLE_CONSUMER = "22200000-2300000000000000";
	
	static final String LOCAL_STORAGE = "22200000-2400000000000000";
	
	static final Map<String, Class<? extends Storage>> STORAGES = Map.of(
		"gpc", Storage.class,
		"aws", Storage.class,
		"ms", Storage.class,
		"s3", Storage.class,
		"file", uniqorn.storage.File.class
	);
	
	static final Map<String, Class<? extends Database>> DATABASES = Map.of(
		"pgsql", uniqorn.database.Pgsql.class,
		"mariadb", uniqorn.database.Mariadb.class
	);
}
