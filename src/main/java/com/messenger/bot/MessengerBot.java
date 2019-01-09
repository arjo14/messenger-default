package com.messenger.bot;

import com.github.messenger4j.Messenger;
import com.github.messenger4j.common.WebviewHeightRatio;
import com.github.messenger4j.exception.MessengerApiException;
import com.github.messenger4j.exception.MessengerIOException;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.github.messenger4j.messengerprofile.MessengerSettingProperty;
import com.github.messenger4j.send.MessagePayload;
import com.github.messenger4j.send.MessagingType;
import com.github.messenger4j.send.message.TemplateMessage;
import com.github.messenger4j.send.message.TextMessage;
import com.github.messenger4j.send.message.template.ButtonTemplate;
import com.github.messenger4j.send.message.template.button.Button;
import com.github.messenger4j.send.message.template.button.UrlButton;
import com.github.messenger4j.webhook.event.QuickReplyMessageEvent;
import com.github.messenger4j.webhook.event.ReferralEvent;
import com.github.messenger4j.webhook.event.TextMessageEvent;
import com.github.messenger4j.webhook.event.attachment.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static java.util.Optional.empty;
import static java.util.Optional.of;

@RestController
@RequestMapping("/callback")
@Slf4j
public class MessengerBot {
    private final Messenger messenger;

    @Value("${messenger4j.pageAccessToken}")
    private String pageAccessToken;


    public MessengerBot(@Value("${messenger4j.appSecret}") final String appSecret,
                        @Value("${messenger4j.verifyToken}") final String verifyToken,
                        @Value("${messenger4j.pageAccessToken}") final String pageAccessToken) throws MessengerApiException, MessengerIOException, MalformedURLException {
        this.messenger = Messenger.create(pageAccessToken, appSecret, verifyToken);

        messenger.deleteSettings(MessengerSettingProperty.PERSISTENT_MENU);
    }

    /**
     * Webhook verification endpoint.
     * The passed verification token (as query parameter) must match the configured verification token.
     * In case this is true, the passed challenge string must be returned by this endpoint.
     */
    @RequestMapping(method = RequestMethod.GET)
    public ResponseEntity<String> verifyWebHook(@RequestParam("hub.mode") final String mode,
                                                @RequestParam("hub.verify_token") final String verifyToken,
                                                @RequestParam("hub.challenge") final String challenge) {
        try {
            this.messenger.verifyWebhook(mode, verifyToken);
            log.info("Webhook verified!");
        } catch (MessengerVerificationException ignored) {
        }
        return ResponseEntity.status(HttpStatus.OK).body(challenge);
    }

