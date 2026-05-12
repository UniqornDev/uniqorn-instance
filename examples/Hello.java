import uniqorn.*;

public class Hello implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/hello", "GET")
            .summary("Hello")
            .description("Greets the caller by name.")
            .parameter("name", "Person to greet", Input.isNotEmpty)
            .returns("JSON object with a `hello` field containing the greeted name.")
            .process(data -> JSON.object().put("hello", data.asString("name")));
    }
}
