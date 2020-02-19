package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExtensionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.google.protobuf.ByteString;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
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
        Message wrapper = null;

        LocalizableMessage msg = e.getUserFriendlyMessage();
        if (msg instanceof SingleLocalizableMessage) {
            wrapper = toMessage((SingleLocalizableMessage) msg);

        } else if (msg instanceof LocalizableMessageList) {
            wrapper = toMessage((LocalizableMessageList) msg);
        }

        if (wrapper == null) {
            throw new UnsupportedOperationException(msg.getClass() + " is not supported");
        }

        builder.setMessage(wrapper);

        return builder.build();
    }

    private static Message toMessage(LocalizableMessageList list) {
        MessageList messageList = toMessageList(list);
        return Message.newBuilder()
                .setList(messageList)
                .build();
    }

    private static MessageList toMessageList(LocalizableMessageList list) {
        MessageList.Builder builder = MessageList.newBuilder();

        for (LocalizableMessage msg : list.getMessages()) {
            Message wrapper = null;
            if (msg instanceof SingleLocalizableMessage) {
                wrapper = toMessage((SingleLocalizableMessage) msg);

            } else if (msg instanceof LocalizableMessageList) {
                wrapper = toMessage((LocalizableMessageList) msg);
            }

            if (wrapper == null) {
                throw new UnsupportedOperationException(msg.getClass() + " is not supported");
            }

            builder.addMessage(wrapper);
        }

        return builder.build();
    }

    private static Message toMessage(SingleLocalizableMessage msg) {
        return Message.newBuilder()
                .setSingle(toSingleMessage(msg))
                .build();
    }

    private static SingleMessage toSingleMessage(SingleLocalizableMessage msg) {
        return SingleMessage.newBuilder()
                .setKey(msg.getKey())
                .addAllArgs(toMessages(msg.getArgs()))
                .build();
    }

    private static Iterable<? extends Message> toMessages(Object[] args) {
        List<Message> list = new ArrayList<>();

        for (Object arg : args) {
            Message wrapper;

            if (arg instanceof SingleLocalizableMessage) {
                wrapper = toMessage((SingleLocalizableMessage) arg);
            } else if (arg instanceof LocalizableMessageList) {
                wrapper = toMessage((LocalizableMessageList) arg);
            } else {
                wrapper = Message.newBuilder()
                        .setString(arg != null ? arg.toString() : "")
                        .build();
            }

            list.add(wrapper);
        }

        return list;
    }

    public static BytesMessage toMessage(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        return BytesMessage.newBuilder()
                .setValue(ByteString.copyFrom(bytes))
                .build();
    }

    public static PolyStringMessage toMessage(PolyString polyString) {
        if (polyString == null) {
            return null;
        }
        return PolyStringMessage.newBuilder()
                .setOrig(polyString.getOrig())
                .setNorm(polyString.getNorm())
                .build();
    }

    public static PolyStringMessage toMessage(PolyStringType polyStringType) {
        if (polyStringType == null) {
            return null;
        }
        return PolyStringMessage.newBuilder()
                .setOrig(polyStringType.getOrig())
                .setNorm(polyStringType.getNorm())
                .build();
    }

    public static List<PolyStringMessage> toMessage(List<PolyStringType> polyStringType) {
        return polyStringType.stream()
                .map(x -> toMessage(x))
                .collect(Collectors.toList());
    }

    public static byte[] toBytes(BytesMessage message) {
        if (message.getBytesOptionalCase() == BytesMessage.BytesOptionalCase.BYTESOPTIONAL_NOT_SET) {
            return null;
        }
        return message.getValue().toByteArray();
    }

    public static PolyStringType toPolyStringType(PolyStringMessage message) {
        if (isEmpty(message)) {
            return null;
        }
        return new PolyStringType(new PolyString(message.getOrig(), message.getNorm()));
    }

    public static boolean isEmpty(PolyStringMessage message) {
        if (message.getNorm().isEmpty() && message.getOrig().isEmpty()) {
            return true;
        }
        return false;
    }

    public static List<PolyStringType> toPolyStringType(List<PolyStringMessage> message) {
        return message.stream()
                .filter(x -> !isEmpty(x))
                .map(x -> toPolyStringType(x))
                .collect(Collectors.toList());
    }

    public static ExtensionType toExtensionType(SchemaRegistry registry, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return null;
        }
        ExtensionType extension = new ExtensionType();
        PrismContainerValue extensionValue = extension.asPrismContainerValue();

        for (Map.Entry<String, ExtensionMessage> entry : map.entrySet()) {
            String k = entry.getKey();
            ExtensionMessage v = entry.getValue();
//            PrismSchema schema = registry.findSchemaByNamespace(v.getNamespaceURI());
//            if (schema == null) {
//                throw new SchemaException("Cannot find schema for extension. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
//            }
//
//            ItemDefinition definition = schema.findItemDefinitionByElementName(new QName(v.getNamespaceURI(), k));
//            if (definition == null) {
//                throw new SchemaException("Cannot find definition for extension. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
//            }

            PrismProperty property = extensionValue.createProperty(new QName(v.getNamespaceURI(), k));
            if (property.isSingleValue()) {
                if (!v.getIsSingleValue()) {
                    throw new SchemaException("Cannot set as multiple value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                }
                if (!v.getValueList().isEmpty()) {
                    property.setRealValue(v.getValueList().get(0));
                }
            } else {
                if (v.getIsSingleValue()) {
                    throw new SchemaException("Cannot set as single value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                }
                if (!v.getValueList().isEmpty()) {
                    property.setRealValue(v.getValueList());
                }
            }
        }

        return extension;
    }

    public static PrismObject<UserType> toPrismObject(PrismContext context, UserTypeMessage message) throws SchemaException {
        UserType user = new UserType(context);

        // ObjectType
        user.setName(nullable(toPolyStringType(message.getName())));
        user.setDescription(nullable(message.getDescription()));
        user.createSubtypeList().addAll(message.getSubtypeList());
        user.setLifecycleState(nullable(message.getLifecycleState()));

        // FocusType
        user.setJpegPhoto(nullable(toBytes(message.getJpegPhoto())));
        user.setCostCenter(nullable(message.getCostCenter()));
        user.setLocality(nullable(toPolyStringType(message.getLocality())));
        user.setPreferredLanguage(nullable(message.getPreferredLanguage()));
        user.setLocale(nullable(message.getLocale()));
        user.setTimezone(nullable(message.getTimezone()));
        user.setEmailAddress(nullable(message.getEmailAddress()));
        user.setTelephoneNumber(nullable(message.getTelephoneNumber()));

        // UserType
        user.setFullName(nullable(toPolyStringType(message.getFullName())));
        user.setGivenName(nullable(toPolyStringType(message.getGivenName())));
        user.setFamilyName(nullable(toPolyStringType(message.getFamilyName())));
        user.setAdditionalName(nullable(toPolyStringType(message.getAdditionalName())));
        user.setNickName(nullable(toPolyStringType(message.getNickName())));
        user.setHonorificPrefix(nullable(toPolyStringType(message.getHonorificPrefix())));
        user.setHonorificSuffix(nullable(toPolyStringType(message.getHonorificSuffix())));
        user.setTitle(nullable(toPolyStringType(message.getTitle())));
        user.setEmployeeNumber(nullable(message.getEmployeeNumber()));
        user.createOrganizationList().addAll(toPolyStringType(message.getOrganizationList()));
        user.createOrganizationalUnitList().addAll(toPolyStringType(message.getOrganizationalUnitList()));

        // Extension
        user.setExtension(nullable(toExtensionType(null, message.getExtensionMap())));

        PrismObject<UserType> object = user.asPrismObject();

        return user.asPrismObject();
    }

    public static UserTypeMessage toMessage(PrismObject<UserType> user) {
        UserType u = user.getRealValue();
        return BuilderWrapper.wrap(UserTypeMessage.newBuilder())
                // ObjectType
                .nullSafe(toMessage(u.getName()), (b, v) -> b.setName(v))
                .nullSafe(u.getDescription(), (b, v) -> b.setDescription(v))
                .nullSafe(u.getSubtype(), (b, v) -> b.addAllSubtype(v))
                .nullSafe(u.getLifecycleState(), (b, v) -> b.setLifecycleState(v))
                // FocusType
                .nullSafe(toMessage(u.getJpegPhoto()), (b, v) -> b.setJpegPhoto(v))
                .nullSafe(u.getCostCenter(), (b, v) -> b.setCostCenter(v))
                .nullSafe(toMessage(u.getLocality()), (b, v) -> b.setLocality(v))
                .nullSafe(u.getPreferredLanguage(), (b, v) -> b.setPreferredLanguage(v))
                .nullSafe(u.getLocale(), (b, v) -> b.setLocale(v))
                .nullSafe(u.getTimezone(), (b, v) -> b.setTimezone(v))
                .nullSafe(u.getEmailAddress(), (b, v) -> b.setEmailAddress(v))
                .nullSafe(u.getTelephoneNumber(), (b, v) -> b.setTelephoneNumber(v))
                // UserType
                .nullSafe(toMessage(u.getFullName()), (b, v) -> b.setFullName(v))
                .nullSafe(toMessage(u.getGivenName()), (b, v) -> b.setGivenName(v))
                .nullSafe(toMessage(u.getFamilyName()), (b, v) -> b.setFamilyName(v))
                .nullSafe(toMessage(u.getAdditionalName()), (b, v) -> b.setAdditionalName(v))
                .nullSafe(toMessage(u.getNickName()), (b, v) -> b.setNickName(v))
                .nullSafe(toMessage(u.getHonorificPrefix()), (b, v) -> b.setHonorificPrefix(v))
                .nullSafe(toMessage(u.getHonorificSuffix()), (b, v) -> b.setHonorificSuffix(v))
                .nullSafe(toMessage(u.getTitle()), (b, v) -> b.setTitle(v))
                .nullSafe(u.getEmployeeNumber(), (b, v) -> b.setEmployeeNumber(v))
                .nullSafe(toMessage(u.getOrganization()), (b, v) -> b.addAllOrganization(v))
                .nullSafe(toMessage(u.getOrganizationalUnit()), (b, v) -> b.addAllOrganizationalUnit(v))
                // Extension
                .nullSafe(toExtensionMessageMap(user), (b, v) -> b.putAllExtension(v))
                .unwrap()
                .build();
    }

    public static Map<String, ExtensionMessage> toExtensionMessageMap(PrismObject<?> object) {
        PrismContainer<?> extension = object.getExtension();
        if (extension == null) {
            return null;
        }

        Map<String, ExtensionMessage> map = new LinkedHashMap<>();

        PrismContainerValue<?> extensionValue = extension.getValue();
        for (Item item : extensionValue.getItems()) {
            ItemDefinition definition = item.getDefinition();

            ExtensionMessage.Builder extBuilder = ExtensionMessage.newBuilder();

            if (item.isSingleValue()) {
                extBuilder.setIsSingleValue(true);
                addExtensionEntryValue(extBuilder, definition, item.getRealValue());
            } else {
                extBuilder.setIsSingleValue(false);
                for (Object val : item.getRealValues()) {
                    addExtensionEntryValue(extBuilder, definition, val);
                }
            }
            // Currently, it doesn't use namespaceURI as the key
            String key = definition.getItemName().getLocalPart();
            map.put(key, extBuilder.build());
        }

        return map;
    }

    private static void addExtensionEntryValue(ExtensionMessage.Builder extBuilder, ItemDefinition definition, Object value) {
        ExtensionValue.Builder entryValueBuilder = ExtensionValue.newBuilder();
        if (definition.getTypeClass() == String.class) {
            entryValueBuilder.setString((String) value);
        } else if (definition.getTypeClass() == PolyString.class) {
            entryValueBuilder.setPolyString(toMessage((PolyString) value));
        }
        extBuilder.addValue(entryValueBuilder);
    }

    private static <T> T nullable(T s) {
        if (s != null) {
            if (s instanceof String) {
                if (((String) s).isEmpty()) {
                    return null;
                }
            } else if (s instanceof byte[]) {
                byte[] b = (byte[]) s;
                if (b.length == 0) {
                    return null;
                }
            } else if (s instanceof IntegerMessage) {
                IntegerMessage i = (IntegerMessage) s;
                if (i.getIntOptionalCase() == IntegerMessage.IntOptionalCase.INTOPTIONAL_NOT_SET) {
                    return null;
                }
            } else if (s instanceof LongMessage) {
                LongMessage i = (LongMessage) s;
                if (i.getLongOptionalCase() == LongMessage.LongOptionalCase.LONGOPTIONAL_NOT_SET) {
                    return null;
                }
            }
        }
        return s;
    }
}