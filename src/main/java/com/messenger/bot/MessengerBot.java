package com.messenger.bot;

import com.github.messenger4j.Messenger;
import com.github.messenger4j.common.SupportedLocale;
import com.github.messenger4j.common.WebviewHeightRatio;
import com.github.messenger4j.exception.MessengerApiException;
import com.github.messenger4j.exception.MessengerIOException;
import com.github.messenger4j.exception.MessengerVerificationException;
import com.github.messenger4j.messengerprofile.MessengerSettings;
import com.github.messenger4j.messengerprofile.getstarted.StartButton;
import com.github.messenger4j.messengerprofile.greeting.Greeting;
import com.github.messenger4j.messengerprofile.greeting.LocalizedGreeting;
import com.github.messenger4j.messengerprofile.persistentmenu.LocalizedPersistentMenu;
import com.github.messenger4j.messengerprofile.persistentmenu.PersistentMenu;
import com.github.messenger4j.messengerprofile.persistentmenu.action.PostbackCallToAction;
import com.github.messenger4j.messengerprofile.persistentmenu.action.UrlCallToAction;
import com.github.messenger4j.send.MessagePayload;
import com.github.messenger4j.send.MessagingType;
import com.github.messenger4j.send.message.TemplateMessage;
import com.github.messenger4j.send.message.TextMessage;
import com.github.messenger4j.send.message.quickreply.QuickReply;
import com.github.messenger4j.send.message.quickreply.TextQuickReply;
import com.github.messenger4j.send.message.template.ButtonTemplate;
import com.github.messenger4j.send.message.template.button.Button;
import com.github.messenger4j.send.message.template.button.UrlButton;
import com.github.messenger4j.webhook.event.QuickReplyMessageEvent;
import com.github.messenger4j.webhook.event.ReferralEvent;
import com.github.messenger4j.webhook.event.TextMessageEvent;
import com.github.messenger4j.webhook.event.attachment.Attachment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.Optional.empty;
import static java.util.Optional.of;

@RestController
@RequestMapping("/callback")
@Slf4j
public class MessengerBot {
    private final Messenger messenger;

    @Value("${messenger4j.pageAccessToken}")
    private String pageAccessToken;

    private Map<String, Stack<Integer>> stateMap = new ConcurrentHashMap<>();


    public MessengerBot(@Value("${messenger4j.appSecret}") final String appSecret,
                        @Value("${messenger4j.verifyToken}") final String verifyToken, @Value("${messenger4j.pageAccessToken}") final String pageAccessToken) throws MessengerApiException, MessengerIOException, MalformedURLException {
        this.messenger = Messenger.create(pageAccessToken, appSecret, verifyToken);
        final PostbackCallToAction callToActionAB = PostbackCallToAction.create("\uD83D\uDD0E Open Menu", "SEARCH_PET_PAYLOAD");
        final PostbackCallToAction callToActionAC = PostbackCallToAction.create("\uD83D\uDD14 Turn On/Off notification", "TURN_ON_OFF_PAYLOAD");

        final Greeting greeting = Greeting.create("Hello!", LocalizedGreeting.create(SupportedLocale.en_US,
                "This is a bot for dating pets "));

        String webViewUrl = "https://petreact.localtunnel.me";
        final UrlCallToAction callToActionA = UrlCallToAction.create("\uD83D\uDC7B Menu",
                new URL(webViewUrl), of(WebviewHeightRatio.FULL), of(true), empty(), empty());

        final PersistentMenu persistentMenu = PersistentMenu.create(false, of(Arrays.asList(callToActionA, callToActionAB, callToActionAC)),
                LocalizedPersistentMenu.create(SupportedLocale.cs_CZ, false, empty()));

        MessengerSettings messengerSettings = MessengerSettings.create(of(StartButton.create("Բարլուսիկ")), of(greeting), empty(), of(Collections.singletonList(new URL(webViewUrl))), empty(), empty(), empty());
        messenger.updateSettings(messengerSettings);
        messengerSettings = MessengerSettings.create(of(StartButton.create("Բարլուսիկ")), of(greeting), of(persistentMenu), of(Collections.singletonList(new URL(webViewUrl))), empty(), empty(), empty());
        messenger.updateSettings(messengerSettings);
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
                            try {
                                newTextMessageEventHandler(textEvent);
                            } catch (MessengerIOException | MessengerApiException e) {
                                e.printStackTrace();
                            }
                        } else if (event.isQuickReplyMessageEvent()) {
                            try {
                                newQuickReplyMessageEventHandler(event.asQuickReplyMessageEvent());
                            } catch (MessengerApiException | MessengerIOException e) {
                                e.printStackTrace();
                            }
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


    private void newTextMessageEventHandler(TextMessageEvent event) throws MessengerApiException, MessengerIOException {
        log.info("Received new Text message");
        if (event.text().equals("start")) {
            Stack<Integer> stack = stateMap.computeIfAbsent(event.senderId(), k -> new Stack<>());

            stack.push(0);

            createMsg(event.senderId());
        }
    }

    private void createMsg(String chatId) throws MessengerApiException, MessengerIOException {
        List<String> list = createButtons(0);
        sendQuickRepliesToUser(chatId, list, false);
    }

    private void sendQuickRepliesToUser(String userId, List<String> list, boolean hasExtraButtons) throws MessengerApiException, MessengerIOException {

        if (hasExtraButtons) {
            list.add("Back");
            list.add("End");
        }

        List<QuickReply> quickReplies = list.stream()
                .map(text -> TextQuickReply.create(text, text))
                .collect(Collectors.toList());

        final TextMessage message = TextMessage.create("Random text", of(quickReplies), empty());
        final MessagePayload payload = MessagePayload.create(userId, MessagingType.RESPONSE, message);

        messenger.send(payload);

    }

    private List<String> createButtons(int startNumber) {
        List<String> list = new ArrayList<>();
        for (int i = startNumber + 1, endNumber = startNumber + 6; i < endNumber; i++) {
            list.add(String.valueOf(i));
        }
        return list;
    }


    private void newQuickReplyMessageEventHandler(QuickReplyMessageEvent event) throws MessengerApiException, MessengerIOException {
        log.info("Received new QuickReply message");
        String userId = event.senderId();
        String text = event.text();

        Stack<Integer> stack;
        int number;
        switch (event.text().toLowerCase()) {
            case "back":

                stack = stateMap.get(userId);
                if (!stack.empty()) {
                    stack.pop();
                }
                if (!stack.empty() && stack.size() > 1) {
                    number = stack.peek();
                    sendQuickRepliesToUser(userId, createButtons(number), true);
                } else {
                    number = 0;
                    sendQuickRepliesToUser(userId, createButtons(number), false);
                }

                break;
            case "end":
                stack = stateMap.get(userId);
                StringBuilder stringBuilder = new StringBuilder();
                stack.forEach(integer -> stringBuilder.append(integer).append(","));
                sendTextMessage(userId, stringBuilder.substring(0, stringBuilder.length() - 1));

                break;
            default:

                number = Integer.parseInt(event.text());

                stack = stateMap.get(event.senderId());

                stack.push(number);

                if (!stack.empty() && stack.size() > 1) {
                    sendQuickRepliesToUser(event.senderId(), createButtons(number), true);
                }
                break;
        }
    }

    private void newAttachmentMessageEventHandler() {
        log.info("Received new Attachment message");
    }

    private void newReferralEventHandler(ReferralEvent referralEvent) throws MessengerApiException, MessengerIOException, MalformedURLException {
        log.info("Received new Referral event");


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
    }

}
