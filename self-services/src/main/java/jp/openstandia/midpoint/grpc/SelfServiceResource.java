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
import com.evolveum.midpoint.prism.delta.DeltaFactory;
import com.evolveum.midpoint.prism.delta.ItemDelta;
import com.evolveum.midpoint.prism.delta.ObjectDelta;
import com.evolveum.midpoint.prism.delta.PropertyDelta;
import com.evolveum.midpoint.prism.delta.builder.S_ItemEntry;
import com.evolveum.midpoint.prism.delta.builder.S_MaybeDelete;
import com.evolveum.midpoint.prism.delta.builder.S_ValuesEntry;
import com.evolveum.midpoint.prism.path.ItemPath;
import com.evolveum.midpoint.prism.query.ObjectQuery;
import com.evolveum.midpoint.prism.query.QueryFactory;
import com.evolveum.midpoint.prism.schema.SchemaRegistry;
import com.evolveum.midpoint.repo.api.RepositoryService;
import com.evolveum.midpoint.schema.*;
import com.evolveum.midpoint.schema.constants.SchemaConstants;
import com.evolveum.midpoint.schema.result.OperationResult;
import com.evolveum.midpoint.schema.util.LocalizationUtil;
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
import static jp.openstandia.midpoint.grpc.TypeConverter.toMessage;
import static jp.openstandia.midpoint.grpc.TypeConverter.toPrismObject;

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
    public static final String OPERATION_ADD_USER = CLASS_DOT + "addUser";
    public static final String OPERATION_EXECUTE_USER_UPDATE = CLASS_DOT + "executeUserUpdate";
    public static final String OPERATION_EXECUTE_CREDENTIAL_CHECK = CLASS_DOT + "executeCredentialCheck";
    public static final String OPERATION_EXECUTE_CREDENTIAL_UPDATE = CLASS_DOT + "executeCredentialUpdate";
    public static final String OPERATION_REQUEST_ASSIGNMENTS = CLASS_DOT + "requestAssignments";
    public static final String OPERATION_SEARCH_OBJECTS = CLASS_DOT + "searchObjects";

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

        PrismObject<UserType> self = runTask(ctx -> {
            Task task = ctx.task;
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SELF);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            Collection<SelectorOptions<GetOperationOptions>> getOptions = GetOperationOptions.fromRestOptions(options, include,
                    exclude, resolveNames, null, prismContext);

            try {
                PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), getOptions, task, parentResult);
                parentResult.recordSuccessIfUnknown();
                return user;
            } finally {
                parentResult.computeStatusIfUnknown();
            }
        });

        GetSelfResponse res = GetSelfResponse.newBuilder()
                .setProfile(toMessage(self.asObjectable()))
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

            try {
                PrismObject<UserType> user = modelCrudService.getObject(UserType.class, loggedInUser.getOid(), null, task, parentResult);
                UserType userType = user.asObjectable();

                List<AssignmentType> assignment = userType.getAssignment();
                List<String> oids = assignment.stream()
                        .map(x -> x.getTargetRef().getOid())
                        .collect(Collectors.toList());

                if (request.getIncludeOrgRefDetail()) {
                    List<String> orgRefOids = assignment.stream()
                            .filter(x -> x.getOrgRef() != null)
                            .map(x -> x.getOrgRef().getOid())
                            .collect(Collectors.toList());
                    oids.addAll(orgRefOids);
                }

                // key: oid, value: Org/Role/Archetype etc.
                Map<String, AbstractRoleType> cache = new HashMap<>();

                // TODO: use search mode for performance?
                // For caching detail of the target objects
                oids.stream().forEach(x -> {
                    try {
                        AbstractRoleType o = modelService.getObject(AbstractRoleType.class, x,
                                SelectorOptions.createCollection(GetOperationOptions.createExecutionPhase().resolveNames(true)), task, parentResult)
                                .asObjectable();
                        cache.put(x, o);

                        // End User doesn't have get permission for Archetype with default.
                        // So currently, we rely on `resolveNames` mode to get name of archetype.
//                        if (request.getIncludeArchetypeRefDetail()) {
//                            o.getArchetypeRef().stream()
//                                    .forEach(ref -> {
//                                        try {
//                                            ArchetypeType a = modelService.getObject(ArchetypeType.class, ref.getOid(),
//                                                    SelectorOptions.createCollection(GetOperationOptions.createExecutionPhase().resolve(true)), task, parentResult)
//                                                    .asObjectable();
//                                            cache.put(ref.getOid(), a);
//
//                                        } catch (CommonException e) {
//                                            LOGGER.warn("Cannot fetch the archetype for collecting assignment detail. oid: {}", ref.getOid(), e);
//                                        }
//                                    });
//                        }
                    } catch (CommonException e) {
                        LOGGER.warn("Cannot fetch the object for collecting assignment detail. oid: {}", x, e);
                    }
                });

                // End User doesn't have search permission with default setting...
                // TODO: use admin permission partially?
                // For caching detail of the target objects
//                ObjectQuery query = createInOidQuery(ObjectType.class, oids);
//                SearchResultList<PrismObject<AbstractRoleType>> foundObjects = modelService.searchObjects(AbstractRoleType.class, query, null,
//                        task, parentResult);
//                foundObjects.stream().forEach(x -> cache.put(x.getOid(), x.asObjectable()));

                List<AssignmentMessage> assignmentMessages = assignment.stream()
                        // The user might not have permission to get the target. So filter them.
                        .filter(x -> cache.containsKey(x.getTargetRef().getOid()))
                        .map(x -> {
                            AbstractRoleType o = cache.get(x.getTargetRef().getOid());

                            ObjectReferenceType orgRef = x.getOrgRef();
                            AbstractRoleType resolvedOrgRef = null;
                            if (orgRef != null) {
                                resolvedOrgRef = cache.get(orgRef.getOid());
                            }

                            QName relation = x.getTargetRef().getRelation();

                            return BuilderWrapper.wrap(AssignmentMessage.newBuilder())
                                    .nullSafe(toMessage(orgRef, resolvedOrgRef), (b, v) -> b.setOrgRef(v))
                                    .unwrap()
                                    .setTargetRef(
                                            BuilderWrapper.wrap(ReferenceMessage.newBuilder())
                                                    .nullSafe(o.getOid(), (b, v) -> b.setOid(v))
                                                    .nullSafe(toMessage(o.getName()), (b, v) -> b.setName(v))
                                                    .nullSafe(toMessage(o.getDescription()), (b, v) -> b.setDescription(v))
                                                    .nullSafe(toMessage(o.getDisplayName()), (b, v) -> b.setDisplayName(v))
                                                    .nullSafe(toMessage(o.getArchetypeRef(), cache), (b, v) -> b.addAllArchetypeRef(v))
                                                    .unwrap()
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

                parentResult.recordSuccessIfUnknown();
                return assignmentMessages;
            } finally {
                parentResult.computeStatusIfUnknown();
            }
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
            UserType loggedInUser = ctx.principal.getUser();

            OperationResult updateResult = task.getResult().createSubresult(OPERATION_EXECUTE_USER_UPDATE);
            try {
                Collection<ObjectDelta<? extends ObjectType>> modifications = new ArrayList<>();

                PrismContainerDefinition<UserType> definition = prismContext.getSchemaRegistry()
                        .findContainerDefinitionByCompileTimeClass(UserType.class);

                S_ItemEntry i = prismContext.deltaFor(UserType.class);

                // https://wiki.evolveum.com/display/midPoint/Using+Prism+Deltas
                for (UserItemDelta m : request.getModificationsList()) {
                    ItemPath path;
                    if (m.hasItemPath()) {
                        path = TypeConverter.toRealValue(m.getItemPath());

                    } else if (m.getPathWrapperCase() == UserItemDelta.PathWrapperCase.PATH) {
                        path = TypeConverter.toItemPath(m.getPath());

                    } else if (m.getPathWrapperCase() == UserItemDelta.PathWrapperCase.USER_TYPE_PATH) {
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

                    S_ValuesEntry v = i.item(path);

                    S_ItemEntry entry = null;

                    // Plain string
                    // TODO Remove this API
                    if (!m.getValuesToAdd().isEmpty()) {
                        S_MaybeDelete av = v.add(TypeConverter.toRealValue(m.getValuesToAdd(), itemClass));

                        if (!m.getValuesToDelete().isEmpty()) {
                            entry = av.delete(TypeConverter.toRealValue(m.getValuesToDelete(), itemClass));
                        } else {
                            entry = av;
                        }
                    } else if (!m.getValuesToReplace().isEmpty()) {
                        entry = v.replace(TypeConverter.toRealValue(m.getValuesToReplace(), itemClass));

                    } else if (!m.getValuesToDelete().isEmpty()) {
                        entry = v.delete(TypeConverter.toRealValue(m.getValuesToDelete(), itemClass));
                    }

                    // PrismValue
                    if (!m.getPrismValuesToAddList().isEmpty()) {
                        S_MaybeDelete av = v.add(TypeConverter.toPrismValue(prismContext, itemDef, m.getPrismValuesToAddList(), itemClass));

                        if (!m.getPrismValuesToDeleteList().isEmpty()) {
                            entry = av.delete(TypeConverter.toPrismValue(prismContext, itemDef, m.getPrismValuesToDeleteList(), itemClass));
                        } else {
                            entry = av;
                        }
                    } else if (!m.getPrismValuesToReplaceList().isEmpty()) {
                        entry = v.replace(TypeConverter.toPrismValue(prismContext, itemDef, m.getPrismValuesToReplaceList(), itemClass));

                    } else if (!m.getPrismValuesToDeleteList().isEmpty()) {
                        entry = v.delete(TypeConverter.toPrismValue(prismContext, itemDef, m.getPrismValuesToDeleteList(), itemClass));
                    }

                    if (entry == null) {
                        LOGGER.warn("Invalid argument. No values for modification.");
                        throw new StatusRuntimeException(Status.INVALID_ARGUMENT);
                    }
                    i = entry;
                }
                // TODO implement options
                ModelExecuteOptions options = new ModelExecuteOptions();

                List<ItemDelta<?, ?>> deltas = i.asItemDeltas();

                modelCrudService.modifyObject(UserType.class, loggedInUser.getOid(), deltas, options, task, updateResult);

                updateResult.computeStatus();
            } finally {
                updateResult.computeStatusIfUnknown();
            }
            return null;
        });

        ModifyProfileResponse res = ModifyProfileResponse.newBuilder().build();
        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End updateProfile");
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
                    .map(x -> TypeConverter.toRealValue(prismContext, x).asPrismContainerValue())
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

            parentResult.recordSuccess();
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

            return oid;
        });

        AddUserResponse res = AddUserResponse.newBuilder()
                .setOid(resultOid)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End addUser");
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

    private <T extends ObjectType> SearchResultList<PrismObject<T>> search(SearchRequest request, Class<T> clazz) {
        SearchResultList<PrismObject<T>> result = runTask(ctx -> {
            Task task = ctx.task;

            OperationResult parentResult = task.getResult().createSubresult(OPERATION_SEARCH_OBJECTS);

            List<String> options = request.getOptionsList();
            List<String> include = request.getIncludeList();
            List<String> exclude = request.getExcludeList();
            List<String> resolveNames = request.getResolveNamesList();

            try {
                Collection<SelectorOptions<GetOperationOptions>> searchOptions = GetOperationOptions.fromRestOptions(options, include,
                        exclude, resolveNames, DefinitionProcessingOption.FULL, prismContext);

                ObjectQuery query = TypeConverter.toObjectQuery(prismContext, clazz, request.getQuery());

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

                parentResult.recordSuccessIfUnknown();
                return foundObjects;
            } finally {
                parentResult.computeStatusIfUnknown();
            }
        });
        return result;
    }

    @Override
    public void searchUsers(SearchRequest request, StreamObserver<SearchUsersResponse> responseObserver) {
        LOGGER.debug("Start searchUsers");

        SearchResultList<PrismObject<UserType>> result = search(request, UserType.class);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<UserTypeMessage> users = result.stream().map(x -> toMessage(x.asObjectable())).collect(Collectors.toList());

        SearchUsersResponse res = SearchUsersResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(users)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchUsers");
    }

    @Override
    public void searchRoles(SearchRequest request, StreamObserver<SearchRolesResponse> responseObserver) {
        LOGGER.debug("Start searchRols");

        SearchResultList<PrismObject<RoleType>> result = search(request, RoleType.class);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<RoleTypeMessage> roles = result.stream().map(x -> toMessage(x.asObjectable())).collect(Collectors.toList());

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

        SearchResultList<PrismObject<OrgType>> result = search(request, OrgType.class);

        Integer total = result.getMetadata().getApproxNumberOfAllResults();
        List<OrgTypeMessage> orgs = result.stream().map(x -> toMessage(x.asObjectable())).collect(Collectors.toList());

        SearchOrgsResponse res = SearchOrgsResponse.newBuilder()
                .setNumberOfAllResults(total)
                .addAllResults(orgs)
                .build();

        responseObserver.onNext(res);
        responseObserver.onCompleted();

        LOGGER.debug("End searchOrgs");
    }

    private <T> List<PrismObject<UserType>> findUsers(QName propertyName, T email, QName matchingRule,
                                                      Task task, OperationResult result) throws SchemaException, ObjectNotFoundException,
            SecurityViolationException, CommunicationException, ConfigurationException, ExpressionEvaluationException {

        ObjectQuery query = createUserEQQuery(propertyName, matchingRule, email);
        List<PrismObject<UserType>> foundObjects = modelService.searchObjects(UserType.class, query, null,
                task, result);
        return foundObjects;
    }

    private <T> ObjectQuery createUserEQQuery(QName property, QName matchingRule, T value)
            throws SchemaException {
        return prismContext.queryFor(UserType.class)
                .item(property)
                .eq(value)
                .matching(matchingRule)
                .build();
    }

    private <T> ObjectQuery createInOidQuery(Class<? extends Containerable> queryClass, List<String> oids) throws SchemaException {
        return prismContext.queryFor(queryClass)
                .id(oids.toArray(new String[]{}))
                .build();
    }
}