package info.ankin.experiments.prometheus.spring.basics;

import io.micrometer.core.annotation.Counted;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Controller
public class BasicsController {
    State state = new State();

    @Counted(value = "home")
    @RequestMapping
    Mono<ResponseEntity<Void>> get() {
        return Mono.delay(Duration.ofMillis(state.sleep)).then(Mono.just(ResponseEntity.ok().build()));
    }

    @RequestMapping(method = RequestMethod.POST)
    Mono<ResponseEntity<State>> post(@RequestBody State newState) {
        state.setSleep(newState.getSleep());
        return Mono.just(ResponseEntity.ok(state));
    }

    @Counted(value = "immediate")
    @RequestMapping(path = "/immediate")
    Mono<ResponseEntity<Void>> immediate() {
        return Mono.just(ResponseEntity.ok().build());
    }

    @Data
    public static class State {
        int sleep = 500;
    }
}
