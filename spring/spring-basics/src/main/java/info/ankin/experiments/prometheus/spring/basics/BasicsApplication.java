package info.ankin.experiments.prometheus.spring.basics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
public class BasicsApplication {
    public static void main(String[] args) {
        Schedulers.enableMetrics();
        SpringApplication.run(BasicsApplication.class, args);
    }
}
