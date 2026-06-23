import uniqorn.*;

public class Orders implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/orders", "GET")
            .summary("List Orders")
            .description("Returns your company's orders, most recent first.")
            .allowGroup("users")
            .returns("A JSON array of order rows: { id, reference, status, total_cents, created_at }.")
            .process((data, user) ->
            {
                // the tenant information is read from the signed-in user
                String tenant = user.attributes().asString("tenant");
                if( tenant == null || tenant.isEmpty() ) Api.error(403, "No tenant assigned");

                // every tenant has its own database to ensure physical isolation
                Database.Type db = Api.database(tenant);
                if( db == null ) Api.error(404, "Tenant datasource not found: " + tenant);

                return db.query("SELECT id, reference, status, total_cents, created_at FROM orders ORDER BY created_at DESC");
            });
    }
}
