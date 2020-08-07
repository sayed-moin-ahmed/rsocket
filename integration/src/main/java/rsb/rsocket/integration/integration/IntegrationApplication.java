package rsb.rsocket.integration.integration;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;
import org.springframework.integration.dsl.MessageChannels;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.transformer.FileToStringTransformer;
import org.springframework.integration.handler.GenericHandler;
import org.springframework.integration.rsocket.ClientRSocketConnector;
import org.springframework.integration.rsocket.RSocketInteractionModel;
import org.springframework.integration.rsocket.dsl.RSockets;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.rsocket.RSocketStrategies;
import rsb.rsocket.BootifulProperties;
import rsb.rsocket.integration.GreetingRequest;
import rsb.rsocket.integration.GreetingResponse;

import java.io.File;

@Log4j2
@SpringBootApplication
public class IntegrationApplication {

	@Bean
	ClientRSocketConnector clientRSocketConnector(RSocketStrategies strategies,
			BootifulProperties properties) {
		ClientRSocketConnector clientRSocketConnector = new ClientRSocketConnector(
				properties.getRsocket().getHostname(), properties.getRsocket().getPort());
		clientRSocketConnector.setRSocketStrategies(strategies);
		return clientRSocketConnector;
	}

	@Bean
	IntegrationFlow greetingFlow(@Value("${user.home}") File home,
			ClientRSocketConnector clientRSocketConnector) {
		var inboundFileAdapter = Files//
				.inboundAdapter(new File(home, "in"))//
				.autoCreateDirectory(true);
		return IntegrationFlows//
				.from(inboundFileAdapter,
						poller -> poller.poller(pm -> pm.fixedRate(100)))//
				.transform(new FileToStringTransformer())//
				.transform(String.class, GreetingRequest::new)//
				.handle(RSockets//
						.outboundGateway("greetings")//
						.interactionModel(RSocketInteractionModel.requestStream)//
						.expectedResponseType(GreetingResponse.class)//
						.clientRSocketConnector(clientRSocketConnector)//
				)//
				.split()//
				.channel(this.channel())
				.handle((GenericHandler<GreetingResponse>) (payload, headers) -> {
					log.info("-----------------");
					log.info(payload.toString());
					headers.forEach((header, value) -> log.info(header + "=" + value));
					return null;
				})//
				.get();
	}

	@Bean
	MessageChannel channel() {
		return MessageChannels.flux().get();
	}

	public static void main(String[] a) {
		SpringApplication.run(IntegrationApplication.class, a);
	}

}
