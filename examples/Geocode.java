import uniqorn.*;

public class Geocode implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/geocode", "GET")
            .summary("Geocode address")
            .description("Resolves a free-form address to geographic coordinates by querying the public Nominatim service. Results are taken from the first hit and normalised. Network latency typically dominates response time; misses return 404.")
            .parameter("query", "Address, place name, or landmark (free-form)", Input.isNotEmpty)
            .returns("JSON object: { lat: <decimal>, lon: <decimal>, displayName: <string> }. 404 if Nominatim returns no results.")
            .concurrency(2) // don't hammer the upstream
            .process(data -> {
                Data params = JSON.object()
                    .put("q", data.asString("query"))
                    .put("format", "json");
                Data headers = JSON.object()
                    .put("User-Agent", "uniqorn-sample-geocode/1.0");
                Data response = null;
                try
                {
                    response = Http.get("https://nominatim.openstreetmap.org/search", params, headers, "GET", 10000);
                }
                catch( Http.Error e )
                {
                    Api.error(502, "Upstream geocoder returned " + e.code);
                }
                if( response.isEmpty() || !response.isList() || response.isEmpty(0) )
                    Api.error(404, "No match for the given query");
                
				Data hit = response.get(0);
                return JSON.object()
                    .put("lat", hit.asDouble("lat"))
                    .put("lon", hit.asDouble("lon"))
                    .put("displayName", hit.asString("display_name"));
            });
    }
}
