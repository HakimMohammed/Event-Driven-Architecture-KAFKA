package com.example.kafkacloudstream.handlers;

import com.example.kafkacloudstream.events.PageEvent;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Random;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

@Component
public class PageEventHandler {

    @Bean
    public Consumer<PageEvent> pageEventConsumer() {
        return event -> System.out.println("****************\nReceived event: " + event.toString() + "\n****************");
    }

    @Bean
    public Supplier<PageEvent> pageEventSupplier() {
        return () -> {
            return new PageEvent(
                    Math.random() > 0.5 ? "Test 1": "Test 2",
                    Math.random() > 0.5 ? "User 1": "User 2",
                    new Date(),
                    10 + new Random().nextInt(1000)
            );
        };
    }

    @Bean
    public Function<KStream<String, PageEvent>, KStream<String, Long>> pageEventFunction() {
        return stream -> stream.filter((k,v) -> v.duration() > 100)
                .map((k,v) -> new KeyValue<>(v.name(), v.duration()) );
    }
}
