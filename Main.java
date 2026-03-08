import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

interface IMediator {
    void joinChannel(String channelName, User user);
    void leaveChannel(String channelName, User user);
    void sendMessage(String channelName, String msg, User sender);
    void sendPrivateMessage(String msg, User sender, String recipientName);
    void sendCrossChannelMessage(String fromChannel, String toChannel, String msg, User sender);
    void blockUser(String channelName, User admin, String userName);
}

interface IUser {
    String getName();
    void receiveMessage(String channel, String msg, User sender);
    void receivePrivateMessage(String msg, User sender);
    void receiveNotification(String notification);
    void receiveBlockNotification(String channel);
}

class User implements IUser {
    private String name;
    private IMediator mediator;
    private Set<String> channels = new HashSet<>();
    private boolean blocked = false;

    public User(String name, IMediator mediator) {
        this.name = name;
        this.mediator = mediator;
    }

    public String getName() { return name; }

    public void joinChannel(String channel) {
        mediator.joinChannel(channel, this);
        channels.add(channel);
    }

    public void leaveChannel(String channel) {
        mediator.leaveChannel(channel, this);
        channels.remove(channel);
    }

    public void sendMessage(String channel, String msg) {
        mediator.sendMessage(channel, msg, this);
    }

    public void sendPrivateMessage(String msg, String recipient) {
        mediator.sendPrivateMessage(msg, this, recipient);
    }

    public void sendCrossChannelMessage(String from, String to, String msg) {
        mediator.sendCrossChannelMessage(from, to, msg, this);
    }

    public void blockUser(String channel, String userName) {
        mediator.blockUser(channel, this, userName);
    }

    @Override
    public void receiveMessage(String channel, String msg, User sender) {
        if (!blocked) {
            System.out.println("[" + channel + "] " + sender.getName() + ": " + msg + " (получил " + name + ")");
        }
    }

    @Override
    public void receivePrivateMessage(String msg, User sender) {
        if (!blocked) {
            System.out.println("[Приватно от " + sender.getName() + " к " + name + "]: " + msg);
        }
    }

    @Override
    public void receiveNotification(String notification) {
        System.out.println(name + " уведомление: " + notification);
    }

    @Override
    public void receiveBlockNotification(String channel) {
        System.out.println(name + " был заблокирован в канале " + channel);
        blocked = true;
    }
}

class ChatMediator implements IMediator {
    private Map<String, List<User>> channels = new ConcurrentHashMap<>();
    private Map<String, User> usersByName = new ConcurrentHashMap<>();
    private Map<String, Set<String>> userChannels = new ConcurrentHashMap<>();

    @Override
    public void joinChannel(String channelName, User user) {
        channels.putIfAbsent(channelName, new ArrayList<>());
        List<User> channelUsers = channels.get(channelName);
        if (!channelUsers.contains(user)) {
            channelUsers.add(user);
            usersByName.put(user.getName(), user);
            userChannels.computeIfAbsent(user.getName(), k -> new HashSet<>()).add(channelName);
            notifyAllInChannel(channelName, "Пользователь " + user.getName() + " присоединился к каналу", user);
        }
    }

    @Override
    public void leaveChannel(String channelName, User user) {
        List<User> channelUsers = channels.get(channelName);
        if (channelUsers != null && channelUsers.remove(user)) {
            userChannels.getOrDefault(user.getName(), new HashSet<>()).remove(channelName);
            notifyAllInChannel(channelName, "Пользователь " + user.getName() + " покинул канал", user);
        }
    }

    @Override
    public void sendMessage(String channelName, String msg, User sender) {
        List<User> channelUsers = channels.get(channelName);
        if (channelUsers == null) {
            System.out.println("Ошибка: канал '" + channelName + "' не существует");
            return;
        }
        if (!channelUsers.contains(sender)) {
            System.out.println("Ошибка: пользователь " + sender.getName() + " не состоит в канале " + channelName);
            return;
        }
        for (User u : channelUsers) {
            if (u != sender) {
                u.receiveMessage(channelName, msg, sender);
            }
        }
    }

    @Override
    public void sendPrivateMessage(String msg, User sender, String recipientName) {
        User recipient = usersByName.get(recipientName);
        if (recipient == null) {
            System.out.println("Ошибка: пользователь " + recipientName + " не найден");
            return;
        }
        recipient.receivePrivateMessage(msg, sender);
    }

    @Override
    public void sendCrossChannelMessage(String fromChannel, String toChannel, String msg, User sender) {
        List<User> fromUsers = channels.get(fromChannel);
        if (fromUsers == null || !fromUsers.contains(sender)) {
            System.out.println("Ошибка: вы не в канале " + fromChannel);
            return;
        }
        List<User> toUsers = channels.get(toChannel);
        if (toUsers == null) {
            System.out.println("Ошибка: канал '" + toChannel + "' не существует");
            return;
        }
        String crossMsg = "[Из " + fromChannel + " в " + toChannel + "] " + sender.getName() + ": " + msg;
        for (User u : toUsers) {
            u.receiveMessage(toChannel, crossMsg, sender);
        }
    }

    @Override
    public void blockUser(String channelName, User admin, String userName) {
        List<User> channelUsers = channels.get(channelName);
        if (channelUsers == null) {
            System.out.println("Канал не существует");
            return;
        }
        User target = usersByName.get(userName);
        if (target == null || !channelUsers.contains(target)) {
            System.out.println("Пользователь " + userName + " не в канале");
            return;
        }
        channelUsers.remove(target);
        userChannels.getOrDefault(userName, new HashSet<>()).remove(channelName);
        target.receiveBlockNotification(channelName);
        notifyAllInChannel(channelName, "Пользователь " + userName + " был заблокирован администратором " + admin.getName(), admin);
    }

    private void notifyAllInChannel(String channelName, String notification, User exclude) {
        List<User> channelUsers = channels.get(channelName);
        if (channelUsers == null) return;
        for (User u : channelUsers) {
            if (u != exclude) {
                u.receiveNotification(notification);
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {
        ChatMediator mediator = new ChatMediator();

        User alice = new User("Alice", mediator);
        User bob = new User("Bob", mediator);
        User charlie = new User("Charlie", mediator);
        User dave = new User("Dave", mediator);

        alice.joinChannel("general");
        bob.joinChannel("general");
        charlie.joinChannel("general");
        dave.joinChannel("admin");

        System.out.println("\n=== Отправка сообщения в канал general ===");
        alice.sendMessage("general", "Всем привет!");

        System.out.println("\n=== Приватное сообщение от Bob к Alice ===");
        bob.sendPrivateMessage("Привет, Алиса!", "Alice");

        System.out.println("\n=== Charlie присоединяется к каналу music ===");
        charlie.joinChannel("music");
        alice.joinChannel("music");
        bob.joinChannel("music");

        System.out.println("\n=== Отправка в music ===");
        alice.sendMessage("music", "Кто любит рок?");

        System.out.println("\n=== Кросс-канальное сообщение из music в general ===");
        bob.sendCrossChannelMessage("music", "general", "Всем в музыку!");

        System.out.println("\n=== Блокировка Charlie в канале general администратором Dave ===");
        dave.blockUser("general", "Charlie");

        System.out.println("\n=== Попытка Charlie отправить сообщение в general (после блокировки) ===");
        charlie.sendMessage("general", "Я ещё здесь?");

        System.out.println("\n=== Отправка в несуществующий канал ===");
        alice.sendMessage("unknown", "тест");

        System.out.println("\n=== Попытка отправить сообщение из канала, где пользователь не состоит ===");
        dave.sendMessage("music", "я админ, но не в music");
    }
}