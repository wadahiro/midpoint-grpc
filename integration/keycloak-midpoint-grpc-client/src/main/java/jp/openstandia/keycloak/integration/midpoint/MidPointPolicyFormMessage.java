package jp.openstandia.keycloak.integration.midpoint;

import org.keycloak.models.utils.FormMessage;

import java.util.List;

public class MidPointPolicyFormMessage {
    public final String key;
    public final List<FormMessage> messages;

    public MidPointPolicyFormMessage(String key, List<FormMessage> message) {
        this.key = key;
        this.messages = message;
    }
}