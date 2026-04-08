package local;

import java.util.Map;

import aeonics.entity.Database;
import aeonics.entity.Storage;

public class Constants
{
	public static final String PLAN_TRIAL = "trial";
	public static final String PLAN_PERSONAL = "personal";
	public static final String PLAN_TEAM = "team";
	public static final String PLAN_ENTERPRISE = "enterprise";
	public static final String PLAN_CUSTOM = "custom";

	public static final String ROOT_STORAGE = "22200000-2500000000000000";
	public static final String LOCAL_STORAGE = "22200000-2400000000000000";
	public static final String LOCAL_DATABASE = "22200000-2600000000000000";
	public static final String GIT_REPO = "22200000-2700000000000000";
	public static final String MCP = "22200000-2800000000000000";
	public static final String APP_STORAGE = "22200000-2900000000000000";

	static final Map<String, Class<? extends Storage>> STORAGES = Map.of(
		"gpc", Storage.class,
		"aws", uniqorn.storage.AWS.class,
		"ms", Storage.class,
		"s3", Storage.class,
		"file", uniqorn.storage.File.class
	);

	static final Map<String, Class<? extends Database>> DATABASES = Map.of(
		"pgsql", uniqorn.database.Pgsql.class,
		"mariadb", uniqorn.database.Mariadb.class,
		"sqlite", uniqorn.database.Sqlite.class
	);
}
