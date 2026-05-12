import uniqorn.*;

public class NotesList implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/notes", "GET")
            .summary("List notes")
            .description("Returns every note stored under the `notes` storage.")
            .returns("JSON array of { id, title, body } objects.")
            .process(data -> {
                Data out = JSON.array();
                Storage.Type store = Api.storage("notes");
                for( String key : store.list("/") )
                {
                    Data note = store.getData(key);
                    out.add(JSON.object()
                        .put("id", key.replace(".json", ""))
                        .put("title", note.asString("title"))
                        .put("body", note.asString("body")));
                }
                return out;
            });
    }
}
