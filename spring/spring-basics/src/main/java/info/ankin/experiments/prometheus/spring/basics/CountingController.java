package info.ankin.experiments.prometheus.spring.basics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping(path = "/counting")
public class CountingController {
    private final MeterRegistry meterRegistry;

    public CountingController(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @GetMapping
    List<Option> optionsList() {
        return Option.OPTION_LIST;
    }

    @GetMapping(path = "/{option}")
    @ResponseStatus(HttpStatus.OK)
    void countOption(@PathVariable("option") Option option) {
        Counter.builder(option.name()).register(meterRegistry).increment();
        Counter.builder("options").register(meterRegistry).increment();
    }

    enum Option {
        A,
        B,
        C,
        D,
        ;
        static final List<Option> OPTION_LIST = Arrays.asList(Option.values());
    }
}
