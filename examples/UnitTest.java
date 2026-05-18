import uniqorn.*;

public class Custom implements Supplier<Api> {
	public Api get() {
		return new Api("/test/sum", "GET")
			.summary("Unit test against the sum endpoint")
			.description("This endpoints acts as a test runner against the sum endpoint, only 'tester' role can perform the test.")
			.allowRole("tester")
			.returns("JSON list of tests with 'pass' being true or false.")
			.process(data -> {
				Data cases = JSON.array()
					// regular cases
					.add(JSON.object().put("name", "positive").put("a", 1.7).put("b", 2.3).put("expected", 4.0))
					.add(JSON.object().put("name", "negative").put("a", -1.4).put("b", -2.6).put("expected", -4.0))
					.add(JSON.object().put("name", "zero").put("a", 0.0).put("b", 0.0).put("expected", 0.0))
					.add(JSON.object().put("name", "integers").put("a", 5).put("b", 4).put("expected", 9.0))
					// invalid parameters
					.add(JSON.object().put("name", "missing a").put("a", null).put("b", 1.0).put("expected", null))
					.add(JSON.object().put("name", "missing b").put("a", 1.0).put("b", null).put("expected", null))
					.add(JSON.object().put("name", "missing both").put("a", null).put("b", null).put("expected", null))
					.add(JSON.object().put("name", "empty a").put("a", "").put("b", 1.0).put("expected", null))
					.add(JSON.object().put("name", "empty b").put("a", 1.0).put("b", "").put("expected", null))
					.add(JSON.object().put("name", "empty both").put("a", "").put("b", "").put("expected", null))
					.add(JSON.object().put("name", "illegal a").put("a", "a").put("b", 1.0).put("expected", null))
					.add(JSON.object().put("name", "illegal b").put("a", 1.0).put("b", "b").put("expected", null))
					.add(JSON.object().put("name", "illegal both").put("a", "a").put("b", "b").put("expected", null))
					// casting cases
					.add(JSON.object().put("name", "cast a").put("a", "5.5").put("b", -4.5).put("expected", 1.0))
					.add(JSON.object().put("name", "cast b").put("a", 5.5).put("b", "-4.5").put("expected", 1.0))
					.add(JSON.object().put("name", "cast both").put("a", "5.5").put("b", "-4.5").put("expected", 1.0))
					// edge cases
					.add(JSON.object().put("name", "overflow").put("a", Double.MAX_VALUE).put("b", Double.MAX_VALUE).put("expected", Double.POSITIVE_INFINITY))
					.add(JSON.object().put("name", "rounding").put("a", 1.0/3.0).put("b", 2.0/3.0).put("expected", 1.0))
					// add more cases hereafter if needed
					;

				Data results = JSON.array();
				for( Data test : cases ) {
					Data result = null;
					try {
						Data params = JSON.object().put("a", test.get("a")).put("b", test.get("b"));
						Data response = Api.chain("/upi/demo/sum", "GET", params);
						result = response.get("sum");
					} catch (Exception e) {
						/* result stays null */
						Api.debug("test", test, e);
					}
					
					boolean pass = false;
					if( test.isNull("expected") && result == null ) pass = true;
					else if( result == null ) pass = false;
					else if( Double.isNaN(test.asDouble("expected")) && Double.isNaN(result.asDouble()) ) pass = true;
					else if( result.asDouble() == test.asDouble("expected") ) pass = true;
					else pass = false;
					
					if( !pass ) Api.debug("test", test, result);

					results.add(JSON.object().put("name", test.get("name")).put("pass", pass));
				}

				return results;
			});
	}
}
