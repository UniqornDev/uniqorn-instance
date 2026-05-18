import uniqorn.*;

public class Custom implements Supplier<Api> {
	public Api get() {
		return new Api("/sum", "GET")
			.summary("Sum Two Numbers")
			.description("Returns a the sum of a and b.")
			.parameter("a", "First number", Input.isFloatingPoint)
			.parameter("b", "Second number", Input.isFloatingPoint)
			.returns("JSON object with field 'sum' containing a + b as a number.")
			.process(data -> JSON.object().put("sum", data.asDouble("a") + data.asDouble("b")));
	}
}