    /**
     * Callback endpoint responsible for processing the inbound messages and events.
     */
    @RequestMapping(method = RequestMethod.POST)
    public ResponseEntity handleCallback(@RequestBody final String payload,
                                         @RequestHeader("X-Hub-Signature") final String signature) throws MessengerVerificationException {
        System.out.println();
        try {
            messenger.onReceiveEvents(payload, of(signature), event -> {
                        if (event.isTextMessageEvent()) {
                            TextMessageEvent textEvent = event.asTextMessageEvent();
                            System.out.println("UserId - " + textEvent.senderId());
                            try {
                                newTextMessageEventHandler(textEvent);
                            } catch (MalformedURLException | MessengerIOException | MessengerApiException e) {
                                e.printStackTrace();
                            }
                        } else if (event.isQuickReplyMessageEvent()) {
                            QuickReplyMessageEvent quickReplyMessageEvent = event.asQuickReplyMessageEvent();
                            newQuickReplyMessageEventHandler(quickReplyMessageEvent.messageId(), quickReplyMessageEvent.senderId(), quickReplyMessageEvent.text(), quickReplyMessageEvent.timestamp());
                        } else if (event.isPostbackEvent()) {
                            newPostbackEventHandler();
                        } else if (event.isMessageEchoEvent()) {
                            newEchoMessageEventHandler();
                        } else if (event.isAccountLinkingEvent()) {
                            newAccountLinkingEventHandler();
                        } else if (event.isAttachmentMessageEvent()) {
                            Attachment attachment = event.asAttachmentMessageEvent().attachments().get(0);
                            newAttachmentMessageEventHandler();
                        } else if (event.isMessageDeliveredEvent()) {
                            newMessageDeliveredEventHandler();
                        } else if (event.isMessageReadEvent()) {
                            newMessageReadEventHandler();
                        } else if (event.isOptInEvent()) {
                            newOptInEventHandler();
                        } else if (event.isReferralEvent()) {
                            try {
                                newReferralEventHandler(event.asReferralEvent());
                            } catch (MessengerApiException | MalformedURLException | MessengerIOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
            );
        } catch (IllegalArgumentException e) {
            log.error(e.getMessage());
        }
        System.out.println();
        return ResponseEntity.status(HttpStatus.OK).build();
    }


    private void newTextMessageEventHandler(TextMessageEvent event) throws MalformedURLException, MessengerApiException, MessengerIOException {
        log.info("Received new Text message");

        System.out.println(event.recipientId());
        sendTextMessage(event.senderId(), event.text());

/*        final IdRecipient recipient = IdRecipient.create(event.senderId());
        final NotificationType notificationType = NotificationType.NO_PUSH;
        String imageUrl = "https://file.transfer.cc-bot.info/file/download?fileName=VfE_html5.mp4";
        final UrlRichMediaAsset richMediaAsset = UrlRichMediaAsset.create(IMAGE, new URL(imageUrl), of(true));
        final RichMediaMessage richMediaMessage = RichMediaMessage.create(richMediaAsset);
        final MessagePayload payload = MessagePayload.create(recipient, MessagingType.RESPONSE,
                richMediaMessage);

        messenger.send(payload);*/


        //sendTextMessage(senderId, "svbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafhsvbkhlsdvkbhlskdalhfvlaksdjfhlksdjfhaskldhflaksdjfhsakdljfhsalkfhasdkljhsdakljfdhlaskdfhksljdafh");
//        sendTextMessage(event.senderId(), event.text());
    }


    private void newQuickReplyMessageEventHandler(String messageId, String senderId, String messageText, Instant timestamp) {
        log.info("Received new QuickReply message");
    }

    private void newAttachmentMessageEventHandler() {
        log.info("Received new Attachment message");
    }

    private void newReferralEventHandler(ReferralEvent referralEvent) throws MessengerApiException, MessengerIOException, MalformedURLException {
        log.info("Received new Referral event");

        String userId = referralEvent.senderId();
        String groupId = referralEvent.referral().refPayload().isPresent() ? referralEvent.referral().refPayload().get() : "";

        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<?> entity = new HttpEntity<>(headers);
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(
                String.format("http://10.100.25.182:8080/group/join?userId=%s&groupId=%s",
                        userId,
                        groupId));


        ResponseEntity<Object> response = restTemplate.exchange(builder.build().encode().toUri(), HttpMethod.POST, entity, Object.class);
        if (response.getStatusCode().value() == 200) {
            sendMenuButtonToUser(userId, groupId);
        }

    }

    private void sendMenuButtonToUser(String userId, String groupId) throws MalformedURLException, MessengerApiException, MessengerIOException {
        final UrlButton buttonC = UrlButton.create("Menu", new URL("https://66aadca7.ngrok.io/restaurant?userId=%s&groupId=%s"),
                of(WebviewHeightRatio.FULL), of(true), empty(), empty());

        final List<Button> buttons = Collections.singletonList(buttonC);
        final ButtonTemplate buttonTemplate = ButtonTemplate.create("Abres sax lava , du grancvar , de bac esi", buttons);

        final TemplateMessage templateMessage = TemplateMessage.create(buttonTemplate);
        final MessagePayload payload = MessagePayload.create(userId, MessagingType.RESPONSE,
                templateMessage);

        messenger.send(payload);
    }

    private void newPostbackEventHandler() {
        log.info("Received new Postback event");
    }

    private void newAccountLinkingEventHandler() {
        log.info("Received new AccountLinking event");
    }

    private void newOptInEventHandler() {
        log.info("Received new OptIn event");
    }

    private void newEchoMessageEventHandler() {
        log.info("Received new Echo Message event");
    }

    private void newMessageDeliveredEventHandler() {
        log.info("Received new MessageDelivery event");
    }

    private void newMessageReadEventHandler() {
        log.info("Received new MessageRead event");
    }

    /**
     * This handler is called when either the text is unsupported or when the event handler for the actual event type
     * is not registered. In this showcase all event handlers are registered. Hence only in case of an
     * unsupported text the fallback event handler is called.
     */
    private void newFallbackEventHandler() {
        log.info("Received new Fallback event");
    }

    private void sendTextMessage(String recipientId, String text) throws MessengerApiException, MessengerIOException {

        final MessagePayload payload = MessagePayload.create(recipientId, MessagingType.RESPONSE, TextMessage.create(text));
        messenger.send(payload);
/*        try {
            SenderAction markSeenAction = SenderAction.MARK_SEEN;
            SenderActionPayload payloadForMarkingSeen = SenderActionPayload.create(recipientId, markSeenAction);


            SenderAction typingOnAction = SenderAction.TYPING_ON;
            SenderActionPayload payloadForTypingOn = SenderActionPayload.create(recipientId, typingOnAction);

            SenderAction typingOffAction = SenderAction.TYPING_OFF;
            SenderActionPayload payloadForTypingOFF = SenderActionPayload.create(recipientId, typingOffAction);

            final LocationQuickReply quickReplyB = LocationQuickReply.create();
            final List<QuickReply> quickReplies = Collections.singletonList(quickReplyB);
            final TextMessage message = TextMessage.create("Send your location", of(quickReplies), empty());

            MessagePayload payload = MessagePayload.create(recipientId, message);
            messenger.send(payload);

            final LogInButton buttonA = LogInButton.create(new URL("https://www.list.am/login"));

            final List<Button> buttons = Collections.singletonList(buttonA);
            final ButtonTemplate buttonTemplate = ButtonTemplate.create("What do you want to do next?", buttons);

            final TemplateMessage templateMessage = TemplateMessage.create(buttonTemplate);
            final MessagePayload payload = MessagePayload.create(recipientId, templateMessage);

            messenger.send(payload);*/

            /*String newTextMessage;
            for (int i = 0, length = text.length(); i < length / 320 + 1; i++) {
                messenger.send(payloadForTypingOn);
                if (length - i * 320 < 320) {
                    newTextMessage = text.substring(i * 320);
                } else {
                    newTextMessage = text.substring(i * 320, i * 320 + 320);
                }
                payload = MessagePayload.create(recipientId, TextMessage.create(newTextMessage));
                messenger.send(payload);
            }
            messenger.send(payloadForTypingOFF);

        } catch (MessengerApiException | MessengerIOException e) {
            e.printStackTrace();
        }
        */
    }

}
