package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.ExtensionType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.TaskType;
import com.evolveum.midpoint.xml.ns._public.common.common_3.UserType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.google.protobuf.ByteString;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.NS_MODEL_EXTENSION;

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

    public static <T> List<T> toRealValue(Class<T> toClass, List<?> message) {
        return message.stream().map(x -> toRealValue(toClass, x)).collect(Collectors.toList());
    }

    public static <T> T toRealValue(Class<T> toClass, Object message) {
        if (message instanceof String) {
            return toRealValue((String) message);
        }
        if (message instanceof ExtensionValue) {
            return toRealValue((ExtensionValue) message);
        }
        if (message instanceof PolyStringMessage) {
            return toRealValue((PolyStringMessage) message);
        }
        if (message instanceof BytesMessage) {
            return toRealValue((BytesMessage) message);
        }

        return null;
    }

    public static <T> T toRealValue(String message) {
        if (message == null || message.isEmpty()) {
            return null;
        }
        return (T) message;
    }

    public static <T> T toRealValue(BytesMessage message) {
        if (message.getBytesOptionalCase() == BytesMessage.BytesOptionalCase.BYTESOPTIONAL_NOT_SET) {
            return null;
        }
        return (T) message.getValue().toByteArray();
    }

    public static <T> T toRealValue(PolyStringMessage message) {
        if (isEmpty(message)) {
            return null;
        }
        return (T) new PolyStringType(new PolyString(message.getOrig(), message.getNorm()));
    }

    public static <T> T toRealValue(ExtensionValue message) {
        if (message.hasInteger()) {
            return (T) toRealValue(message.getInteger());
        }
        if (message.hasLong()) {
            return (T) toRealValue(message.getLong());
        }
        if (message.hasPolyString()) {
            return (T) toRealValue(message.getPolyString());
        }
        // string case
        return (T) message.getString();
    }

    public static Object toRealValue(IntegerMessage message) {
        if (message.getIntOptionalCase() == IntegerMessage.IntOptionalCase.INTOPTIONAL_NOT_SET) {
            return null;
        }
        return message.getValue();
    }

    public static Object toRealValue(LongMessage message) {
        if (message.getLongOptionalCase() == LongMessage.LongOptionalCase.LONGOPTIONAL_NOT_SET) {
            return null;
        }
        return message.getValue();
    }

    public static boolean isEmpty(PolyStringMessage message) {
        if (message.getNorm().isEmpty() && message.getOrig().isEmpty()) {
            return true;
        }
        return false;
    }

    public static <O extends Objectable> ExtensionType toExtensionType(PrismContext prismContext, Class<O> compileTimeClass, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return null;
        }

        PrismContainerDefinition<ExtensionType> extDef = prismContext.getSchemaRegistry()
                .findObjectDefinitionByCompileTimeClass(compileTimeClass)
                .findContainerDefinition(TaskType.F_EXTENSION);
        PrismContainer<ExtensionType> ext = extDef.instantiate();

        for (Map.Entry<String, ExtensionMessage> entry : map.entrySet()) {
            String k = entry.getKey();
            ExtensionMessage v = entry.getValue();

            ItemName itemName = new ItemName(v.getNamespaceURI(), k);
            PrismPropertyDefinition<Object> definition = extDef.findItemDefinition(itemName);
            if (definition == null) {
                throw new SchemaException("Cannot find definition for extension. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
            }

            PrismProperty<Object> property = definition.instantiate();

            if (property.isSingleValue()) {
                if (!v.getIsSingleValue()) {
                    throw new SchemaException("Cannot set as multiple value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                }
                if (!v.getValueList().isEmpty()) {
                    ExtensionValue ev = v.getValueList().get(0);
                    Object realValue = toRealValue(ev);
                    property.setRealValue(realValue);
                }
            } else {
                if (v.getIsSingleValue()) {
                    throw new SchemaException("Cannot set as single value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                }
                if (!v.getValueList().isEmpty()) {
                    List<ExtensionValue> ev = v.getValueList();
                    Object realValue = toRealValue(Object.class, ev);
                    property.setRealValue(realValue);
                }
            }
            ext.add(property);
        }

        return ext.getRealValue();
    }

    public static PrismObject<UserType> toPrismObject(PrismContext prismContext, UserTypeMessage message) throws SchemaException {
        UserType user = new UserType(prismContext);

        // ObjectType
        user.setName(toRealValue(message.getName()));
        user.setDescription(toRealValue(message.getDescription()));
        user.createSubtypeList().addAll(toRealValue(String.class, message.getSubtypeList()));
        user.setLifecycleState(toRealValue(message.getLifecycleState()));

        // FocusType
        user.setJpegPhoto(toRealValue(message.getJpegPhoto()));
        user.setCostCenter(toRealValue(message.getCostCenter()));
        user.setLocality((toRealValue(message.getLocality())));
        user.setPreferredLanguage(toRealValue(message.getPreferredLanguage()));
        user.setLocale(toRealValue(message.getLocale()));
        user.setTimezone(toRealValue(message.getTimezone()));
        user.setEmailAddress(toRealValue(message.getEmailAddress()));
        user.setTelephoneNumber(toRealValue(message.getTelephoneNumber()));

        // UserType
        user.setFullName(toRealValue(message.getFullName()));
        user.setGivenName(toRealValue(message.getGivenName()));
        user.setFamilyName(toRealValue(message.getFamilyName()));
        user.setAdditionalName(toRealValue(message.getAdditionalName()));
        user.setNickName(toRealValue(message.getNickName()));
        user.setHonorificPrefix(toRealValue(message.getHonorificPrefix()));
        user.setHonorificSuffix(toRealValue(message.getHonorificSuffix()));
        user.setTitle(toRealValue(message.getTitle()));
        user.setEmployeeNumber(toRealValue(message.getEmployeeNumber()));
        user.createOrganizationList().addAll(toRealValue(PolyStringType.class, message.getOrganizationList()));
        user.createOrganizationalUnitList().addAll(toRealValue(PolyStringType.class, message.getOrganizationalUnitList()));

        // Extension
        user.setExtension(toExtensionType(prismContext, UserType.class, message.getExtensionMap()));

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
}