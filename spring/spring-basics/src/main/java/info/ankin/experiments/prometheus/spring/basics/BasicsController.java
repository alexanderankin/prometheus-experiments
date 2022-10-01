package info.ankin.experiments.prometheus.spring.basics;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Random;

@Controller
public class BasicsController {
    Random random = new Random();
    State state = new State();

    @Autowired
    void metrics(MeterRegistry meterRegistry) {
        Gauge.builder("basics_state_sleep", state::getSleep).register(meterRegistry);
    }

    @Timed(value = "basics_request_timing_home")
    @Counted(value = "basics_request_home")
    @RequestMapping
    Mono<ResponseEntity<State>> get() {
        return Mono.delay(Duration.ofMillis(state.sleep)).then(Mono.just(ResponseEntity.ok(state)));
    }

    @Timed(value = "basics_request_timing_random")
    @Counted(value = "basics_request_random")
    @RequestMapping("/random")
    Mono<ResponseEntity<State>> random() {
        return Mono.delay(Duration.ofMillis(random.nextInt(state.getSleep()))).then(Mono.just(ResponseEntity.ok(state)));
    }

    @Timed(value = "basics_request_timing_state")
    @Counted(value = "basics_request_state")
    @RequestMapping(method = RequestMethod.POST)
    Mono<ResponseEntity<State>> post(@RequestBody State newState) {
        state.setSleep(newState.getSleep());
        return Mono.just(ResponseEntity.ok(state));
    }

    @Timed(value = "basics_request_timing_immediate")
    @Counted(value = "basics_request_immediate")
    @RequestMapping(path = "/immediate")
    Mono<ResponseEntity<Void>> immediate() {
        return Mono.just(ResponseEntity.ok().build());
    }

    @Data
    public static class State {
        int sleep = 500;
    }
}
