package jp.openstandia.midpoint.grpc;

import com.evolveum.midpoint.gui.api.util.WebComponentUtil;
import com.evolveum.midpoint.model.api.ModelExecuteOptions;
import com.evolveum.midpoint.model.api.ModelInteractionService;
import com.evolveum.midpoint.model.api.ModelService;
import com.evolveum.midpoint.model.api.TaskService;
import com.evolveum.midpoint.model.api.context.ModelContext;
import com.evolveum.midpoint.model.impl.ModelCrudService;
import com.evolveum.midpoint.model.impl.util.ModelImplUtils;
import com.evolveum.midpoint.prism.*;
import com.evolveum.midpoint.prism.crypto.EncryptionException;
import com.evolveum.midpoint.prism.crypto.Protector;
import com.evolveum.midpoint.prism.delta.*;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.delta.builder.S_MaybeDelete;
import com.evolveum.midpoint.prism.delta.builder.S_ValuesEntry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.QueryFactory;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.ObjectTypes;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.LocalizationUtil;
import com.evolveum.midpoint.schema.util.ObjectQueryUtil;
import com.evolveum.midpoint.task.api.Task;
import com.evolveum.midpoint.task.api.TaskCategory;
import com.evolveum.midpoint.util.LocalizableMessage;
import com.evolveum.midpoint.util.LocalizableMessageList;
import com.evolveum.midpoint.util.LocalizableMessageListBuilder;
import com.evolveum.midpoint.util.exception.*;
import com.evolveum.midpoint.util.logging.LoggingUtils;
import com.evolveum.midpoint.util.logging.Trace;
import com.evolveum.midpoint.util.logging.TraceManager;
import com.evolveum.midpoint.xml.ns._public.common.common_3.*;
import com.evolveum.prism.xml.ns._public.query_3.QueryType;
import com.evolveum.prism.xml.ns._public.types_3.EvaluationTimeType;
import com.evolveum.prism.xml.ns._public.types_3.ObjectDeltaType;
import com.evolveum.prism.xml.ns._public.types_3.PolyStringType;
import com.evolveum.prism.xml.ns._public.types_3.ProtectedStringType;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.protobuf.ProtoUtils;
import io.grpc.stub.StreamObserver;
import org.lognet.springboot.grpc.GRpcService;
import org.springframework.beans.factory.annotation.Autowired;

import javax.xml.namespace.QName;
import java.util.*;
import java.util.stream.Collectors;

import static com.evolveum.midpoint.xml.ns._public.common.common_3.PartialProcessingTypeType.SKIP;
import static jp.openstandia.midpoint.grpc.TypeConverter.*;

@GRpcService
public class SelfServiceResource extends SelfServiceResourceGrpc.SelfServiceResourceImplBase implements MidPointGrpcService {

    private static final Trace LOGGER = TraceManager.getTrace(SelfServiceResource.class);

    static {
        LOGGER.info("SelfServiceResource loaded");
        System.out.println("SelfServiceResource loaded");
    }

    public static final String CLASS_DOT = SelfServiceResource.class.getName() + ".";
    public static final String OPERATION_SELF = CLASS_DOT + "self";
    public static final String OPERATION_SELF_ASSIGNMENT = CLASS_DOT + "selfAssignment";
    public static final String OPERATION_MODIFY_PROFILE = CLASS_DOT + "modifyProfile";
    public static final String OPERATION_ADD_USER = CLASS_DOT + "addUser";
    public static final String OPERATION_MODIFY_USER = CLASS_DOT + "modifyUser";
    public static final String OPERATION_GET_USER = CLASS_DOT + "getUser";
    public static final String OPERATION_ADD_ROLE = CLASS_DOT + "addRole";
    public static final String OPERATION_GET_ROLE = CLASS_DOT + "getRole";
    public static final String OPERATION_ADD_ORG = CLASS_DOT + "addOrg";
    public static final String OPERATION_GET_ORG = CLASS_DOT + "getOrg";
    public static final String OPERATION_ADD_SERVICE = CLASS_DOT + "addService";
    public static final String OPERATION_GET_SERVICE = CLASS_DOT + "getService";
    public static final String OPERATION_RECOMPUTE_OBJECT = CLASS_DOT + "recomputeObject";
    public static final String OPERATION_EXECUTE_USER_UPDATE = CLASS_DOT + "executeUserUpdate";
    public static final String OPERATION_EXECUTE_CREDENTIAL_CHECK = CLASS_DOT + "executeCredentialCheck";
    public static final String OPERATION_EXECUTE_CREDENTIAL_UPDATE = CLASS_DOT + "executeCredentialUpdate";
    public static final String OPERATION_REQUEST_ASSIGNMENTS = CLASS_DOT + "requestAssignments";
    public static final String OPERATION_SEARCH_OBJECTS = CLASS_DOT + "searchObjects";
    public static final String OPERATION_SEARCH_ASSIGNMENTS = CLASS_DOT + "searchAssignments";
    public static final String OPERATION_ADD_OBJECT = CLASS_DOT + "addObject";
    public static final String OPERATION_MODIFY_OBJECT = CLASS_DOT + "modifyObject";
    public static final String OPERATION_EXECUTE_OBJECT_UPDATE = CLASS_DOT + "executeObjectUpdate";
    public static final String OPERATION_DELETE_OBJECT = CLASS_DOT + "deleteObject";

    private static final long WAIT_FOR_TASK_STOP = 2000L;

    @Autowired
    protected ModelService modelService;
    @Autowired
    protected RepositoryService repositoryService;
    @Autowired
    protected ModelCrudService modelCrudService;
    @Autowired
    protected ModelInteractionService modelInteraction;
    @Autowired
    protected PrismContext prismContext;
    @Autowired
    protected Protector protector;
    @Autowired
    protected TaskService taskService;

    public static final Metadata.Key<PolicyError> PolicyErrorMetadataKey = ProtoUtils.keyForProto(PolicyError.getDefaultInstance());

    @Override
    public Metadata handlePolicyViolationException(PolicyViolationException e) {
        PolicyError error = TypeConverter.toPolicyError(e);

        Metadata metadata = new Metadata();
        metadata.put(PolicyErrorMetadataKey, error);

        return metadata;
    }

    /**
     * Getting self profile API based on
     * {@link com.evolveum.midpoint.model.impl.ModelRestService#getSelf(org.apache.cxf.jaxrs.ext.MessageContext)}.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSelf(GetSelfRequest request, StreamObserver<GetSelfResponse> responseObserver) {
        LOGGER.debug("Start getSelf");

        UserTypeMessage self = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SELF);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), getOptions, task, parentResult);

            parentResult.computeStatus();

            return TypeConverter.toUserTypeMessage(user.asObjectable(), getOptions);
        });

        GetSelfResponse res = GetSelfResponse.newBuilder()
                .setProfile(self)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End getSelf");
    }

    private class A {

    }

    /**
     * Getting self assignments API.
     *
     * @param request
     * @param responseObserver
     */
    @Override
    public void getSelfAssignment(GetSelfAssignmentRequest request, StreamObserver<GetSelfAssignmentResponse> responseObserver) {
        LOGGER.debug("Start getSelf");

        List<AssignmentMessage> assignments = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SELF_ASSIGNMENT);

            PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), null, task, parentResult);
            UserType userType = user.asObjectable();

            Set<String> directOids = userType.getAssignment().stream()
                    .map(x -> x.getTargetRef().getOid())
                    .collect(Collectors.toSet());

            Set<String> orgRefOids = Collections.emptySet();
            if (request.getIncludeOrgRefDetail()) {
                orgRefOids = userType.getAssignment().stream()
                        .filter(x -> x.getOrgRef() != null)
                        .map(x -> x.getOrgRef().getOid())
                        .collect(Collectors.toSet());
            }

            Set<String> indirectOids = Collections.emptySet();
            if (request.getIncludeIndirect()) {
                indirectOids = userType.getRoleMembershipRef().stream()
                        .map(x -> x.getOid())
                        .collect(Collectors.toSet());
            }

            // For caching detail of the target objects
            Set<String> oids = new HashSet<>();
            oids.addAll(directOids);
            oids.addAll(orgRefOids);
            oids.addAll(indirectOids);

            ObjectQuery query = prismContext.queryFor(AbstractRoleType.class)
                    .id(oids.toArray(new String[0]))
                    .build();
            Collection<SelectorOptions<GetOperationOptions>> options = SelectorOptions.createCollection(
                    GetOperationOptions.createExecutionPhase().resolveNames(true));

            // key: oid, value: Org/Role/Archetype etc.
            Map<String, AbstractRoleType> cache = modelService.searchObjects(AbstractRoleType.class, query, options, task, parentResult)
                    .stream()
                    .collect(Collectors.toMap(a -> a.getOid(), a -> a.asObjectable()));

            Set<String> directAssignment = new HashSet<>();

            List<AssignmentMessage> assignmentMessages = userType.getAssignment().stream()
                    // The user might not have permission to get the target. So filter them.
                    .filter(x -> cache.containsKey(x.getTargetRef().getOid()))
                    .map(x -> {
                        AbstractRoleType o = cache.get(x.getTargetRef().getOid());

                        ObjectReferenceType orgRef = x.getOrgRef();
                        AbstractRoleType resolvedOrgRef = null;
                        if (orgRef != null) {
                            resolvedOrgRef = cache.get(orgRef.getOid());
                        }

                        QName type = x.getTargetRef().getType();
                        QName relation = x.getTargetRef().getRelation();

                        if (request.getIncludeIndirect()) {
                            directAssignment.add(x.getTargetRef().getOid() + "#" + relation.toString());
                        }
                        return BuilderWrapper.wrap(AssignmentMessage.newBuilder())
                                .nullSafe(toReferenceMessage(orgRef, resolvedOrgRef), (b, v) -> b.setOrgRef(v))
                                .nullSafe(x.getSubtype(), (b, v) -> b.addAllSubtype(v))
                                .unwrap()
                                .setTargetRef(
                                        BuilderWrapper.wrap(ReferenceMessage.newBuilder())
                                                .nullSafe(o.getOid(), (b, v) -> b.setOid(v))
                                                .nullSafe(toPolyStringMessage(o.getName()), (b, v) -> b.setName(v))
                                                .nullSafe(toStringMessage(o.getDescription()), (b, v) -> b.setDescription(v))
                                                .nullSafe(toPolyStringMessage(o.getDisplayName()), (b, v) -> b.setDisplayName(v))
                                                .nullSafe(toReferenceMessageList(o.getArchetypeRef(), cache), (b, v) -> b.addAllArchetypeRef(v))
                                                .nullSafe(o.getSubtype(), (b, v) -> b.addAllSubtype(v))
                                                .unwrap()
                                                .setType(
                                                        QNameMessage.newBuilder()
                                                                .setNamespaceURI(type.getNamespaceURI())
                                                                .setLocalPart(type.getLocalPart())
                                                                .setPrefix(type.getPrefix())
                                                )
                                                .setRelation(
                                                        QNameMessage.newBuilder()
                                                                .setNamespaceURI(relation.getNamespaceURI())
                                                                .setLocalPart(relation.getLocalPart())
                                                                .setPrefix(relation.getPrefix())
                                                )
                                                .build()
                                ).build();
                    })
                    .collect(Collectors.toList());

            if (request.getIncludeIndirect()) {
                List<AssignmentMessage> indirect = userType.getRoleMembershipRef().stream()
                        .filter(x -> cache.containsKey(x.getOid()))
                        .filter(x -> !directAssignment.contains(x.getOid() + "#" + x.getRelation().toString()))
                        .map(x -> {
                            AbstractRoleType o = cache.get(x.getOid());
                            QName type = x.getType();
                            QName relation = x.getRelation();

                            return BuilderWrapper.wrap(AssignmentMessage.newBuilder())
                                    .unwrap()
                                    .setIndirect(true)
                                    .setTargetRef(
                                            BuilderWrapper.wrap(ReferenceMessage.newBuilder())
                                                    .nullSafe(o.getOid(), (b, v) -> b.setOid(v))
                                                    .nullSafe(toPolyStringMessage(o.getName()), (b, v) -> b.setName(v))
                                                    .nullSafe(toStringMessage(o.getDescription()), (b, v) -> b.setDescription(v))
                                                    .nullSafe(toPolyStringMessage(o.getDisplayName()), (b, v) -> b.setDisplayName(v))
                                                    .nullSafe(toReferenceMessageList(o.getArchetypeRef(), cache), (b, v) -> b.addAllArchetypeRef(v))
                                                    .nullSafe(o.getSubtype(), (b, v) -> b.addAllSubtype(v))
                                                    .unwrap()
                                                    .setType(
                                                            QNameMessage.newBuilder()
                                                                    .setNamespaceURI(type.getNamespaceURI())
                                                                    .setLocalPart(type.getLocalPart())
                                                                    .setPrefix(type.getPrefix())
                                                    )
                                                    .setRelation(
                                                            QNameMessage.newBuilder()
                                                                    .setNamespaceURI(relation.getNamespaceURI())
                                                                    .setLocalPart(relation.getLocalPart())
                                                                    .setPrefix(relation.getPrefix())
                                                    )
                                                    .build()
                                    ).build();
                        })
                        .collect(Collectors.toList());
                assignmentMessages.addAll(indirect);
            }

            parentResult.computeStatus();
            return assignmentMessages;
        });

        GetSelfAssignmentResponse res = GetSelfAssignmentResponse.newBuilder()
                .addAllAssignment(assignments)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End getSelfAssignment");
    }

    @Override
    public void modifyProfile(ModifyProfileRequest request, StreamObserver<ModifyProfileResponse> responseObserver) {
        LOGGER.debug("Start modifyProfile");

        runTask(ctx -> {
            Task task = ctx.task;
            OperationResult parentResult = task.getResult().createSubresult(OPERATION_MODIFY_PROFILE);

            UserType loggedInUser = ctx.principal.getUser();
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            executeChanges(loggedInUser.getOid(), request.getModificationsList(), modelExecuteOptions, task, parentResult);

            parentResult.computeStatus();
            return null;
        });

        ModifyProfileResponse res = ModifyProfileResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateProfile");
    }

    private void executeChanges(String oid, List<UserItemDeltaMessage> userModifications,
                                ModelExecuteOptions modelExecuteOptions,
                                Task task, OperationResult result) throws SchemaException, CommunicationException,
            ObjectNotFoundException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException {

        OperationResult updateResult = result.createSubresult(OPERATION_EXECUTE_USER_UPDATE);

        PrismContainerDefinition<UserType> definition = prismContext.getSchemaRegistry()
                .findContainerDefinitionByCompileTimeClass(UserType.class);

        S_ItemEntry i = prismContext.deltaFor(UserType.class);

        // https://wiki.evolveum.com/display/midPoint/Using+Prism+Deltas
        for (UserItemDeltaMessage m : userModifications) {
            ItemPath path;
            if (m.hasItemPath()) {
                path = TypeConverter.toItemPathValue(m.getItemPath());

            } else if (m.getPathWrapperCase() == UserItemDeltaMessage.PathWrapperCase.PATH) {
                path = TypeConverter.toItemPath(m.getPath());

            } else if (m.getPathWrapperCase() == UserItemDeltaMessage.PathWrapperCase.USER_TYPE_PATH) {
                path = TypeConverter.toItemName(m.getUserTypePath());

            } else {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_path")
                        .asRuntimeException();
                throw exception;
            }

            ItemDefinition itemDef = definition.findItemDefinition(path);
            if (itemDef == null) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_path")
                        .asRuntimeException();
                throw exception;
            }

            Class itemClass = itemDef.getTypeClass();
            if (itemClass == null) {
                QName typeName = itemDef.getTypeName();
                if (typeName.equals(ProtectedStringType.COMPLEX_TYPE)) {
                    itemClass = ProtectedStringType.class;
                }
            }

            S_ValuesEntry v = i.item(path);

            S_ItemEntry entry = null;

            // Plain string
            // TODO Remove this API
            if (!m.getValuesToAddList().isEmpty()) {
                S_MaybeDelete av = v.addRealValues(TypeConverter.toRealValue(m.getValuesToAddList(), itemClass));

                if (!m.getValuesToDeleteList().isEmpty()) {
                    entry = av.deleteRealValues(TypeConverter.toRealValue(m.getValuesToDeleteList(), itemClass));
                } else {
                    entry = av;
                }
            } else if (!m.getValuesToReplaceList().isEmpty()) {
                entry = v.replaceRealValues(TypeConverter.toRealValue(m.getValuesToReplaceList(), itemClass));

            } else if (!m.getValuesToDeleteList().isEmpty()) {
                entry = v.deleteRealValues(TypeConverter.toRealValue(m.getValuesToDeleteList(), itemClass));
            }

            // PrismValue
            if (!m.getPrismValuesToAddList().isEmpty()) {
                S_MaybeDelete av = v.add(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToAddList()));

                if (!m.getPrismValuesToDeleteList().isEmpty()) {
                    entry = av.delete(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToDeleteList()));
                } else {
                    entry = av;
                }
            } else if (!m.getPrismValuesToReplaceList().isEmpty()) {
                entry = v.replace(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToReplaceList()));

            } else if (!m.getPrismValuesToDeleteList().isEmpty()) {
                entry = v.delete(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToDeleteList()));
            }

            if (entry == null) {
                LOGGER.warn("Invalid argument. No values for modification.");
                throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
            }
            i = entry;
        }
        Collection<? extends ItemDelta<?, ?>> modifications = i.asItemDeltas();

        modelCrudService.modifyObject(UserType.class, oid, modifications, modelExecuteOptions, task, updateResult);

        updateResult.computeStatus();
    }

    private void executeChanges(Class clazz, String oid, List<ItemDeltaMessage> modifications,
                                ModelExecuteOptions modelExecuteOptions,
                                Task task, OperationResult result) throws SchemaException, CommunicationException,
            ObjectNotFoundException, ObjectAlreadyExistsException, PolicyViolationException, SecurityViolationException,
            ConfigurationException, ExpressionEvaluationException {

        OperationResult updateResult = result.createSubresult(OPERATION_EXECUTE_OBJECT_UPDATE);

        PrismContainerDefinition<ObjectType> definition = prismContext.getSchemaRegistry()
                .findContainerDefinitionByCompileTimeClass(clazz);

        S_ItemEntry i = prismContext.deltaFor(clazz);

        // https://wiki.evolveum.com/display/midPoint/Using+Prism+Deltas
        for (ItemDeltaMessage m : modifications) {
            ItemPath path;
            if (m.hasItemPath()) {
                path = TypeConverter.toItemPathValue(m.getItemPath());

            } else if (m.getPathWrapperCase() == ItemDeltaMessage.PathWrapperCase.PATH) {
                path = TypeConverter.toItemPath(m.getPath());

            } else {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_path")
                        .asRuntimeException();
                throw exception;
            }

            ItemDefinition itemDef = definition.findItemDefinition(path);
            if (itemDef == null) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_path")
                        .asRuntimeException();
                throw exception;
            }

            S_ValuesEntry v = i.item(path);

            S_ItemEntry entry = null;

            // PrismValue
            if (!m.getPrismValuesToAddList().isEmpty()) {
                S_MaybeDelete av = v.add(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToAddList()));

                if (!m.getPrismValuesToDeleteList().isEmpty()) {
                    entry = av.delete(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToDeleteList()));
                } else {
                    entry = av;
                }
            } else if (!m.getPrismValuesToReplaceList().isEmpty()) {
                entry = v.replace(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToReplaceList()));

            } else if (!m.getPrismValuesToDeleteList().isEmpty()) {
                entry = v.delete(TypeConverter.toPrismValueList(prismContext, itemDef, m.getPrismValuesToDeleteList()));
            }

            if (entry == null) {
                LOGGER.warn("Invalid argument. No values for modification.");
                throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
            }
            i = entry;
        }
        Collection<? extends ItemDelta<?, ?>> mods = i.asItemDeltas();

        modelCrudService.modifyObject(definition.getCompileTimeClass(), oid, mods, modelExecuteOptions, task, updateResult);

        updateResult.computeStatus();
    }

    @Override
    public void updateCredential(UpdateCredentialRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        LOGGER.debug("Start updateCredential");

        runTask(ctx -> {
            updateCredential(ctx, request.getOld(), request.getNew(), true);
            return null;
        });

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateCredential");
    }

    @Override
    public void forceUpdateCredential(ForceUpdateCredentialRequest request, StreamObserver<UpdateCredentialResponse> responseObserver) {
        LOGGER.debug("Start forceUpdateCredential");

        runTask(ctx -> {
            updateCredential(ctx, null, request.getNew(), false);
            return null;
        });

        UpdateCredentialResponse res = UpdateCredentialResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End forceUpdateCredential");
    }

    private void resolveReference(ObjectDelta delta, OperationResult result) {
        try {
            ModelImplUtils.resolveReferences(delta, repositoryService, false, false, EvaluationTimeType.IMPORT, true, prismContext, result);
        } catch (SystemException e) {
            StatusRuntimeException exception = Status.INVALID_ARGUMENT
                    .withDescription("invalid_reference")
                    .asRuntimeException();
            throw exception;
        }
    }

    @Override
    public void requestAssignments(RequestAssignmentsRequest request, StreamObserver<RequestAssignmentsResponse> responseObserver) {
        LOGGER.debug("Start requestAssignments");

        String result = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_REQUEST_ASSIGNMENTS);

            List<PrismContainerValue> assignmentTypes = request.getAssignmentsList().stream()
                    .map(x -> TypeConverter.toAssignmentTypeValue(prismContext, x).asPrismContainerValue())
                    .collect(Collectors.toList());

            String taskOid = null;

            // Self assignment
            if (request.getOidsList().size() < 2) {
                String targetOid;
                if (request.getOidsList().isEmpty()) {
                    targetOid = loggedInUser.getOid();
                } else {
                    targetOid = request.getOids(0);
                }
                ObjectDelta delta = prismContext.deltaFactory().object()
                        .createModificationAddContainer(UserType.class, targetOid,
                                FocusType.F_ASSIGNMENT, assignmentTypes.toArray(new PrismContainerValue[assignmentTypes.size()]));
                resolveReference(delta, parentResult);

                ModelExecuteOptions options = createOptions(request.getComment());
                options.setInitialPartialProcessing(new PartialProcessingOptionsType().inbound(SKIP).projection(SKIP)); // TODO make this configurable?
                modelCrudService.modifyObject(UserType.class, delta.getOid(), delta.getModifications(), options, task, parentResult);
            } else {
                // Assign to specified target users

                // Do preview changes due to get the result of the policy check
                LocalizableMessageListBuilder builder = new LocalizableMessageListBuilder();
                for (String oid : request.getOidsList()) {
                    ObjectDelta previewDelta = prismContext.deltaFactory().object()
                            .createModificationAddContainer(UserType.class, oid,
                                    FocusType.F_ASSIGNMENT,
                                    assignmentTypes.stream()
                                            .map(x -> x.clone())
                                            .toArray(PrismContainerValue[]::new));
                    resolveReference(previewDelta, parentResult);

                    Collection<ObjectDelta<? extends ObjectType>> previewDeltas = new ArrayList<>();
                    previewDeltas.add(previewDelta);

                    ModelExecuteOptions options = createOptions(request.getComment());
                    options.getOrCreatePartialProcessing().setApprovals(PartialProcessingTypeType.PROCESS);
                    ModelContext<ObjectType> previewResult = modelInteraction.previewChanges(previewDeltas, options, task, parentResult);

                    // Policy error check
                    PolicyRuleEnforcerHookPreviewOutputType enforcements = previewResult.getHookPreviewResult(PolicyRuleEnforcerHookPreviewOutputType.class);
                    List<EvaluatedPolicyRuleType> rule = enforcements.getRule();

                    for (EvaluatedPolicyRuleType r : rule) {
                        for (EvaluatedPolicyRuleTriggerType g : r.getTrigger()) {
                            LocalizableMessage message = LocalizationUtil.toLocalizableMessage(g.getMessage());
                            builder.addMessage(message);
                        }
                    }
                }
                LocalizableMessage allMessages = builder.separator(LocalizableMessageList.SEMICOLON)
                        .buildOptimized();
                if (!allMessages.isEmpty()) {
                    throw new PolicyViolationException(allMessages);
                }

                // Now, submit a task to change all target user's assignments
                ObjectDelta delta = prismContext.deltaFactory().object()
                        .createModificationAddContainer(UserType.class, "fakeOid",
                                FocusType.F_ASSIGNMENT,
                                assignmentTypes.stream()
                                        .map(x -> x.clone())
                                        .toArray(PrismContainerValue[]::new));
                resolveReference(delta, parentResult);

                QueryFactory queryFactory = prismContext.queryFactory();
                ObjectQuery query = queryFactory.createQuery(queryFactory.createInOid(request.getOidsList()));

                // TODO Need to localize taskName?
                TaskType reqTask = createSingleRecurrenceTask(loggedInUser, "Request assignments - " + UUID.randomUUID().toString(),
                        UserType.COMPLEX_TYPE,
                        query, delta, createOptions(request.getComment()), TaskCategory.EXECUTE_CHANGES);
                taskOid = runTask(reqTask, task, parentResult);
            }

            parentResult.computeStatus();
            return taskOid;
        });

        RequestAssignmentsResponse.Builder builder = RequestAssignmentsResponse.newBuilder();
        if (result != null) {
            builder.setTaskOid(result);
        }

        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();

        LOGGER.debug("End requestAssignments");
    }

    protected TaskType createSingleRecurrenceTask(UserType owner, String taskName, QName applicableType, ObjectQuery query,
                                                  ObjectDelta delta, ModelExecuteOptions options, String category) throws SchemaException {
        TaskType task = new TaskType(prismContext);

        ObjectReferenceType ownerRef = new ObjectReferenceType();
        ownerRef.setOid(owner.getOid());
        ownerRef.setType(owner.COMPLEX_TYPE);
        task.setOwnerRef(ownerRef);

        task.setBinding(TaskBindingType.LOOSE);
        task.setCategory(category);
        task.setExecutionStatus(TaskExecutionStatusType.RUNNABLE);
        task.setRecurrence(TaskRecurrenceType.SINGLE);
        task.setThreadStopAction(ThreadStopActionType.RESTART);
        task.setHandlerUri(taskService.getHandlerUriForCategory(category));
        ScheduleType schedule = new ScheduleType();
        schedule.setMisfireAction(MisfireActionType.EXECUTE_IMMEDIATELY);
        task.setSchedule(schedule);

        task.setName(PolyStringType.fromOrig(taskName));

        PrismObject<TaskType> prismTask = task.asPrismObject();
        QueryType queryType = prismContext.getQueryConverter().createQueryType(query);
        prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_QUERY).addRealValue(queryType);

        if (applicableType != null) {
            prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_TYPE).setRealValue(applicableType);
        }

        if (delta != null) {
            ObjectDeltaType deltaBean = DeltaConvertor.toObjectDeltaType(delta);
            prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_OBJECT_DELTA).setRealValue(deltaBean);
        }

        if (options != null) {
            prismTask.findOrCreateProperty(SchemaConstants.PATH_MODEL_EXTENSION_EXECUTE_OPTIONS)
                    .setRealValue(options.toModelExecutionOptionsType());
        }
        return task;
    }

    protected String runTask(TaskType taskToRun, Task operationalTask, OperationResult parentResult) {
        try {
            ObjectDelta<TaskType> delta = DeltaFactory.Object.createAddDelta(taskToRun.asPrismObject());
            prismContext.adopt(delta);

            Collection<ObjectDeltaOperation<? extends ObjectType>> results = modelService.executeChanges(WebComponentUtil.createDeltaCollection(delta), null,
                    operationalTask, parentResult);

            ObjectDeltaOperation<? extends ObjectType> result = results.iterator().next();
            String taskOid = result.getObjectDelta().getOid();

            parentResult.recordInProgress();
            parentResult.setBackgroundTaskOid(taskOid);
            return taskOid;

        } catch (CommonException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't run task " + e.getMessage(), e);
            StatusRuntimeException exception = Status.INTERNAL
                    .withDescription("Couldn't run task " + e.getMessage())
                    .asRuntimeException();
            throw exception;
        }
    }

    private ModelExecuteOptions createOptions(String comment) {
        OperationBusinessContextType businessContextType;
        if (comment != null && !comment.isEmpty()) {
            businessContextType = new OperationBusinessContextType();
            businessContextType.setComment(comment);
        } else {
            businessContextType = null;
        }
        // ModelExecuteOptions options = ExecuteChangeOptionsDto.createFromSystemConfiguration().createOptions();
        ModelExecuteOptions options = ModelExecuteOptions.fromRestOptions(Collections.EMPTY_LIST);
        if (options == null) {
            options = new ModelExecuteOptions();
        }
        options.setRequestBusinessContext(businessContextType);
        return options;
    }

    @Override
    public void addUser(AddUserRequest request, StreamObserver<AddUserResponse> responseObserver) {
        LOGGER.debug("Start addUser");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_USER);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            UserTypeMessage message = request.getProfile();
            PrismObject<UserType> user = toPrismObject(prismContext, repositoryService, message);

            // To handle error to resolve reference, call resolveReferences here.
            try {
                ModelImplUtils.resolveReferences(user, repositoryService, false, false, EvaluationTimeType.IMPORT, true, prismContext, parentResult);
            } catch (SystemException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_reference")
                        .asRuntimeException();
                throw exception;
            }

            String oid = modelCrudService.addObject(user, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            parentResult.computeStatus();
            return oid;
        });

        AddUserResponse.Builder builder = AddUserResponse.newBuilder();
        // If the create process is suspended by workflow, oid is null
        if (resultOid != null) {
            builder.setOid(resultOid);
        }
        AddUserResponse res = builder.build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addUser");
    }

    @Override
    public void modifyUser(ModifyUserRequest request, StreamObserver<ModifyUserResponse> responseObserver) {
        LOGGER.debug("Start modifyUser");

        runTask(ctx -> {
            Task task = ctx.task;
            OperationResult parentResult = task.getResult().createSubresult(OPERATION_MODIFY_USER);

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            executeChanges(oid, request.getModificationsList(), modelExecuteOptions, task, parentResult);

            parentResult.computeStatus();
            return null;
        });

        ModifyUserResponse res = ModifyUserResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End modifyUser");
    }

    protected void updateCredential(MidPointTaskContext ctx, String oldCred, String newCred, boolean validate) throws SchemaException, SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException, ObjectNotFoundException, EncryptionException, PolicyViolationException, ObjectAlreadyExistsException {
        Task task = ctx.task;
        UserType user = ctx.principal.getUser();

        ProtectedStringType oldPassword = null;
        if (validate) {
            OperationResult checkPasswordResult = task.getResult().createSubresult(OPERATION_EXECUTE_CREDENTIAL_CHECK);
            try {
                oldPassword = new ProtectedStringType();
                oldPassword.setClearValue(oldCred);

                boolean isCorrectPassword = modelInteraction.checkPassword(user.getOid(), oldPassword,
                        task, checkPasswordResult);
                if (!isCorrectPassword) {
                    StatusRuntimeException exception = Status.INVALID_ARGUMENT
                            .withDescription("invalid_credential")
                            .asRuntimeException();
                    throw exception;
                }
                checkPasswordResult.computeStatus();
            } finally {
                checkPasswordResult.computeStatusIfUnknown();
            }
        }

        OperationResult updateResult = task.getResult().createSubresult(OPERATION_EXECUTE_CREDENTIAL_UPDATE);
        try {
            ProtectedStringType newProtectedCred = protector.encryptString(newCred);
            final ItemPath valuePath = ItemPath.create(SchemaConstantsGenerated.C_CREDENTIALS,
                    CredentialsType.F_PASSWORD, PasswordType.F_VALUE);
            SchemaRegistry registry = prismContext.getSchemaRegistry();
            Collection<ObjectDelta<? extends ObjectType>> deltas = new ArrayList<>();

            PrismObjectDefinition objDef = registry.findObjectDefinitionByCompileTimeClass(UserType.class);

            PropertyDelta<ProtectedStringType> delta = prismContext.deltaFactory().property()
                    .createModificationReplaceProperty(valuePath, objDef, newProtectedCred);
            if (oldPassword != null) {
                delta.addEstimatedOldValue(prismContext.itemFactory().createPropertyValue(oldPassword));
            }

            deltas.add(prismContext.deltaFactory().object().createModifyDelta(user.getOid(), delta, UserType.class));

            modelService.executeChanges(deltas, null, task, updateResult);

            updateResult.computeStatus();
        } catch (PolicyViolationException e) {
            LoggingUtils.logExceptionAsWarning(LOGGER, "Couldn't save password changes because of policy violation: {}", e, e.getMessage());
            throw e;
        } catch (EncryptionException | ObjectAlreadyExistsException e) {
            LoggingUtils.logUnexpectedException(LOGGER, "Couldn't save password changes", e);
            throw e;
        } finally {
            updateResult.computeStatusIfUnknown();
        }
    }

    private <T extends ObjectType> SearchResultList<PrismObject<T>> search(QueryMessage queryMessage, Class<T> clazz,
                                                                           Collection<SelectorOptions<GetOperationOptions>> searchOptions) {
        SearchResultList<PrismObject<T>> result = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SEARCH_OBJECTS);

            ObjectQuery query = TypeConverter.toObjectQuery(prismContext, clazz, queryMessage);

            Integer total = modelService.countObjects(clazz, query, searchOptions,
                    task, parentResult);

            SearchResultList<PrismObject<T>> foundObjects = modelService.searchObjects(clazz, query, searchOptions,
                    task, parentResult);

            SearchResultMetadata metadata = foundObjects.getMetadata();
            if (metadata == null) {
                metadata = new SearchResultMetadata();
                foundObjects.setMetadata(metadata);
            }
            foundObjects.getMetadata().setApproxNumberOfAllResults(total);

            parentResult.computeStatus();
            return foundObjects;
        });
        return result;
    }

    @Override
    public void searchUsers(SearchRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        LOGGER.debug("Start searchUsers");

        List<String> options = request.getOptionsList();
        List<String> include = request.getIncludeList();
        List<String> exclude = request.getExcludeList();
        List<String> resolveNames = request.getResolveNamesList();

        Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

        SearchResultList<PrismObject<UserType>> result = search(request.getQuery(), UserType.class, searchOptions);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<UserTypeMessage> users = result.stream()
                .map(x -> TypeConverter.toUserTypeMessage(x.asObjectable(), searchOptions))
                .collect(Collectors.toList());

        SearchUsersResponse res = SearchUsersResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(users)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchUsers");
    }

    @Override
    public void addRole(AddRoleRequest request, StreamObserver<AddObjectResponse> responseObserver) {
        LOGGER.debug("Start addRole");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_ROLE);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            RoleTypeMessage message = request.getObject();
            PrismObject<RoleType> object = toPrismObject(prismContext, repositoryService, message);

            // To handle error to resolve reference, call resolveReferences here.
            try {
                ModelImplUtils.resolveReferences(object, repositoryService, false, false, EvaluationTimeType.IMPORT, true, prismContext, parentResult);
            } catch (SystemException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_reference")
                        .asRuntimeException();
                throw exception;
            }

            String oid = modelCrudService.addObject(object, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            parentResult.computeStatus();
            return oid;
        });

        AddObjectResponse.Builder builder = AddObjectResponse.newBuilder();
        // If the create process is suspended by workflow, oid is null
        if (resultOid != null) {
            builder.setOid(resultOid);
        }
        AddObjectResponse res = builder.build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addRole");
    }

    @Override
    public void getRole(GetRoleRequest request, StreamObserver<GetRoleResponse> responseObserver) {
        RoleTypeMessage found = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_GET_ROLE);

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            PrismObject<RoleType> object = modelCrudService.getObject(RoleType.class, oid, getOptions, task, parentResult);

            parentResult.computeStatus();

            return toRoleTypeMessage(object.asObjectable(), getOptions);
        });

        responseObserver.onNext(GetRoleResponse.newBuilder()
                .setResult(found)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void addOrg(AddOrgRequest request, StreamObserver<AddObjectResponse> responseObserver) {
        LOGGER.debug("Start addOrg");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_ORG);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            OrgTypeMessage message = request.getObject();
            PrismObject<OrgType> object = toPrismObject(prismContext, repositoryService, message);

            // To handle error to resolve reference, call resolveReferences here.
            try {
                ModelImplUtils.resolveReferences(object, repositoryService, false, false, EvaluationTimeType.IMPORT, true, prismContext, parentResult);
            } catch (SystemException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_reference")
                        .asRuntimeException();
                throw exception;
            }

            String oid = modelCrudService.addObject(object, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            parentResult.computeStatus();
            return oid;
        });

        AddObjectResponse.Builder builder = AddObjectResponse.newBuilder();
        // If the create process is suspended by workflow, oid is null
        if (resultOid != null) {
            builder.setOid(resultOid);
        }
        AddObjectResponse res = builder.build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addOrg");
    }

    @Override
    public void getOrg(GetOrgRequest request, StreamObserver<GetOrgResponse> responseObserver) {
        OrgTypeMessage found = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_GET_ORG);

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            PrismObject<OrgType> object = modelCrudService.getObject(OrgType.class, oid, getOptions, task, parentResult);

            parentResult.computeStatus();

            return toOrgTypeMessage(object.asObjectable(), getOptions);
        });

        responseObserver.onNext(GetOrgResponse.newBuilder()
                .setResult(found)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void addService(AddServiceRequest request, StreamObserver<AddObjectResponse> responseObserver) {
        LOGGER.debug("Start addService");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_SERVICE);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            ServiceTypeMessage message = request.getObject();
            PrismObject<ServiceType> object = toPrismObject(prismContext, repositoryService, message);

            // To handle error to resolve reference, call resolveReferences here.
            try {
                ModelImplUtils.resolveReferences(object, repositoryService, false, false, EvaluationTimeType.IMPORT, true, prismContext, parentResult);
            } catch (SystemException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_reference")
                        .asRuntimeException();
                throw exception;
            }

            String oid = modelCrudService.addObject(object, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            parentResult.computeStatus();
            return oid;
        });

        AddObjectResponse.Builder builder = AddObjectResponse.newBuilder();
        // If the create process is suspended by workflow, oid is null
        if (resultOid != null) {
            builder.setOid(resultOid);
        }
        AddObjectResponse res = builder.build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addService");
    }

    @Override
    public void getService(GetServiceRequest request, StreamObserver<GetServiceResponse> responseObserver) {
        ServiceTypeMessage found = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_GET_SERVICE);

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            PrismObject<ServiceType> object = modelCrudService.getObject(ServiceType.class, oid, getOptions, task, parentResult);

            parentResult.computeStatus();

            return toServiceTypeMessage(object.asObjectable(), getOptions);
        });

        responseObserver.onNext(GetServiceResponse.newBuilder()
                .setResult(found)
                .build());
        responseObserver.onCompleted();
    }

    @Override
    public void searchRoles(SearchRequest request, StreamObserver<SearchRolesResponse> responseObserver) {
        LOGGER.debug("Start searchRols");

        List<String> options = request.getOptionsList();
        List<String> include = request.getIncludeList();
        List<String> exclude = request.getExcludeList();
        List<String> resolveNames = request.getResolveNamesList();

        Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

        SearchResultList<PrismObject<RoleType>> result = search(request.getQuery(), RoleType.class, searchOptions);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<RoleTypeMessage> roles = result.stream()
                .map(x -> TypeConverter.toRoleTypeMessage(x.asObjectable(), searchOptions))
                .collect(Collectors.toList());

        SearchRolesResponse res = SearchRolesResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(roles)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchRols");
    }

    @Override
    public void searchOrgs(SearchRequest request, StreamObserver<SearchOrgsResponse> responseObserver) {
        LOGGER.debug("Start searchOrgs");

        List<String> options = request.getOptionsList();
        List<String> include = request.getIncludeList();
        List<String> exclude = request.getExcludeList();
        List<String> resolveNames = request.getResolveNamesList();

        Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

        SearchResultList<PrismObject<OrgType>> result = search(request.getQuery(), OrgType.class, searchOptions);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<OrgTypeMessage> orgs = result.stream()
                .map(x -> TypeConverter.toOrgTypeMessage(x.asObjectable(), searchOptions))
                .collect(Collectors.toList());

        SearchOrgsResponse res = SearchOrgsResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(orgs)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchOrgs");
    }

    @Override
    public void searchServices(SearchRequest request, StreamObserver<SearchServicesResponse> responseObserver) {
        LOGGER.debug("Start searchServices");

        List<String> options = request.getOptionsList();
        List<String> include = request.getIncludeList();
        List<String> exclude = request.getExcludeList();
        List<String> resolveNames = request.getResolveNamesList();

        Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

        SearchResultList<PrismObject<ServiceType>> result = search(request.getQuery(), ServiceType.class, searchOptions);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<ServiceTypeMessage> services = result.stream()
                .map(x -> TypeConverter.toServiceTypeMessage(x.asObjectable(), searchOptions))
                .collect(Collectors.toList());

        SearchServicesResponse res = SearchServicesResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(services)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchServices");
    }

    @Override
    public void searchObjects(SearchObjectsRequest request, StreamObserver<SearchObjectsResponse> responseObserver) {
        LOGGER.debug("Start searchObjects");

        List<String> options = request.getOptionsList();
        List<String> include = request.getIncludeList();
        List<String> exclude = request.getExcludeList();
        List<String> resolveNames = request.getResolveNamesList();

        Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

        Class<? extends ObjectType> clazz;
        if (request.hasType()) {
            QName qname = toQNameValue(request.getType());
            clazz = ObjectTypes.getObjectTypeClass(qname);
        } else {
            QName qname = toQNameValue(request.getObjectType());
            clazz = ObjectTypes.getObjectTypeClass(qname);
        }

        SearchResultList<? extends PrismObject<? extends ObjectType>> result = search(request.getQuery(), clazz, searchOptions);

        // Convert to ItemMessages
        final boolean hasInclude = hasIncludeInSearchOptions(searchOptions);
        final Collection<SelectorOptions<GetOperationOptions>> finalSearchOptions = searchOptions;

        List<PrismObjectMessage> itemMessage = result.stream()
                .map(x -> {
                    try {
                        return toPrismObjectMessage(x.getDefinition(), x, finalSearchOptions, hasInclude);
                    } catch (SchemaException e) {
                        LOGGER.error("Failed to convert the object", e);
                        StatusRuntimeException exception = Status.INVALID_ARGUMENT
                                .withDescription("invalid_schema")
                                .asRuntimeException();
                        throw exception;
                    }
                })
                .collect(Collectors.toList());

        // Write Response
        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        SearchObjectsResponse res = SearchObjectsResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(itemMessage)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchObjects");
    }

    @Override
    public void searchObjectsAsStream(SearchObjectsRequest request, StreamObserver<SearchObjectsResponse> responseObserver) {
        runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SEARCH_OBJECTS);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

            Class<? extends ObjectType> clazz;
            if (request.hasType()) {
                QName qname = toQNameValue(request.getType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            } else {
                QName qname = toQNameValue(request.getObjectType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            }

            ObjectQuery query = TypeConverter.toObjectQuery(prismContext, clazz, request.getQuery());

            SearchResultMetadata metadata = modelService.searchObjectsIterative(clazz, query,
                    (object, pr) -> {
                        try {
                            PrismObjectMessage prismObjectMessage = toPrismObjectMessage(object.getDefinition(), object);

                            SearchObjectsResponse response = SearchObjectsResponse.newBuilder()
                                    // Unknown all count yet
                                    .setNumberOfAllResults(-1)
                                    .addResults(prismObjectMessage)
                                    .build();
                            responseObserver.onNext(response);

                        } catch (SchemaException e) {
                            LOGGER.error("Failed to convert the object", e);
                            responseObserver.onError(e);
                        }
                        return true;
                    },
                    searchOptions,
                    task, parentResult);

            parentResult.computeStatus();
            return null;
        });

        responseObserver.onCompleted();
    }

    @Override
    public void searchAssignments(SearchAssignmentsRequest request, StreamObserver<SearchObjectsResponse> responseObserver) {
        LOGGER.debug("Start searchAssignments");

        runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SEARCH_ASSIGNMENTS);

            String oid = resolveOid(AssignmentHolderType.class, request.getOid(), request.getName(), task, parentResult);

            PrismObject<AssignmentHolderType> object = modelCrudService.getObject(AssignmentHolderType.class, oid, null, task, parentResult);
            AssignmentHolderType objectType = object.asObjectable();

            Set<String> directOids = objectType.getAssignment().stream()
                    .map(x -> x.getTargetRef().getOid())
                    .collect(Collectors.toSet());

            Set<String> indirectOids = Collections.emptySet();
            if (request.getIncludeIndirect()) {
                indirectOids = objectType.getRoleMembershipRef().stream()
                        .map(x -> x.getOid())
                        .collect(Collectors.toSet());
            }

            Set<String> oids = new HashSet<>();
            oids.addAll(directOids);
            oids.addAll(indirectOids);

            ObjectFilterMessage oidFilter = ObjectFilterMessage.newBuilder()
                    .setInOid(FilterInOidMessage.newBuilder().addAllValue(oids)).build();

            QueryMessage queryMessage = QueryMessage.newBuilder()
                    .setFilter(ObjectFilterMessage.newBuilder()
                            .setInOid(FilterInOidMessage.newBuilder()
                                    .addAllValue(oids)))
                    .build();
            Class<? extends ObjectType> clazz = null;
            Collection<SelectorOptions<GetOperationOptions>> searchOptions = null;

            SearchObjectsRequest search = request.getSearch();
            if (request.hasSearch()) {
                List<String> options = search.getOptionsList();
                List<String> include = search.getIncludeList();
                List<String> exclude = search.getExcludeList();
                List<String> resolveNames = search.getResolveNamesList();

                searchOptions = GetOperationOptions.fromRestOptions(options, include,
                        exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

                if (search.hasType()) {
                    QName qname = toQNameValue(search.getType());
                    clazz = ObjectTypes.getObjectTypeClass(qname);
                } else {
                    QName qname = toQNameValue(search.getObjectType());
                    clazz = ObjectTypes.getObjectTypeClass(qname);
                }

                if (search.hasQuery()) {
                    ObjectFilterMessage.Builder filter = ObjectFilterMessage.newBuilder()
                            .setAnd(AndFilterMessage.newBuilder()
                                    .addConditions(oidFilter)
                                    .addConditions(search.getQuery().getFilter())
                            );
                    queryMessage = search.getQuery().toBuilder().setFilter(filter).build();
                }
            }

            if (queryMessage == null) {
                // No filter case
                queryMessage = QueryMessage.newBuilder()
                        .setFilter(oidFilter)
                        .build();
            }

            // TODO sort by something?

            // Search
            SearchResultList<? extends PrismObject<? extends ObjectType>> result = search(queryMessage, clazz, searchOptions);

            // Convert to ItemMessages
            final boolean hasInclude = hasIncludeInSearchOptions(searchOptions);
            final Collection<SelectorOptions<GetOperationOptions>> finalSearchOptions = searchOptions;

            List<PrismObjectMessage> itemMessage = result.stream()
                    .map(x -> {
                        try {
                            return toPrismObjectMessage(x.getDefinition(), x, finalSearchOptions, hasInclude);
                        } catch (SchemaException e) {
                            LOGGER.error("Failed to convert the object", e);
                            StatusRuntimeException exception = Status.INVALID_ARGUMENT
                                    .withDescription("invalid_schema")
                                    .asRuntimeException();
                            throw exception;
                        }
                    })
                    .collect(Collectors.toList());

            // Write response
            Integer total = result.getMetadata().getApproxNumberOfAllResults();
            SearchObjectsResponse res = SearchObjectsResponse.newBuilder()
                    .setNumberOfAllResults(total)
                    .addAllResults(itemMessage)
                    .build();

            responseObserver.onNext(res);
            responseObserver.onCompleted();

            return null;
        });

        LOGGER.debug("End searchAssignments");
    }

    private boolean hasIncludeInSearchOptions(Collection<SelectorOptions<GetOperationOptions>> searchOptions) {
        if (searchOptions != null) {
            Optional<RetrieveOption> include = searchOptions.stream()
                    .map(o -> o.getOptions().getRetrieve())
                    .filter(r -> r == RetrieveOption.INCLUDE)
                    .findFirst();
            if (include.isPresent()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void getUser(GetUserRequest request, StreamObserver<GetUserResponse> responseObserver) {
        UserTypeMessage foundUser = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_GET_USER);

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            PrismObject<UserType> user = modelCrudService.getObject(UserType.class, oid, getOptions, task, parentResult);

            parentResult.computeStatus();

            return toUserTypeMessage(user.asObjectable(), getOptions);
        });

        responseObserver.onNext(GetUserResponse.newBuilder()
                .setResult(foundUser)
                .build());
        responseObserver.onCompleted();
    }

    private Collection<SelectorOptions<GetOperationOptions>> getDefaultGetOptionCollection() {
        return SelectorOptions.createCollection(GetOperationOptions.createExecutionPhase());
    }

    @Override
    public void addObject(AddObjectRequest request, StreamObserver<AddObjectResponse> responseObserver) {
        LOGGER.debug("Start addObject");

        String resultOid = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_ADD_USER);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            Class<? extends ObjectType> clazz;
            if (request.hasType()) {
                QName qname = toQNameValue(request.getType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            } else {
                QName qname = toQNameValue(request.getObjectType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            }
            PrismContainerMessage message = request.getObject();
            PrismObject object = toPrismObject(prismContext, clazz, message);

            // To handle error to resolve reference, call resolveReferences here.
            try {
                ModelImplUtils.resolveReferences(object, repositoryService, false,
                        false, EvaluationTimeType.IMPORT, true, prismContext, parentResult);
            } catch (SystemException e) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_reference")
                        .asRuntimeException();
                throw exception;
            }

            String oid = modelCrudService.addObject(object, modelExecuteOptions, task, parentResult);
            LOGGER.debug("returned oid :  {}", oid);

            parentResult.computeStatus();
            return oid;
        });

        AddObjectResponse.Builder builder = AddObjectResponse.newBuilder();
        // If the create process is suspended by workflow, oid is null
        if (resultOid != null) {
            builder.setOid(resultOid);
        }
        AddObjectResponse res = builder.build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addObject");
    }

    @Override
    public void modifyObject(ModifyObjectRequest request, StreamObserver<ModifyObjectResponse> responseObserver) {
        LOGGER.debug("Start modifyObject");

        runTask(ctx -> {
            Task task = ctx.task;
            OperationResult parentResult = task.getResult().createSubresult(OPERATION_MODIFY_OBJECT);

            Class<? extends ObjectType> clazz;
            if (request.hasType()) {
                QName qname = toQNameValue(request.getType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            } else {
                QName qname = toQNameValue(request.getObjectType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            }

            String oid = resolveOid(clazz, request.getOid(), request.getName(), task, parentResult);
            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(request.getOptionsList());

            executeChanges(clazz, oid, request.getModificationsList(), modelExecuteOptions, task, parentResult);

            parentResult.computeStatus();
            return null;
        });

        ModifyObjectResponse res = ModifyObjectResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End modifyObject");
    }

    @Override
    public void deleteObject(DeleteObjectRequest request, StreamObserver<DeleteObjectResponse> responseObserver) {
        runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_DELETE_OBJECT);

            Class<? extends ObjectType> clazz;
            if (request.hasType()) {
                QName qname = toQNameValue(request.getType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            } else {
                QName qname = toQNameValue(request.getObjectType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            }

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);

            List<String> options = request.getOptionsList();

            if (clazz.isAssignableFrom(TaskType.class)) {
                taskService.suspendAndDeleteTask(oid, WAIT_FOR_TASK_STOP, true, task, parentResult);
                parentResult.computeStatus();
                if (parentResult.isSuccess()) {
                    return null;
                }
                StatusRuntimeException exception = Status.INTERNAL
                        .withDescription(parentResult.getMessage())
                        .asRuntimeException();
                throw exception;
            }

            ModelExecuteOptions modelExecuteOptions = ModelExecuteOptions.fromRestOptions(options);

            try {
                modelCrudService.deleteObject(clazz, oid, modelExecuteOptions, task, parentResult);
            } catch (SchemaException e) {
                // TODO Add force delete mode for invalid schema data
//                repositoryService.deleteObject(clazz, oid, parentResult);
                throw e;
            }

            parentResult.computeStatus();
            return null;
        });

        responseObserver.onNext(DeleteObjectResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void recomputeObject(RecomputeObjectRequest request, StreamObserver<RecomputeObjectResponse> responseObserver) {
        runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_RECOMPUTE_OBJECT);

            Class<? extends FocusType> clazz;
            if (request.hasType()) {
                QName qname = toQNameValue(request.getType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            } else {
                QName qname = toQNameValue(request.getObjectType());
                clazz = ObjectTypes.getObjectTypeClass(qname);
            }

            if (!FocusType.class.isAssignableFrom(clazz)) {
                StatusRuntimeException exception = Status.INVALID_ARGUMENT
                        .withDescription("invalid_object_type")
                        .asRuntimeException();
                throw exception;
            }

            String oid = resolveOid(UserType.class, request.getOid(), request.getName(), task, parentResult);
            ModelExecuteOptions options = ModelExecuteOptions.createReconcile();

            ObjectDelta<? extends FocusType> emptyDelta = prismContext.deltaFactory().object()
                    .createEmptyDelta(clazz, oid, ChangeType.MODIFY);
            modelService.executeChanges(Collections.singleton(emptyDelta), options, task, parentResult);

            parentResult.computeStatus();
            return null;
        });

        responseObserver.onNext(RecomputeObjectResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    private String resolveOid(Class<? extends ObjectType> clazz, String oid, String name, Task task, OperationResult result) throws SecurityViolationException, ObjectNotFoundException,
            CommunicationException, ConfigurationException, SchemaException, ExpressionEvaluationException {
        if (oid.isEmpty() && !name.isEmpty()) {
            // Fallback to search by name
            ObjectType obj = searchObjectByName(clazz, name, task, result);
            if (obj == null) {
                throw new ObjectNotFoundException("Not found: " + name);
            }
            oid = obj.getOid();
        }
        return oid;
    }

    private <T extends ObjectType> T searchObjectByName(Class<T> type, String name, Task task, OperationResult result)
            throws SecurityViolationException, ObjectNotFoundException, CommunicationException, ConfigurationException,
            SchemaException, ExpressionEvaluationException {
        ObjectQuery nameQuery = ObjectQueryUtil.createNameQuery(name, prismContext);
        List<PrismObject<T>> foundObjects = modelService
                .searchObjects(type, nameQuery,
                        getDefaultGetOptionCollection(), task, result);
        if (foundObjects.isEmpty()) {
            return null;
        }
        if (foundObjects.size() > 1) {
            throw new IllegalStateException("More than one object found for type " + type + " and name '" + name + "'");
        }
        return foundObjects.iterator().next().asObjectable();
    }
}