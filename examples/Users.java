import uniqorn.*;

public class Users implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/users", "GET")
            .summary("List users")
            .description("Returns users from the `app` database. Both filters are optional and combine; both use bound parameters.")
            .parameter("role", "Optional role filter (e.g. `admin`, `member`)")
            .parameter("id", "Optional user id (exact match)")
            .returns("JSON array of rows from the `users` table: { id, email, role }.")
            .process(data -> {
                Database.Type db = Api.database("app");
                boolean hasRole = !data.isEmpty("role");
                boolean hasId = !data.isEmpty("id");
                if( hasRole && hasId )
                    return db.query("SELECT id, email, role FROM users WHERE role = ? AND id = ?",
                        data.asString("role"), data.asString("id"));
                if( hasRole )
                    return db.query("SELECT id, email, role FROM users WHERE role = ? ORDER BY email",
                        data.asString("role"));
                if( hasId )
                    return db.query("SELECT id, email, role FROM users WHERE id = ?", data.asString("id"));
                return db.query("SELECT id, email, role FROM users ORDER BY email");
            });
    }
}
