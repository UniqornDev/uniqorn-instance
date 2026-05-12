import uniqorn.*;

public class Aggregator implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/product/{sku}", "GET")
            .summary("Product detail")
            .description("Builds a product detail document by chaining to the local /catalog and /reviews endpoints in-process. Both calls execute against the same sku without an outbound HTTP roundtrip, saving the caller a second request.")
            .parameter("sku", "Product SKU (exact match)", Input.isNotEmpty)
            .returns("JSON object: { sku, name, price, currency, rating, reviews }. 404 if the SKU is unknown to the catalog.")
            .process(data -> {
                String sku = data.asString("sku");
                Data product = Api.chain("/catalog/" + sku, "GET", null);
                if( product == null || product.isEmpty() )
                    Api.error(404, "Unknown SKU");
				
                Data reviews = Api.chain("/reviews", "GET", JSON.object().put("sku", sku));
                return JSON.object()
                    .put("sku", sku)
                    .put("name", product.asString("name"))
                    .put("price", product.asDouble("price"))
                    .put("currency", product.asString("currency"))
                    .put("rating", reviews.asDouble("rating"))
                    .put("reviews", reviews.asInt("count"));
            });
    }
}
