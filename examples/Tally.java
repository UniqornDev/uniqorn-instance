import uniqorn.*;

public class Tally implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/tally", "POST")
            .summary("Increment tally")
            .description("Atomically increments a global counter shared across all callers and returns its new value.")
            .returns("JSON object: { count: <long> }")
            .process(data -> Api.atomic(() -> {
                Long current = State.global("tally");
                long next = (current == null ? 0L : current) + 1L;
                State.global("tally", next);
                return JSON.object().put("count", next);
            }));
    }
}
