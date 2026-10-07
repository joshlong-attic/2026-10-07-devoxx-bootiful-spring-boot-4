package com.example.bootiful;

import com.example.bootiful.grpc.DogServiceGrpc;
import com.example.bootiful.grpc.ListDogsRequest;
import com.example.bootiful.grpc.ListDogsResponse;
import io.grpc.stub.StreamObserver;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRequestHandler;
import org.jobrunr.scheduling.JobRequestScheduler;
import org.jobrunr.scheduling.carbonaware.CarbonAware;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.BeanRegistry;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.dialect.JdbcPostgresDialect;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.resilience.annotation.ConcurrencyLimit;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.registry.ImportHttpServices;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Import(BootifulApplication.MyBeanRegistrar.class)
@EnableResilientMethods
@ImportHttpServices(CatFactsClient.class)
@SpringBootApplication
public class BootifulApplication {

    public static void main(String[] args) {
        SpringApplication.run(BootifulApplication.class, args);
    }

    @Bean
    JdbcPostgresDialect jdbcPostgresDialect() {
        return JdbcPostgresDialect.INSTANCE;
    }

    @Bean
    Launcher launcher(JobRequestScheduler requestScheduler) {
        return new Launcher(requestScheduler);
    }

    static class MyBeanRegistrar implements BeanRegistrar {

        @Override
        public void register(BeanRegistry registry, Environment env) {
            registry.registerBean(Launcher.class);
            registry.registerBean(MyJobRequestHandler.class);
        }
    }

    static class Launcher implements ApplicationRunner {

        private final JobRequestScheduler scheduler;

        Launcher(JobRequestScheduler scheduler) {
            this.scheduler = scheduler;
        }

        @Override
        public void run(ApplicationArguments args) {
            this.scheduler.scheduleRecurrently(CarbonAware.interval(Duration.ofSeconds(10),
                    Duration.ofSeconds(5)), new MyJobRequest("Hello Devoxx!"));
        }
    }


    public record MyJobRequest(String message) implements JobRequest {

        @Override
        public Class<MyJobRequestHandler> getJobRequestHandler() {
            return MyJobRequestHandler.class;
        }
    }

    public static class MyJobRequestHandler implements JobRequestHandler<MyJobRequest> {

        @Override
        public void run(MyJobRequest jobRequest) throws Exception {
            IO.println(jobRequest.message() + " @ " + Instant.now());
        }
    }

//    @Bean
//    InetAddressFilter inetAddressFilter (){
//        return InetAddressFilter.none();
//    }
}


@Service
class DogsService extends DogServiceGrpc.DogServiceImplBase {

    private final DogRepository repository;

    DogsService(DogRepository repository) {
        this.repository = repository;
    }

    @Override
    public void list(ListDogsRequest request, StreamObserver<ListDogsResponse> responseObserver) {

        responseObserver.onNext(ListDogsResponse.newBuilder()
                .addAllDogs(repository.findAll()
                        .stream()
                        .map(realDog -> com.example.bootiful.grpc.Dog.newBuilder()
                                .setDescription(realDog.description())
                                .setId(realDog.id())
                                .setName(realDog.name())
                                .build())
                        .toList())
                .build());

        responseObserver.onCompleted();
    }
}

@Controller
@ResponseBody
class CatsController {

    private final CatFactsClient catFactsClient;

    private final AtomicInteger counter = new AtomicInteger();

    CatsController(CatFactsClient catFactsClient) {
        this.catFactsClient = catFactsClient;
    }

    @ConcurrencyLimit(10)
    @Retryable(maxRetries = 5, includes = IllegalStateException.class)
    @GetMapping("/cats")
    Collection<CatFact> facts() {
//
//        if (this.counter.getAndIncrement() < 5) {
//            IO.println("oops!");
//            throw new IllegalStateException("oops!");
//        }
//
//        IO.println("ok!");
        return this.catFactsClient.facts().facts();
    }
}

/*
@Component
class CatFactsClient {

    private final RestClient http;

    CatFactsClient(RestClient.Builder http) {
        this.http = http.build();
    }

    Collection<CatFact> facts() {
        return this.http
                .get()
                .uri("https://www.catfacts.net/api")
                .retrieve()
                .body(CatFacts.class)
                .facts();
    }

}

 */
interface CatFactsClient {

    @GetExchange("https://www.catfacts.net/api")
    CatFacts facts();
}

record CatFact(String fact) {
}

record CatFacts(Collection<CatFact> facts) {
}


@Controller
@ResponseBody
class DogsController {

    private final DogRepository repository;

    DogsController(DogRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/dogs", version = "2.0")
    Collection<Dog> dogsV2() {
        return this.repository.findAll();
    }

    @GetMapping(value = "/dogs", version = "1.0")
    Collection<Map<String, Object>> dogsV1() {
        return repository.findAll().stream()
                .map(d -> Map.of("id", (Object) d.id(), "fullName", d.name()))
                .toList();
    }
}

interface DogRepository extends ListCrudRepository<Dog, Integer> {
}

record Dog(@Id int id, String name, String description) {
}