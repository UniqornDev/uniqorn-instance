
export default {
	'ok': "OK",
	'cancel': "Cancel",
	'yes': "Yes",
	'no': "No",
	'close': "Close",
	'remove': "Remove",
	'rename': "Rename",
	'edit': "Edit",
	'info': "Details",
	'all': "View all",
	'time': "Time",
	'save': "Save",
	'create': "Create",
	'reset': "Reset",
	'update': "Update",
	'next': "Next",
	'previous': "Previous",
	
	'login.welcome': "Welcome {}",
	'login.no_access': "Unfortunately you do not have access to this application. Please login with another user.",
	'login.required': "Authentication required",
	'login.login': "Login",
	'login.error.fetch': "The required information could not be fetched at this time. Please try again or contact the Uniqorn team.",
	
	'fetch.error': "Communication with the server failed.",
	'fetch.limit': "Quota limit reached.",
	
	'code': "Code",
	'code.sample': "Code Sample",
	'code.doc': "&gt; Related documentation page",
	'code.javadoc': "&gt; Related Javadoc page",
	'code.file.invalid': "Invalid code file",
	'code.security.role': "You can control access to your endpoints by checking the user's role. Grant or deny access based on <em>role name</em>, and combine them with other conditions if needed.",
	'code.security.group': "You can control access to your endpoints by checking the user's group. Grant or deny access based on <em>group name</em>, and combine them with other conditions if needed.",
	'code.security.user': "You can control access to your endpoints by checking the user. Grant or deny access based on <em>user name</em>, and combine them with other conditions if needed. Remember that only <em>consumers</em> have access to your APIs.<br /><br />API consumers must use the <code>Authorization: Bearer [key]</code> header to authenticate when calling your APIs.",
	'code.env': "You can fetch global configuration parameters in your code. When a value needs to change, you do not have to redeploy your APIs.",
	'code.endpoint.documentation': "You can improve your API by providing documentation directly in your code.",
	'code.log': "You can emit logs to help troubleshooting your endpoints. There is no log retention, only live information is streamed to the panel.",
	'code.debug': "You can dump variables and inspect the call stack trace to help troubleshooting your endpoints. There is no retention, only live information is streamed to the panel.",
	'code.storage': "You can fetch and store content from an Object Storage. To ease deployment, you just reference the storage by name.",
	'code.database': "You can fetch and store data from a database using plain SQL. To ease deployment, you just reference the database by name.",
	
	'menu.home': "Overview",
	'menu.security': "Security",
	'menu.env': "Global Variables",
	'menu.endpoints': "Endpoints",
	'menu.storage': "Storage",
	'menu.troubleshoot': "Troubleshooting",
	'menu.doc': "Documentation",
	'menu.javadoc': "Javadoc",
	
	'home.plan': "Plan",
	'home.plan.trial': "Trial",
	'home.plan.personal': "Personal",
	'home.plan.team': "Team",
	'home.plan.enterprise': "Enterprise",
	'home.plan.custom': "Custom",
	'home.limit.workspaces': "Workspaces",
	'home.limit.endpoints': "Endpoints",
	'home.limit.consumers': "Consumers",
	'home.limit.users': "Users",
	'home.limit.groups': "Groups",
	'home.limit.roles': "Roles",
	'home.limit.storages': "Storages",
	'home.limit.databases': "Databases",
	'home.limit.env': "Global Variables",
	'home.limit.versions': "Code Versions",
	'home.limit.rate': "Hourly Calls",
	'home.limit.tooltip.workspaces': "Total number of workspaces",
	'home.limit.tooltip.endpoints': "Total number of API endpoints",
	'home.limit.tooltip.consumers': "Total number of API consumer accounts",
	'home.limit.tooltip.users': "Total number of contributor and manager user accounts",
	'home.limit.tooltip.groups': "Total number of security groups",
	'home.limit.tooltip.roles': "Total number of security roles",
	'home.limit.tooltip.storages': "Total number of storage connections",
	'home.limit.tooltip.databases': "Total number of database connections",
	'home.limit.tooltip.env': "Total number of global environment variables",
	'home.limit.tooltip.versions': "Total number of versions across all endpoints",
	'home.limit.tooltip.rate': "Total API calls this hour across all endpoints",
	'home.warning.rate': "The number of calls for endpoint <em>{}</em> is getting high.",
	'home.warning.versions': "The maximum number of code versions is reached for endpoint <em>{}</em>.",
	
	'security.roles': "Roles",
	'security.roles.explain': "Roles are tags that can be assigned to an API Consumer to enforce security constraints.",
	'security.role.add': "Add Role",
	'security.role.name': "New role name",
	'security.role.empty': "Role name cannot be empty",
	'security.role.success': "Role created",
	'security.role.error': "Could not create role",
	'security.role.users': "Users with this role:",
	'security.role.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove the role <em>{}</em> ?<br />"
		+ "If this role is used in your API code, you'll need to update it.",
	'security.role.delete.success': "Role removed",
	'security.role.delete.error': "Could not remove role",
	'security.role.rename.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />If you change the role name you'll need to update your API code too.",
	'security.role.rename.success': "Role renamed",
	'security.role.rename.error': "Could not rename role",
	'security.groups': "Groups",
	'security.groups.explain': "API Consumers can be organized into groups to enforce security constraints.",
	'security.group.add': "Add Group",
	'security.group.name': "New group name",
	'security.group.empty': "Group name cannot be empty",
	'security.group.success': "Group created",
	'security.group.error': "Could not create group",
	'security.group.users': "Members of this group:",
	'security.group.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove the group <em>{}</em> ?<br />"
		+ "If this group is used in your API code, you'll need to update it.",
	'security.group.delete.success': "Group removed",
	'security.group.delete.error': "Could not remove group",
	'security.group.rename.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />If you change the group name you'll need to update your API code too.",
	'security.group.rename.success': "Group renamed",
	'security.group.rename.error': "Could not rename group",
	'security.consumers': "Consumers",
	'security.consumers.explain': "API Consumer are special users that can access your APIs with an authentication key.",
	'security.consumer.add': "Add Consumer",
	'security.consumer.name': "Name",
	'security.consumer.empty': "Name cannot be empty",
	'security.consumer.success': "Consumer created",
	'security.consumer.error': "Could not create consumer",
	'security.consumer.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove the consumer <em>{}</em> ?<br />"
		+ "If this consumer user is used in your API code, you'll need to update it.",
	'security.consumer.delete.success': "Consumer removed",
	'security.consumer.delete.error': "Could not remove consumer",
	'security.consumer.rename.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />If you change the consumer name you'll need to update your API code too.",
	'security.consumer.rename.success': "Consumer renamed",
	'security.consumer.rename.error': "Could not rename consumer",
	'security.users': "Users",
	'security.users.explain': "Users can login to this panel and perform some actions according to their access level.",
	'security.user.add': "Add User",
	'security.user.login': "Login",
	'security.user.name': "Display name",
	'security.user.type.contributor': "Contributor",
	'security.user.type.manager': "Manager",
	'security.user.type.other': "Other",
	'security.user.empty': "Name and login cannot be empty",
	'security.user.success': "User created",
	'security.user.error': "Could not create user",
	'security.user.delete.confirm': "Are you sure you want to remove user <em>{}</em> ?",
	'security.user.delete.success': "User removed",
	'security.user.delete.error': "Could not remove user",
	'security.user.rename.confirm': "Update user <em>{}</em>:",
	'security.user.rename.success': "User updated",
	'security.user.rename.error': "Could not update user",
	'security.user.info.login': "Login",
	'security.user.info.name': "Display name",
	'security.user.info.level': "Access level",
	'security.user.reset_password': "Reset Password",
	'security.user.reset.confirm': "This operation will generate a new random password and reset multifactor authentication for <em>{}</em>.<br />Proceed ?",
	'security.user.reset.success': "Password reset",
	'security.user.reset.error': "Could not reset password",
	'security.user.reset.result': "The new user password is:<br /><em>{}</em><br />Copy it now because it cannot be recovered later.",
	'security.consumer.groups': "Member of groups:",
	'security.consumer.roles': "Has roles:",
	'security.consumer.reveal': "Show key",
	'security.consumer.reveal.error': "Could not fetch key",
	'security.consumer.reveal.result': "The authentication key is:<br /><em>{}</em>",
	'security.consumer.reveal.empty': "The authentication key is not yet set.",
	'security.consumer.rotate': "Reset key",
	'security.consumer.rotate.confirm': "This operation will generate a new random authentication key for <em>{}</em>. The previous key will no longer be operational effective immediately.<br />Proceed ?",
	'security.consumer.rotate.success': "Key reset",
	'security.consumer.rotate.error': "Could not reset key",
	'security.consumer.rotate.result': "The new authentication key is:<br /><em>{}</em>",
	'security.consumer.delete.confirm': "Are you sure you want to remove consumer <em>{}</em> ?",
	'security.consumer.delete.success': "Consumer removed",
	'security.consumer.delete.error': "Could not remove consumer",
	'security.consumer.rename.confirm': "Rename consumer <em>{}</em>:",
	'security.consumer.rename.success': "Consumer renamed",
	'security.consumer.rename.error': "Could not rename consumer",
	'security.consumer.groups.available': "Groups available",
	'security.consumer.groups.selected': "Selected groups",
	'security.consumer.groups.success': "Groups set",
	'security.consumer.groups.error': "Could not set groups",
	'security.consumer.roles.available': "Roles available",
	'security.consumer.roles.selected': "Selected roles",
	'security.consumer.roles.success': "Roles set",
	'security.consumer.roles.error': "Could not set roles",
	
	'me.logout': "Logout",
	'me.info': "User information",
	'me.info.login': "Login",
	'me.info.name': "Display name",
	'me.info.level': "Access level",
	'me.info.mfa': "Multifactor enabled",
	'me.level.contributor': "Contributor",
	'me.level.manager': "Manager",
	'me.level.other': "Other",
	'me.reset_mfa': "Reset MFA",
	'me.mfa.title': "Multifactor Reset",
	'me.mfa.confirm': "Please confirm that you want to reset your multifactor authentication.<br />Your current session will remain open but you will have to enroll again on next login.",
	'me.mfa.success': "MFA reset",
	'me.mfa.error': "Could not reset MFA",
	'me.reset_password': "Change password",
	'me.password.title': "Password Update",
	'me.password.confirm': "Please enter your new password. Use minimum 10 characters.<br />After changing your password, you will have to login again.",
	'me.password.success': "Password changed",
	'me.password.error': "Could not change password",
	'me.password.first': "New password",
	'me.password.second': "Confirm new password",
	'me.password.empty': "Password cannot be empty",
	'me.password.mismatch': "Password mismatch",
	'me.password.short': "Password too short",
	'me.destroy': "Destroy instance",
	'me.destroy.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />This action will destroy this Uniqorn instance, all endpoints and all local data without any recovery possible."
		+ "<br /><br />Please enter your multifactor code to confirm:",
	'me.destroy.mfa': "Multifactor code",
	'me.destroy.abort': "Multifactor code missing",
	'me.destroy.success': "Instance destroyed",
	'me.destroy.error': "Could not destroy instance",
	
	'env.title': "Global Environment Variables",
	'env.add': "Add variable",
	'env.set': "Set variable",
	'env.explain': "Environment variables are accessible in read-only mode from your code. This is the best way to manage configuration settings in a centralized way.",
	'env.add.explain': "Environment variable names can only be composed of <em>lower case letters</em>, <em>numbers</em>, and the <em>dot</em> character.",
	'env.name': "Variable name",
	'env.description': "Description...",
	'env.value': "Value",
	'env.add.success': "Variable set",
	'env.add.error': "Could not set variable",
	'env.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove the global variable <em>{}</em> ?<br />"
		+ "If this variable is used in your API code, you'll need to update it.",
	'env.delete.success': "Variable removed",
	'env.delete.error': "Could not remove variable",
	
	'endpoints.workspace.add': "New Workspace",
	'endpoints.prefix.global': "URL prefix for all APIs:",
	'endpoints.workspace.name': "Workspace name",
	'endpoints.workspace.prefix': "URL /prefix",
	'endpoints.workspace.empty': "Name cannot be empty",
	'endpoints.workspace.invalid': "No funny URL prefix please!",
	'endpoints.workspace.success': "Workspace created",
	'endpoints.workspace.error': "Could not create workspace",
	'endpoints.workspace.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove the workspace <em>{}</em> ?<br />"
		+ "All endpoints and all versions in this workspace will be removed too.",
	'endpoints.workspace.delete.success': "Workspace removed",
	'endpoints.workspace.delete.error': "Could not remove workspace",
	'endpoints.workspace.edit': "Edit Workspace",
	'endpoints.workspace.edit.success': "Workspace updated",
	'endpoints.workspace.edit.error': "Could not update workspace",
	'endpoints.endpoint.add': "New Endpoint",
	'endpoints.endpoint.code': "Endpoint Source Code",
	'endpoints.endpoint.deploy': "Deploy",
	'endpoints.endpoint.deploy.success': "Endpoint deployed",
	'endpoints.endpoint.deploy.error': "Could not deploy endpoint",
	'endpoints.nopath': '[no path]',
	'endpoints.endpoint.enable.success': "Endpoint enabled",
	'endpoints.endpoint.disable.success': "Endpoint disabled",
	'endpoints.endpoint.enable.error': "Could not enable endpoint",
	'endpoints.endpoint.disable.error': "Could not disable endpoint",
	
	'endpoint.back': "Endpoint list",
	'endpoint.delete': "Delete Endpoint",
	'endpoint.delete.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />Are you sure you want to remove this endpoint ?<br />"
		+ "All versions will be removed too.",
	'endpoint.delete.success': "Endpoint removed",
	'endpoint.delete.error': "Could not remove endpoint",
	'endpoint.documentation': "API Documentation",
	'endpoint.summary': "Summary",
	'endpoint.description': "Description",
	'endpoint.returns': "Return value",
	'endpoint.empty': "Not specified",
	'endpoint.parameters': "Parameters",
	'endpoint.no_parameters': "None",
	'endpoint.versions': "Code Versions",
	'endpoint.head': "Live version (head)",
	'endpoint.modified': "Published {}",
	'endpoint.restore': "Restore",
	'endpoint.view': "View",
	'endpoint.tag': "Tag version",
	'endpoint.version.date': "Created {}",
	'endpoint.version.tag': "Archive the <em>live version</em> of this endpoint in a new version.",
	'endpoint.version.name': "Version name",
	'endpoint.tag.success': "Version created",
	'endpoint.tag.error': "Could not create version",
	'endpoint.version.rename': "Rename code version",
	'endpoint.version.rename.success': "Version renamed",
	'endpoint.version.rename.error': "Could not rename version",
	'endpoint.version.delete.confirm': "Are you sure you want to remove version <em>{}</em> ?",
	'endpoint.version.delete.success': "Version removed",
	'endpoint.version.delete.error': "Could not remove version",
	'endpoint.version.restore.confirm': "Are you sure you want to restore version <em>{}</em> ?<br />This version will become the new live version.",
	'endpoint.version.restore.success': "Version restored",
	'endpoint.version.restore.error': "Could not restore version",
	'endpoint.version.fetch.error': "Could not fetch version",
	'endpoint.head.update': "Update live version",
	'endpoint.head.success': "Live version updated",
	'endpoint.head.error': "Could not update live version",
	
	'troubleshoot.log': "Live log stream",
	'troubleshoot.debug': "Live debug stream",
	'troubleshoot.log.explain': "You can set the global log level and all matching log statements reached in your code will be shown below. "
		+ "<br />The log level is set for everyone and can affect other users that are also streaming logs at the same time. "
		+ "<br />Once you stop streaming logs, the log level is reset globally, which will also impact other live stream sessions. "
		+ "<br /><span class=\"orange\">Only the last 50 log entries are displayed. If you leave this page, all displayed log entries are lost.</span>",
	'troubleshoot.debug.explain': "When a debug statement is reached in your code, the output is shown below. Using the capture filter, you can refine which "
		+ "information is captured."
		+ "<br /><span class=\"orange\">Only the last 20 debug entries are displayed. If you leave this page, all displayed debug entries are lost.</span>",
	'troubleshoot.start': "Start",
	'troubleshoot.stop': "Stop",
	'troubleshooting.log.level': "Set the global log level",
	'troubleshoot.log.connected': "Log stream connected",
	'troubleshoot.log.disconnected': "Log stream disconnected",
	'troubleshoot.debug.filter': "Debug filter key",
	'troubleshoot.debug.connected': "Debug stream connected",
	'troubleshoot.debug.disconnected': "Debug stream disconnected",
	'troubleshoot.cleanup': "Cleanup",
	'troubleshoot.reboot': "Reboot instance",
	'troubleshoot.reboot.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />This operation will kill your instance and restart it. "
		+ "All unsaved work will be lost and there will be an interruption of service during reboot.<br /><br />"
		+ "Please enter your multifactor code to confirm:",
	'troubleshoot.mfa': "Multifactor code",
	'troubleshoot.reboot.abort': "Multifactor code missing",
	'troubleshoot.reboot.error': "Could not reboot instance",
	
	'storage.file.add': "Add Storage",
	'storage.database.add': "Add Database",
	'storage.file.title': "Object Storages",
	'storage.file.explain': "The object storages is a simple file-oriented persistent storage that can be used to store and retrieve data. It acts as a bridge "
		+ "between different technologies to ensure common operations from your APIs."
		+ "<br /><span class=\"orange\">Storages cannot be modified after they are created. Create a new one if connection parameters changed.</span>",
	'storage.file.delete.confirm': "Please confirm you want to remove storage <em>{}</em>."
		+ "<br />If this storage is used in your API code, you will have to update it.",
	'storage.file.delete.success': "Storage removed",
	'storage.file.delete.error': "Could not remove storage",
	'storage.file.name': "Display name",
	'storage.file.type': "Provider",
	'storage.file.path': "Path",
	'storage.file.empty': "Missing connection information",
	'storage.file.add.success': "Storage created",
	'storage.file.add.error': "Could not create storage",
	'storage.file.rename.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />If you change the storage name you'll need to update your API code too.",
	'storage.file.rename.success': "Storage renamed",
	'storage.file.rename.error': "Could not rename storage",
	'storage.database.title': "Databases",
	'storage.database.explain': "Databases offer direct SQL access to various providers."
		+ "<br /><span class=\"orange\">Databases cannot be modified after they are created. Create a new one if connection parameters changed.</span>",
	'storage.database.delete.confirm': "Please confirm you want to remove database <em>{}</em>."
		+ "<br />If this database is used in your API code, you will have to update it.",
	'storage.database.delete.success': "Database removed",
	'storage.database.delete.error': "Could not remove database",
	'storage.database.name': "Display name",
	'storage.database.type': "Provider",
	'storage.database.host': "Host",
	'storage.database.port': "Port",
	'storage.database.database': "Database",
	'storage.database.username': "Username",
	'storage.database.password': "Password",
	'storage.database.ssl': "Use SSL/TLS",
	'storage.database.empty': "Missing connection information",
	'storage.database.add.success': "Database created",
	'storage.database.add.error': "Could not create database",
	'storage.database.rename.confirm': "<em>Whoa, easy!</em> Be careful with this.<br /><br />If you change the database name you'll need to update your API code too.",
	'storage.database.rename.success': "Database renamed",
	'storage.database.rename.error': "Could not rename database",
	'storage.aws.region': "Region",
	'storage.aws.bucket': "Bucket Name",
	'storage.aws.key': "Access Key",
	'storage.aws.secret': "Secret",
	
	'': ""
};
