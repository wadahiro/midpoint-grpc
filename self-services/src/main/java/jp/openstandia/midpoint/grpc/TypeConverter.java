package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.impl.query.builder.QueryBuilder;
import com.evolveum.midpoint.prism.match.MatchingRule;
import com.evolveum.midpoint.prism.match.MatchingRuleRegistry;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.FilterCreationUtil;
import com.evolveum.midpoint.prism.query.FilterUtil;
import com.evolveum.midpoint.prism.query.ObjectFilter;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.schema.PrismSchema;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.SearchResultList;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.util.ObjectTypeUtil;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.schema.constants.SchemaConstants.NS_MODEL_EXTENSION;
import static com.sun.tools.xjc.reader.Ring.add;

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
        } else if (msg == null) {
            wrapper = toMessages(new Object[]{e.getMessage()}).iterator().next();
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

    public static String toMessage(String string) {
        if (string == null) {
            return null;
        }
        return string;
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


    public static List<ReferenceMessage> toMessage(List<ObjectReferenceType> ref, Map<String, AbstractRoleType> resolved) {
        return ref.stream()
                .map(x -> toMessage(x, resolved.get(x.getOid())))
                .collect(Collectors.toList());
    }

    public static ReferenceMessage toMessage(ObjectReferenceType ref, AbstractRoleType resolved) {
        if (ref == null) {
            return null;
        }

        QName relation = ref.getRelation();
        ReferenceMessage.Builder builder = BuilderWrapper.wrap(ReferenceMessage.newBuilder())
                .nullSafe(toMessage(ref.getTargetName()), (b, v) -> b.setName(v))
                .unwrap()
                .setOid(ref.getOid())
                .setRelation(
                        QNameMessage.newBuilder()
                                .setNamespaceURI(relation.getNamespaceURI())
                                .setLocalPart(relation.getLocalPart())
                                .setPrefix(relation.getPrefix())
                );
        if (resolved != null) {
            builder = BuilderWrapper.wrap(builder)
                    .nullSafe(toMessage(resolved.getName()), (b, v) -> b.setName(v))
                    .nullSafe(toMessage(resolved.getDisplayName()), (b, v) -> b.setDisplayName(v))
                    .nullSafe(toMessage(resolved.getDescription()), (b, v) -> b.setDescription(v))
                    .unwrap();
        }

        return builder.build();
    }

    public static <T> List<T> toRealValue(PrismContext prismContext, Class<T> toClass, List<?> message) {
        return message.stream().map(x -> toRealValue(prismContext, toClass, x)).collect(Collectors.toList());
    }

    public static <T> T toRealValue(PrismContext prismContext, Class<T> toClass, Object message) {
        if (message instanceof String) {
            return toRealValue((String) message);
        }
        if (message instanceof ExtensionValue) {
            return toRealValue(prismContext, (ExtensionValue) message);
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

    public static <T> T toRealValue(PrismContext prismContext, ExtensionValue message) {
        if (message.hasInteger()) {
            return (T) toRealValue(message.getInteger());
        }
        if (message.hasLong()) {
            return (T) toRealValue(message.getLong());
        }
        if (message.hasPolyString()) {
            return (T) toRealValue(message.getPolyString());
        }
        if (message.hasRef()) {
            return (T) toRealValue(prismContext, message.getRef());
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

    public static Collection<? extends AssignmentType> toRealValue(PrismContext prismContext, List<AssignmentMessage> assignmentList) {
        return assignmentList.stream()
                .map(x -> toRealValue(prismContext, x))
                .filter(x -> x != null)
                .collect(Collectors.toList());
    }

    public static AssignmentType toRealValue(PrismContext prismContext, AssignmentMessage message) {
        AssignmentType assignment = new AssignmentType();
        assignment.setTargetRef(toRealValue(prismContext, message.getTargetRef()));
        if (message.hasOrgRef()) {
            assignment.setOrgRef(toRealValue(prismContext, message.getOrgRef()));
        }
        if (!message.getExtensionMap().isEmpty()) {
            try {
                addExtensionType(prismContext, assignment.asPrismContainerValue(), message.getExtensionMap());
            } catch (SchemaException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withCause(e)
                        .withDescription("invalid_assignment_extension")
                        .asRuntimeException();
                throw exception;
            }
        }
        return assignment;
    }

    private static ObjectReferenceType toRealValue(PrismContext prismContext, ReferenceMessage message) {
        ObjectReferenceType ref = new ObjectReferenceType();

        // Find the target by name or oid
        if (message.hasName()) {
            ObjectFilter filter = QueryBuilder.queryFor(ObjectType.class, prismContext)
                    .item(ObjectType.F_NAME)
                    .eqPoly(message.getName().getOrig())
                    .build()
                    .getFilter();

            SearchFilterType searchFilterType = null;
            try {
                searchFilterType = prismContext.getQueryConverter().createSearchFilterType(filter);
            } catch (SchemaException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withCause(e)
                        .withDescription("invalid_filter")
                        .asRuntimeException();
                throw exception;
            }
            ref.setFilter(searchFilterType);
        } else if (!message.getOid().isEmpty()) {
            ref.setOid(message.getOid());
        } else {
            return null;
        }

        if (message.hasRelation()) {
            ref.setRelation(toRealValue(message.getRelation()));
        } else if (message.getRelationWrapperCase() == ReferenceMessage.RelationWrapperCase.RELATION_TYPE) {
            ref.setRelation(toRealValue(message.getRelationType()));
        } else {
            // Use default relation if missing relation
            ref.setRelation(prismContext.getDefaultRelation());
        }

        if (message.hasType()) {
            ref.setType(toRealValue(message.getType()));
        } else if (message.getTypeWrapperCase() == ReferenceMessage.TypeWrapperCase.OBJECT_TYPE) {
            ref.setType(toRealValue(message.getObjectType()));
        } else {
            StatusRuntimeException exception = Status.INVALID_ARGUMENT
                    .withDescription("invalid_relation")
                    .asRuntimeException();
            throw exception;
        }

        return ref;
    }


//    private static ObjectQuery createSearchByName(PrismContext prismContext, Class<? extends Containerable> queryClass, PolyStringMessage message)
//            throws SchemaException {
//        if (!message.getOrig().isEmpty()) {
//            return prismContext.queryFor(queryClass)
//                    .item(ObjectType.F_NAME)
//                    .eq(message.getOrig())
//                    .matching(PrismConstants.POLY_STRING_ORIG_MATCHING_RULE_NAME)
//                    .build();
//        } else if (!message.getNorm().isEmpty()) {
//            return prismContext.queryFor(queryClass)
//                    .item(ObjectType.F_NAME)
//                    .eq(message.getNorm())
//                    .matching(PrismConstants.POLY_STRING_NORM_MATCHING_RULE_NAME)
//                    .build();
//        }
//        return null;
//    }

    public static QName toRealValue(QNameMessage message) {
        if (message.getNamespaceURI().isEmpty() && message.getLocalPart().isEmpty()) {
            return SchemaConstants.ORG_DEFAULT;
        }

        if (message.getNamespaceURI().isEmpty()) {
            return new QName(message.getLocalPart());
        }
        if (message.getPrefix().isEmpty()) {
            return new QName(message.getNamespaceURI(), message.getLocalPart());
        }
        return new QName(message.getNamespaceURI(), message.getLocalPart(), message.getPrefix());
    }

    public static QName toRealValue(DefaultRelationType type) {
        switch (type) {
            case ORG_DEFAULT:
                return SchemaConstants.ORG_DEFAULT;
            case ORG_MANAGER:
                return SchemaConstants.ORG_MANAGER;
            case ORG_META:
                return SchemaConstants.ORG_META;
            case ORG_DEPUTY:
                return SchemaConstants.ORG_DEPUTY;
            case ORG_APPROVER:
                return SchemaConstants.ORG_APPROVER;
            case ORG_OWNER:
                return SchemaConstants.ORG_OWNER;
            case ORG_CONSENT:
                return SchemaConstants.ORG_CONSENT;
        }
        return null;
    }

    public static QName toRealValue(DefaultObjectType type) {
        switch (type) {
            case OBJECT_TYPE:
                return ObjectType.COMPLEX_TYPE;
            case FOCUS_TYPE:
                return FocusType.COMPLEX_TYPE;
            case USER_TYPE:
                return UserType.COMPLEX_TYPE;
            case ROLE_TYPE:
                return RoleType.COMPLEX_TYPE;
            case ORG_TYPE:
                return OrgType.COMPLEX_TYPE;
            case SERVICE_TYPE:
                return ServiceType.COMPLEX_TYPE;
            case ASSIGNMENT_HOLDER_TYPE:
                return AssignmentHolderType.COMPLEX_TYPE;
        }
        return null;
    }

    public static boolean isEmpty(PolyStringMessage message) {
        if (message.getNorm().isEmpty() && message.getOrig().isEmpty()) {
            return true;
        }
        return false;
    }

    public static void addExtensionType(PrismContext prismContext, ObjectType object, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return;
        }

        addExtensionType(prismContext, object.asPrismObject(), map);
    }

    public static void addExtensionType(PrismContext prismContext, PrismObject object, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return;
        }

        PrismContainer extension = object.findOrCreateContainer(ObjectType.F_EXTENSION);
        addExtensionType(prismContext, extension, map);
    }

    public static void addExtensionType(PrismContext prismContext, PrismContainerValue object, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return;
        }

        PrismContainer extension = object.findOrCreateContainer(ObjectType.F_EXTENSION);
        addExtensionType(prismContext, extension, map);
    }

    public static void addExtensionType(PrismContext prismContext, PrismContainer extension, Map<String, ExtensionMessage> map) throws SchemaException {
        if (map.isEmpty()) {
            return;
        }

        for (Map.Entry<String, ExtensionMessage> entry : map.entrySet()) {
            String k = entry.getKey();
            ExtensionMessage v = entry.getValue();

            QName qname = toRealValue(v, k);

            List<Object> realValue = null;
            if (!v.getValueList().isEmpty()) {
                List<ExtensionValue> ev = v.getValueList();
                realValue = toRealValue(prismContext, Object.class, ev);
            }

            if (realValue == null || realValue.isEmpty()) {
                continue;
            }

            Object o = realValue.get(0);

            if (o instanceof ObjectReferenceType) {
                PrismReference reference = extension.findOrCreateReference(ItemPath.create(qname));

                if (reference.isSingleValue()) {
                    reference.add(((ObjectReferenceType) o).asReferenceValue());
                } else {
                    for (Object val : realValue) {
                        reference.add(((ObjectReferenceType) val).asReferenceValue());
                    }
                }

            } else {
                PrismProperty property = extension.findOrCreateProperty(ItemPath.create(qname));

                if (property.isSingleValue()) {
                    if (!v.getIsSingleValue()) {
                        throw new SchemaException("Cannot set as multiple value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                    }
                    property.setRealValue(o);
                } else {
                    if (v.getIsSingleValue()) {
                        throw new SchemaException("Cannot set as single value. namespaceURI: " + v.getNamespaceURI() + ", localPart: " + k);
                    }
                    property.setRealValue(realValue);
                }
            }
        }
    }

    public static QName toRealValue(ExtensionMessage message, String localPart) {
        if (message.getNamespaceURI().isEmpty()) {
            return new QName(localPart);
        }
        return new QName(message.getNamespaceURI(), localPart);
    }

    public static PrismObject<UserType> toPrismObject(PrismContext prismContext, RepositoryService repo, UserTypeMessage message) throws SchemaException {
        UserType user = new UserType(prismContext);

        // ObjectType
        user.setName(toRealValue(message.getName()));
        user.setDescription(toRealValue(message.getDescription()));
        user.createSubtypeList().addAll(toRealValue(prismContext, String.class, message.getSubtypeList()));
        user.setLifecycleState(toRealValue(message.getLifecycleState()));

        // AssignmentHolderType
        user.createAssignmentList().addAll(toRealValue(prismContext, message.getAssignmentList()));
        // can't set Archetype here directly because it throws policy error always.

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
        user.createOrganizationList().addAll(toRealValue(prismContext, PolyStringType.class, message.getOrganizationList()));
        user.createOrganizationalUnitList().addAll(toRealValue(prismContext, PolyStringType.class, message.getOrganizationalUnitList()));

        // Extension
        addExtensionType(prismContext, user, message.getExtensionMap());

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