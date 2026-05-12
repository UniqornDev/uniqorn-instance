import uniqorn.*;

public class Echo implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/echo", "POST")
            .summary("Echo contact form")
            .description("Accepts a contact-form submission and echoes the validated fields back. Demonstrates the canonical POST-with-JSON-body pattern: each body field is declared as a parameter so the framework parses, validates, and exposes it on `data`.")
            .parameter("name", "Sender's full name", Input.isNotEmpty)
            .parameter("email", "Reply-to address", Input.isEmail)
            .parameter("message", "Free-form message body", Input.isNotEmpty)
            .returns("JSON object: { received: { name: <string>, email: <string>, message: <string> } }")
            .process(data -> JSON.object()
                .put("received", JSON.object()
                    .put("name", data.asString("name"))
                    .put("email", data.asString("email"))
                    .put("message", data.asString("message"))));
    }
}
