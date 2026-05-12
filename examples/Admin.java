import uniqorn.*;

public class Admin implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/account/{name}", "GET")
            .summary("Account profile")
            .description("Returns the profile for the given account name. Any authenticated user may call, but only the account owner or an admin can read it; everyone else gets 403.")
            .allowGroup("users")
            .parameter("name", "Account name (identifier)", Input.isNotEmpty)
            .returns("JSON object: { name, email, plan, createdAt }.")
            .process((data, user) -> {
                String name = data.asString("name");
                if( !name.equals(user.name()) && !user.hasRole("admin") )
                    Api.error(403, "You may only read your own account.");

                Storage.Type store = Api.storage("accounts");
                Data profile = store.getData("/" + name + ".json");
                if( profile == null )
                    Api.error(404, "Unknown account: " + name);

                return profile;
            });
    }
}
