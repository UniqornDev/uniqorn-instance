package local;

import java.util.Objects;

import aeonics.data.Data;
import aeonics.entity.Entity;
import aeonics.entity.Message;
import aeonics.entity.Registry;
import aeonics.entity.security.User;
import aeonics.http.Endpoint;
import aeonics.http.HttpException;
import aeonics.manager.Logger;
import aeonics.manager.Manager;
import aeonics.util.Tuples.Tuple;
import uniqorn.Api;
import uniqorn.Workspace;

public class Router extends Endpoint
{
	public static long limit = 0;
	
	public static class Type extends Endpoint.Type
	{
		private String prefix = "";
		public String prefix() { return prefix; }
		public void prefix(String value) { prefix = value; }
		
		@Override
		public boolean internal() { return true; }
		
		@Override
		public SnapshotMode snapshotMode() { return SnapshotMode.NONE; }
		
		@Override
		public boolean matchesMethod(String method) { return true; }
		
		@Override
		public boolean matchesPath(String url) { return url != null && url.startsWith(prefix() + "/"); }
		
		@Override
		public Data process(Message request) throws Exception
		{
			String method = request.content().asString("method");
			String path = request.content().asString("path").substring(prefix().length());

			Manager.of(Logger.class).log(Logger.FINER, Api.class, "{} {}{} call from {} authenticated as {}", 
				method,
				prefix(),
				path,
				request.connection() == null ? "0.0.0.0" : request.connection().clientIp(),
				Objects.requireNonNullElse(Registry.of(User.class).get(request.user()), User.ANONYMOUS).name());
			
			try
			{
				for( Workspace.Type w : Registry.of(Workspace.class) )
				{
					String workspacePrefix = w.valueOf("prefix").asString();
					if( !path.startsWith(workspacePrefix + "/") ) continue;
					for( Tuple<Entity, Data> t : w.relations("endpoints") )
					{
						if( t == null || t.a == null || !t.a.valueOf("enabled").asBool() ) continue;
						if( !t.a.<uniqorn.Endpoint.Type>cast().matches(method, path.substring(workspacePrefix.length())) ) continue;
						
						uniqorn.Endpoint.Type e = t.a.cast();
						if( e.counter().incrementAndGet() > limit )
							throw new HttpException(429, "Call rate limit exceeded");
						
						Api a = e.api();
						if( a == null ) throw new HttpException(404); // race condition
						Endpoint.Rest.Type r = a.api();
						if( r == null ) throw new HttpException(404); // race condition
						
						request.content().put("path", path.substring(workspacePrefix.length()));
						return r.process(request);
					}
				}
				throw new HttpException(404);
			}
			catch(Exception e)
			{
				Manager.of(Logger.class).log(Logger.FINER, Api.class, e);
				throw e;
			}
		}
	}
	
	protected Class<? extends Router.Type> defaultTarget() { return Router.Type.class; }
	protected java.util.function.Supplier<? extends Router.Type> defaultCreator() { return Router.Type::new; }

	@Override
	public Endpoint.Template template()
	{
		return super.template()
			.summary("Uniqorn mapping")
			.description("This endpoint ensures the mapping and rate limiting of uniqorn apis.")
			;
	}
}
