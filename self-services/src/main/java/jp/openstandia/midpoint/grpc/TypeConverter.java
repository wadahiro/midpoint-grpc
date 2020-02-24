package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.impl.query.builder.QueryBuilder;
import com.evolveum.midpoint.prism.path.ItemName;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.polystring.PolyString;
import com.evolveum.midpoint.prism.query.*;
import com.evolveum.midpoint.prism.query.builder.S_AtomicFilterExit;
import com.evolveum.midpoint.prism.query.builder.S_FilterEntry;
import com.evolveum.midpoint.prism.query.builder.S_FilterEntryOrEmpty;
import com.evolveum.midpoint.prism.query.builder.S_MatchingRuleEntry;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.SingleLocalizableMessage;
import com.evolveum.midpoint.util.exception.PolicyViolationException;
import com.evolveum.midpoint.util.exception.SchemaException;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.SearchFilterType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.google.protobuf.ByteString;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.namespace.QName;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.*;
import java.util.stream.Collectors;

public class TypeConverter {

    private static Map<DefaultUserTypePath, ItemName> userTypeMap = new HashMap<>();
    private static Map<ItemName, Class> userValueTypeMap = new HashMap<>();

    static {
        Map<String, ItemName> strToItemName = new HashMap<>();

        Field[] fields = UserType.class.getFields();
        Arrays.stream(fields)
                .filter(x -> x.getName().startsWith("F_") && x.getType() == ItemName.class)
                .forEach(x -> {
                    String name = x.getName();
                    try {
                        DefaultUserTypePath path = DefaultUserTypePath.valueOf(name);
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

    public static ItemName toItemName(DefaultUserTypePath path) {
        ItemName itemName = userTypeMap.get(path);
        if (itemName == null) {
            throw new UnsupportedOperationException(path + " is not supported");
        }
        return itemName;
    }

    public static Object toRealValue(String value, Class clazz) {
        if (clazz.isAssignableFrom(String.class)) {
            return value;
        }
        if (clazz.isAssignableFrom(PolyString.class)) {
            return PolyString.fromOrig((String) value);
        }
        if (clazz.isAssignableFrom(Integer.class)) {
            return Integer.parseInt(value);
        }
        if (clazz.isAssignableFrom(Long.class)) {
            return Long.parseLong(value);
        }
        // TODO more type

        throw new UnsupportedOperationException(clazz + " is not supported");
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
                    .nullSafe(toMessage(resolved.getEmailAddress()), (b, v) -> b.setEmailAddress(v))
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

    private static Class<? extends Containerable> toObjectClass(QName type) {
        // TODO midpoint has utility method for this converting?
        if (type == ObjectType.COMPLEX_TYPE) {
            return ObjectType.class;
        }
        if (type == FocusType.COMPLEX_TYPE) {
            return FocusType.class;
        }
        if (type == AssignmentHolderType.COMPLEX_TYPE) {
            return AssignmentHolderType.class;
        }
        if (type == UserType.COMPLEX_TYPE) {
            return UserType.class;
        }
        if (type == AbstractRoleType.COMPLEX_TYPE) {
            return AbstractRoleType.class;
        }
        if (type == RoleType.COMPLEX_TYPE) {
            return RoleType.class;
        }
        if (type == OrgType.COMPLEX_TYPE) {
            return OrgType.class;
        }
        if (type == ServiceType.COMPLEX_TYPE) {
            return ServiceType.class;
        }
        // TODO add more type converter

        return ObjectType.class;
    }

    public static ObjectQuery toObjectQuery(PrismContext prismContext, Class<? extends Containerable> queryClass,
                                            QueryMessage message) {
        S_FilterEntryOrEmpty builder = QueryBuilder.queryFor(queryClass, prismContext);
        S_AtomicFilterExit exitFilter = toObjectFilter(builder, message.getFilter());

        if (exitFilter != null) {
            ObjectQuery query = exitFilter.build();
            query.setPaging(toMessage(prismContext, message.getPaging()));
            return query;
        }

        return null;
    }

    public static ObjectPaging toMessage(PrismContext prismContext, PagingMessage message) {
        ObjectPaging paging = prismContext.queryFactory().createPaging();
        paging.setOffset(message.getOffset());
        if (message.getMaxSize() != 0) {
            paging.setMaxSize(message.getMaxSize());
        }
        paging.setOrdering(toObjectOrdering(prismContext, message.getOrderingList()));
        return paging;
    }

    public static Collection<? extends ObjectOrdering> toObjectOrdering(PrismContext prismContext, List<ObjectOrderingMessage> message) {
        return message.stream().map(x -> {
            OrderDirection direction = toRealValue(x.getOrderDirection());
            String orderBy = x.getOrderBy();
            ItemPath itemPath = ItemPath.create(orderBy);

            return prismContext.queryFactory().createOrdering(itemPath, direction);
        }).collect(Collectors.toList());
    }

    private static OrderDirection toRealValue(OrderDirectionType message) {
        switch (message) {
            case ASCEDING:
                return OrderDirection.ASCENDING;
            case DESCENDING:
                return OrderDirection.DESCENDING;
        }
        return OrderDirection.ASCENDING;
    }

    public static S_AtomicFilterExit toObjectFilter(S_FilterEntryOrEmpty builder, ObjectFilterMessage message) {
        if (message.hasAnd()) {
            return toAndQuery(builder, message.getAnd());
        } else if (message.hasOr()) {
            return toOrQuery(builder, message.getOr());

        } else if (message.hasEq()) {
            return toEqFilter(builder, message.getEq());
        } else if (message.hasStartsWith()) {
            return toStartsWithFilter(builder, message.getStartsWith());
        } else if (message.hasContains()) {
            return toContainsFilter(builder, message.getContains());
        } else if (message.hasEndsWith()) {
            return toEndsWithFilter(builder, message.getEndsWith());

        } else if (message.hasEqPolyString()) {
            return toEqPolyFilter(builder, message.getEqPolyString());
        } else if (message.hasStartsWithPolyString()) {
            return toStartsWithPolyFilter(builder, message.getStartsWithPolyString());
        } else if (message.hasContainsPolyString()) {
            return toContainsPolyFilter(builder, message.getContainsPolyString());
        } else if (message.hasEndsWithPolyString()) {
            return toEndsWithPolyFilter(builder, message.getEndsWithPolyString());
        }
        return null;
    }

    public static S_AtomicFilterExit toObjectFilter(S_FilterEntry builder, ObjectFilterMessage message) {
        if (message.hasAnd()) {
            return toAndQuery(builder, message.getAnd());
        } else if (message.hasOr()) {
            return toOrQuery(builder, message.getOr());
        }

        if (builder instanceof S_FilterEntryOrEmpty) {
            return toObjectFilter((S_FilterEntryOrEmpty) builder, message);
        }

        return null;
    }

    public static ItemPath toItemPath(FilterEntryMessage message) {
        return toItemPath(message.getFullPath());
    }

    public static ItemPath toItemPath(String message) {
        return ItemPath.create(message.split("/"));
    }

    public static S_AtomicFilterExit toEqFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .eq(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toStartsWithFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .startsWith(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    private static S_AtomicFilterExit toContainsFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .contains(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toEndsWithFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .endsWith(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toEqPolyFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .eqPoly(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toStartsWithPolyFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .startsWithPoly(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    private static S_AtomicFilterExit toContainsPolyFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .containsPoly(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toEndsWithPolyFilter(S_FilterEntryOrEmpty builder, FilterEntryMessage message) {
        S_MatchingRuleEntry filter = builder.item(toItemPath(message))
                .endsWithPoly(message.getValue());

        if (message.hasMatchingRule()) {
            return filter.matching(toRealValue(message.getMatchingRule()));
        }
        return filter;
    }

    public static S_AtomicFilterExit toAndQuery(S_FilterEntry builder, AndFilterMessage message) {
        List<ObjectFilterMessage> list = message.getConditionsList();

        for (int i = 0; i < list.size(); i++) {
            ObjectFilterMessage filter = list.get(i);
            S_AtomicFilterExit f = toObjectFilter(builder, filter);

            if (i < list.size() - 1) {
                builder = f.and();
            } else {
                return f;
            }
        }
        return null;
    }

    public static S_AtomicFilterExit toOrQuery(S_FilterEntry builder, OrFilterMessage message) {
        List<ObjectFilterMessage> list = message.getConditionsList();

        for (int i = 0; i < list.size(); i++) {
            ObjectFilterMessage filter = list.get(i);
            S_AtomicFilterExit f = toObjectFilter(builder, filter);

            if (i < list.size() - 1) {
                builder = f.or();
            } else {
                return f;
            }
        }
        return null;
    }

    public static SearchFilterType toEqFilter(PrismContext prismContext, QName type, QName findKey, PolyStringMessage message) {
        Class<? extends Containerable> queryClass = toObjectClass(type);

        ObjectFilter filter = QueryBuilder.queryFor(queryClass, prismContext)
                .item(findKey)
                .eqPoly(message.getOrig())
                .matchingOrig()
                .build()
                .getFilter();

        try {
            return prismContext.getQueryConverter().createSearchFilterType(filter);
        } catch (SchemaException e) {
            StatusRuntimeException exception = Status.INVALID_ARGUMENT
                    .withCause(e)
                    .withDescription("invalid_filter")
                    .asRuntimeException();
            throw exception;
        }
    }

    public static SearchFilterType toEqFilter(PrismContext prismContext, QName type, QName findKey, String message, QName matchingRule) {
        Class<? extends Containerable> queryClass = toObjectClass(type);

        ObjectFilter filter = QueryBuilder.queryFor(queryClass, prismContext)
                .item(findKey)
                .eq(message)
                .matching(matchingRule)
                .build()
                .getFilter();

        try {
            return prismContext.getQueryConverter().createSearchFilterType(filter);
        } catch (SchemaException e) {
            StatusRuntimeException exception = Status.INVALID_ARGUMENT
                    .withCause(e)
                    .withDescription("invalid_filter")
                    .asRuntimeException();
            throw exception;
        }
    }

    public static ObjectReferenceType toRealValue(PrismContext prismContext, ReferenceMessage message) {
        ObjectReferenceType ref = new ObjectReferenceType();

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

        QName type = ref.getType();

        // Find the target by name, emailAddress or oid
        if (message.hasName()) {
            ref.setFilter(toEqFilter(prismContext, type, ObjectType.F_NAME, message.getName()));
        } else if (!message.getEmailAddress().isEmpty()) {
            ref.setFilter(toEqFilter(prismContext, type, FocusType.F_EMAIL_ADDRESS, message.getEmailAddress(), PrismConstants.STRING_IGNORE_CASE_MATCHING_RULE_NAME));
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


    public static UserTypeMessage toMessage(UserType u) {
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
                .nullSafe(toExtensionMessageMap(u.asPrismObject()), (b, v) -> b.putAllExtension(v))
                .unwrap()
                .build();
    }

    public static RoleTypeMessage toMessage(RoleType u) {
        return BuilderWrapper.wrap(RoleTypeMessage.newBuilder())
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
                // RoleType
                .nullSafe(u.getRoleType(), (b, v) -> b.setRoleType(v))
                // Extension
                .nullSafe(toExtensionMessageMap(u.asPrismObject()), (b, v) -> b.putAllExtension(v))
                .unwrap()
                .build();
    }

    public static OrgTypeMessage toMessage(OrgType u) {
        return BuilderWrapper.wrap(OrgTypeMessage.newBuilder())
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
                // OrgType
                .nullSafe(u.getOrgType(), (b, v) -> b.addAllOrgType(v))
                .nullSafe(u.isTenant(), (b, v) -> b.setTenant(v))
                .nullSafe(u.getMailDomain(), (b, v) -> b.addAllMailDomain(v))
                .nullSafe(u.getDisplayOrder(), (b, v) -> b.setDisplayOrder(v))
                // Extension
                .nullSafe(toExtensionMessageMap(u.asPrismObject()), (b, v) -> b.putAllExtension(v))
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

    public static ItemPath toRealValue(ItemPathMessage itemPath) {
        List<QName> qnames = itemPath.getPathList().stream()
                .map(x -> toRealValue(x))
                .collect(Collectors.toList());
        return ItemPath.create(qnames);
    }
}