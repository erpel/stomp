package de.lancom.systems.stomp.spring;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import de.lancom.systems.stomp.core.StompClient;
import de.lancom.systems.stomp.core.wire.StompInterceptors;
import de.lancom.systems.stomp.core.wire.StompUrl;
import de.lancom.systems.stomp.core.wire.frame.SendFrame;
import de.lancom.systems.stomp.test.AsyncHolder;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@Slf4j
@EnableStomp
@ContextConfiguration(classes = ConsumerTest.class)
@RunWith(SpringJUnit4ClassRunner.class)
public class ProducerTest {

    private static final int WAIT_SECONDS = 5;

    private static final String URL = "${embedded.broker.url}/topic/ff550add-01ca-4181-97dc-6c64457cdf57";

    @Autowired
    private Environment environment;

    @Autowired
    private StompClient client;

    @StompDestination(URL)
    private StompProducer<SendFrame> producer1;

    @StompDestination(URL)
    private StompProducer<String> producer2;

    @StompDestination(URL)
    private StompProducer<byte[]> producer3;

    @Test
    public void produceFrame() {
        final StompUrl url = StompUrl.parse(environment.resolvePlaceholders(URL));

        final AsyncHolder<String> holder = AsyncHolder.create();

        client.addInterceptor(StompInterceptors.forBodyAsString(url, holder::set), "SEND");

        final SendFrame sendFrame = new SendFrame();
        sendFrame.setBodyAsString("Test1");

        producer1.send(sendFrame);

        assertThat(holder.get(1, WAIT_SECONDS, TimeUnit.SECONDS), is("Test1"));

    }

    @Test
    public void produceFrameDifferentDestination() {
        final StompUrl url = StompUrl.parse(environment.resolvePlaceholders(URL));
        final AsyncHolder<String> holder = AsyncHolder.create();

        client.addInterceptor(StompInterceptors.forBodyAsString(url, holder::set), "SEND");

        final SendFrame sendFrame = new SendFrame();
        sendFrame.setBodyAsString("Test1");
        sendFrame.setDestination("/topic/7f0b8579-afac-4173-a721-058c253fc0c6");

        producer1.send(sendFrame);

        assertThat(holder.get(1, WAIT_SECONDS, TimeUnit.SECONDS), is(nullValue()));

    }

    @Test
    public void produceString() {
        final StompUrl url = StompUrl.parse(environment.resolvePlaceholders(URL));
        final AsyncHolder<String> holder = AsyncHolder.create();

        client.addInterceptor(StompInterceptors.forBodyAsString(url, holder::set), "SEND");

        producer2.send("Test2");

        assertThat(holder.get(1, WAIT_SECONDS, TimeUnit.SECONDS), is("Test2"));

    }

    @Test
    public void produceByteArray() {
        final StompUrl url = StompUrl.parse(environment.resolvePlaceholders(URL));
        final AsyncHolder<String> holder = AsyncHolder.create();

        client.addInterceptor(StompInterceptors.forBodyAsString(url, holder::set), "SEND");

        producer3.send("Test3".getBytes(StandardCharsets.UTF_8));

        assertThat(holder.get(1, WAIT_SECONDS, TimeUnit.SECONDS), is("Test3"));

    }

}
