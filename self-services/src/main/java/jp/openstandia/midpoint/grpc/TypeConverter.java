package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;

import javax.xml.bind.annotation.XmlElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class TypeConverter {

    private static Map<UserItemPath, ItemName> userTypeMap = new HashMap<>();
    private static Map<ItemName, Class> userValueTypeMap = new HashMap<>();

    static {
        Map<String, ItemName> strToItemName = new HashMap<>();

        Field[] fields = UserType.class.getFields();
        Arrays.stream(fields)
                .filter(x -> x.getName().startsWith("F_") && x.getType() == ItemName.class)
                .forEach(x -> {
                    String name = x.getName();
                    try {
                        UserItemPath path = UserItemPath.valueOf(name);
                        ItemName itemName = (ItemName) x.get(null);
                        userTypeMap.put(path, itemName);
                        strToItemName.put(itemName.getLocalPart(), itemName);
                    } catch (IllegalArgumentException | IllegalAccessException ignore) {
                    }
                });

        Method[] methods = UserType.class.getMethods();
        Arrays.stream(methods)
                .filter(x -> x.isAnnotationPresent(XmlElement.class))
                .forEach(x -> {
                    XmlElement ele = x.getAnnotation(XmlElement.class);

                    ItemName itemName = strToItemName.get(ele.name());
                    if (itemName == null) {
                        return;
                    }
                    Class<?> returnType = x.getReturnType();

                    if (returnType.isAssignableFrom(List.class)) {
                        Type genericReturnType = x.getGenericReturnType();
                        String typeName = genericReturnType.getTypeName();
                        if (typeName.contains(String.class.getName())) {
                            userValueTypeMap.put(itemName, String.class);
                        } else if (typeName.contains(PolyStringType.class.getName())) {
                            userValueTypeMap.put(itemName, PolyStringType.class);
                        } else {
                            throw new UnsupportedOperationException(itemName + " is not supported");
                        }
                    } else {
                        userValueTypeMap.put(itemName, returnType);
                    }
                });
    }

    public static ItemName toItemName(UserItemPath path) {
        ItemName itemName = userTypeMap.get(path);
        if (itemName == null) {
            throw new UnsupportedOperationException(path + " is not supported");
        }
        return itemName;
    }

    public static Object toValue(UserItemPath path, String value) {
        ItemName itemName = toItemName(path);

        Class clazz = userValueTypeMap.get(itemName);

        if (clazz.isAssignableFrom(String.class)) {
            return value;
        }
        if (clazz.isAssignableFrom(PolyStringType.class)) {
            return PolyString.fromOrig((String) value);
        }

        throw new UnsupportedOperationException(path + " is not supported");
    }

    public static PolicyError toPolicyError(PolicyViolationException e) {
        PolicyError.Builder builder = PolicyError.newBuilder();
        MessageWrapper wrapper = null;

        LocalizableMessage msg = e.getUserFriendlyMessage();
        if (msg instanceof SingleLocalizableMessage) {
            wrapper = toMessageWrapper((SingleLocalizableMessage) msg);

        } else if (msg instanceof LocalizableMessageList) {
            wrapper = toMessageWrapper((LocalizableMessageList) msg);
        }

        if (wrapper == null) {
            throw new UnsupportedOperationException(msg.getClass() + " is not supported");
        }

        builder.addErrors(wrapper);

        return builder.build();
    }

    private static MessageWrapper toMessageWrapper(LocalizableMessageList list) {
        MessageList messageList = toMessageList(list);
        return MessageWrapper.newBuilder()
                .setMsgListArg(messageList)
                .build();
    }

    private static MessageList toMessageList(LocalizableMessageList list) {
        MessageList.Builder builder = MessageList.newBuilder();

        for (LocalizableMessage msg : list.getMessages()) {
            MessageWrapper wrapper = null;
            if (msg instanceof SingleLocalizableMessage) {
                wrapper = toMessageWrapper((SingleLocalizableMessage) msg);

            } else if (msg instanceof LocalizableMessageList) {
                wrapper = toMessageWrapper((LocalizableMessageList) msg);
            }

            builder.addArgs(wrapper);
        }

        return builder.build();
    }

    private static MessageWrapper toMessageWrapper(SingleLocalizableMessage msg) {
        return MessageWrapper.newBuilder()
                .setMsgArg(toMessage(msg))
                .build();
    }

    private static Message toMessage(SingleLocalizableMessage msg) {
        return Message.newBuilder()
                .setKey(msg.getKey())
                .addAllArgs(toMessageWrappers(msg.getArgs()))
                .build();
    }

    private static Iterable<? extends MessageWrapper> toMessageWrappers(Object[] args) {
        List<MessageWrapper> list = new ArrayList<>();

        for (Object arg : args) {
            MessageWrapper wrapper;

            if (arg instanceof SingleLocalizableMessage) {
                wrapper = toMessageWrapper((SingleLocalizableMessage) arg);
            } else if (arg instanceof LocalizableMessageList) {
                wrapper = toMessageWrapper((LocalizableMessageList) arg);
            } else {
                wrapper = MessageWrapper.newBuilder()
                        .setStringArg(arg != null ? arg.toString() : "")
                        .build();
            }

            list.add(wrapper);
        }

        return list;
    }
}