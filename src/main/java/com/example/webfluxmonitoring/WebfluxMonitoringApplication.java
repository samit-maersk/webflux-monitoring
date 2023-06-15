package com.example.webfluxmonitoring;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;

@SpringBootApplication
@Slf4j
public class WebfluxMonitoringApplication {

	public static void main(String[] args) {
		Hooks.enableAutomaticContextPropagation();
		SpringApplication.run(WebfluxMonitoringApplication.class, args);
	}

	@Bean
	RouterFunction routerFunction(TodoService todoService) {
		return RouterFunctions
				.route()
				.path("/todos", builder -> builder
						.GET("/all", request -> ServerResponse.ok().body(todoService.todos(), Todo.class))
						.GET("/{id}", request -> {
							var id = Integer.parseInt(request.pathVariable("id"));
							return ServerResponse.ok().body(todoService.todoById(id), Todo.class);
						})
						.GET("", request -> {
							var userId = Integer.parseInt(request.queryParam("userId").orElse("0"));
							return ServerResponse.ok().body(todoService.todosByUserId(userId), Todo.class);
						})
				)
				.after((request, response) -> {
					log.info("{} {} {}", request.method(), request.path(), response.statusCode());
					return response;
				})
				.build();
	}
}

record Todo(int userId, int id, String title, boolean completed) {}

@HttpExchange(url = "/todos")
interface TodoClient {
	@GetExchange
	Flux<Todo> todos();

	@GetExchange("/{id}")
	Mono<Todo> todoById(@PathVariable int id);

	@GetExchange
	Flux<Todo> todosByUserId(@RequestParam int userId);
}

@Component
@Slf4j
@RequiredArgsConstructor
class ClientConfiguration {
	private final WebClient.Builder builder;
	@Value("${spring.application.jsonplaceHolder.host}")
	private String baseUrl;

	@Bean
	TodoClient todoClient() {
		var webClientAdapter = WebClientAdapter.forClient(builder.baseUrl(baseUrl).build());
		var httpServiceProxyFactory = HttpServiceProxyFactory.builder(webClientAdapter).build();
		return httpServiceProxyFactory.createClient(TodoClient.class);
	}
}


@Service
@RequiredArgsConstructor
class TodoService {
	private final TodoClient todoClient;

	Flux<Todo> todos() {
		return todoClient.todos();
	}

	Mono<Todo> todoById(int id) {
		return todoClient.todoById(id);
	}

	Flux<Todo> todosByUserId(int userId) {
		return todoClient.todosByUserId(userId);
	}
}
