package com.example.kafkacloudstream.handlers;

import com.example.kafkacloudstream.events.PageEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

@Component
public class PageEventHandler {

    @Bean
    public Consumer<PageEvent> pageEventConsumer() {
        return event -> System.out.println("****************\nReceived event: " + event.toString() + "\n****************");
    }
}
