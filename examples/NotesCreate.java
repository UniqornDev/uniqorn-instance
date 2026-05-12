import uniqorn.*;

public class NotesCreate implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/notes", "POST")
            .summary("Create note")
            .description("Persists a note as a JSON document under the `notes` storage. The filename is a UUID.")
            .parameter("title", "Short title", Input.isNotEmpty)
            .parameter("body", "Free-form content", Input.isNotEmpty)
            .returns("JSON object: { id: <uuid> }")
            .process(data -> {
                String id = UUID.randomUUID().toString();
                Data note = JSON.object()
                    .put("title", data.asString("title"))
                    .put("body", data.asString("body"));
                Api.storage("notes").put(id + ".json", note);
                return JSON.object().put("id", id);
            });
    }
}
