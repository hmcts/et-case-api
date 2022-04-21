package uk.gov.hmcts.reform.et.syaapi.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import uk.gov.hmcts.reform.et.syaapi.config.notification.NotificationsProperties;
import uk.gov.hmcts.reform.et.syaapi.exception.NotificationException;
import uk.gov.service.notify.NotificationClient;
import uk.gov.service.notify.SendEmailResponse;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationServiceTest {
    private final String GOV_NOTIFY_API_KEY = "secret-21beac2b-d979-4771-bbb5-e34b62cba543-8b49a864-15de-4a07-935c-32693e4c374d";

    private final String SAMPLE_TEMPLATE_API_KEY = "905ac179-cfe3-40c0-ba33-93323b25f149";

    private final String REFERENCE = "TEST_EMAIL_Alert";

    private NotificationService notificationService;
    private NotificationClient notificationClient;

    @BeforeEach
    void setUp() {
        notificationClient = new NotificationClient(GOV_NOTIFY_API_KEY);
        notificationService = new NotificationService(notificationClient);
    }

    @Test
    void shouldSendEmailWithProperties() {
        String targetEmail = "vinoth.kumarsrinivasan@HMCTS.NET";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("multipleReference", "1234567890");
        SendEmailResponse sendEmailResponse = notificationService.sendMail(
            SAMPLE_TEMPLATE_API_KEY, targetEmail, parameters, REFERENCE);
        assertThat(sendEmailResponse.getReference().get()).isEqualTo(REFERENCE);
    }

    @Test
    void shouldFailSendingEmail() {
        String targetEmail = "vinoth1.kumarsrinivasan@HMCTS.NET";
        Map<String, String> parameters = new HashMap<>();
        parameters.put("references", "1234567890");
        parameters.put("firstname", "Vinothkumar");
        String reference = "TEST EMAIL Alert";
        assertThatThrownBy( () -> {
                notificationService.sendMail(SAMPLE_TEMPLATE_API_KEY, targetEmail, parameters, reference);
            }).isInstanceOf(NotificationException.class)
            .hasMessageContaining("send to this recipient using a team-only API key");
    }
}
