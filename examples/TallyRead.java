import uniqorn.*;

public class TallyRead implements Supplier<Api>
{
    public Api get()
    {
        return new Api("/tally", "GET")
            .summary("Read tally")
            .description("Returns the current value of the shared tally counter without modifying it.")
            .returns("JSON object: { count: <long> } — 0 if never incremented.")
            .process(data -> {
                Long current = State.global("tally");
                return JSON.object().put("count", current == null ? 0L : current);
            });
    }
}
