import uniqorn.*;

public class Upload implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/upload", "POST")
            .summary("Upload image")
            .description("Accepts a single image upload and stores it under the `uploads` storage keyed by UUID. Non-image MIME types are rejected with HTTP 415.")
            .parameter("file", "Image file (jpeg, png, gif, or webp)", Input.isFile)
            .returns("JSON object: { id: <uuid>, mime: <string>, size: <bytes> }")
            .process(data -> {
                Data file = data.get("file");
                String mime = file.asString("mime");
                if( mime == null || !mime.startsWith("image/") )
                    Api.error(415, "Only image uploads are accepted (got: " + mime + ")");
                
				String raw = file.asString("content");
                if( raw == null )
                    Api.error(400, "Empty upload");
                
				byte[] content = raw.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
                String id = UUID.randomUUID().toString();
                Api.storage("uploads").put(id, content);
                return JSON.object()
                    .put("id", id)
                    .put("mime", mime)
                    .put("size", content.length);
            });
    }
}
