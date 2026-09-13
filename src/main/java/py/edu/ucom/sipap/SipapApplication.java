package py.edu.ucom.sipap;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.main.Main;
import py.edu.ucom.sipap.routes.SipapRoutes;
import py.edu.ucom.sipap.service.TransactionStore;

import java.time.Clock;
import java.time.ZoneId;

public final class SipapApplication {
    private SipapApplication() {
    }

    public static void main(String[] args) throws Exception {
        String brokerUrl = env("ARTEMIS_URL", "tcp://localhost:61616");
        String user = env("ARTEMIS_USER", "admin");
        String password = env("ARTEMIS_PASSWORD", "admin");
        String bankMockUrl = env("BANK_MOCK_URL", "http://localhost:8089");
        ZoneId zone = ZoneId.of(env("APP_TIMEZONE", "America/Asuncion"));

        ActiveMQConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory(brokerUrl, user, password);
        Main main = new Main();
        main.bind("connectionFactory", connectionFactory);
        main.configure().addRoutesBuilder(new SipapRoutes(
                new TransactionStore(), Clock.system(zone), true,
                jmsQueue("sipap.transferencias.entrada"),
                jmsQueue("sipap.auditoria"),
                jmsQueue("sipap.banco.itau"),
                jmsQueue("sipap.banco.atlas"),
                jmsQueue("sipap.banco.familiar"),
                bankMockUrl + "/banks/${header.SipapBankCode}/transfers"
                        + "?throwExceptionOnFailure=false"));
        main.run(args);
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String jmsQueue(String name) {
        return "jms:queue:" + name + "?connectionFactory=#connectionFactory"
                + "&exchangePattern=InOnly&disableReplyTo=true";
    }
}
