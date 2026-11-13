package com.fazbear.orden.checkout;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazbear.orden.config.RabbitMQConfig;
import com.fazbear.orden.model.PedidoEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once delivery: consumers deduplicate stable eventoId (operation UUID). */
@Component
@Profile("ev3-local")
@EnableScheduling
public class CheckoutOutbox {
    private final JdbcTemplate db;
    private final RabbitTemplate rabbit;
    public CheckoutOutbox(JdbcTemplate db,RabbitTemplate rabbit) { this.db=db; this.rabbit=rabbit; }
    @Scheduled(fixedDelayString="${checkout.outbox-delay-ms:3000}")
    @Transactional
    public void publish() {
        for (var row:db.queryForList("select * from checkout_outbox where published=false limit 20 for update skip locked")) {
            try {
                PedidoEvent event=new ObjectMapper().readValue(row.get("payload").toString(),PedidoEvent.class);
                rabbit.invoke(operations -> {
                    operations.convertAndSend(RabbitMQConfig.EXCHANGE,RabbitMQConfig.ROUTING_KEY,event);
                    operations.waitForConfirmsOrDie(5000);
                    return true;
                });
                db.update("update checkout_outbox set published=true where event_id=?",row.get("event_id"));
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(getClass()).warn("Outbox pending event {}: {}",row.get("event_id"),e.getClass().getSimpleName());
                break;
            }
        }
    }
}
