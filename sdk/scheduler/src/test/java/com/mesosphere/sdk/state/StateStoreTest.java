package com.mesosphere.sdk.state;

import com.mesosphere.sdk.testutils.TestConstants;
import org.apache.mesos.Protos;

import com.mesosphere.sdk.offer.CommonIdUtils;
import com.mesosphere.sdk.storage.MemPersister;
import com.mesosphere.sdk.storage.Persister;
import com.mesosphere.sdk.storage.PersisterException;
import com.mesosphere.sdk.storage.PersisterUtils;
import com.mesosphere.sdk.storage.StorageError;

import org.junit.*;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.Assert.*;

/**
 * Tests to validate the operation of the {@link StateStore}.
 */
public class StateStoreTest {
    private static final Protos.TaskState TASK_STATE = Protos.TaskState.TASK_STAGING;
    private static final Protos.TaskStatus TASK_STATUS = Protos.TaskStatus.newBuilder()
            .setTaskId(TestConstants.TASK_ID)
            .setState(TASK_STATE)
            .build();
    private static final String NAMESPACE = "test-namespace";
    private static final String NAMESPACE_PATH = "Services/" + NAMESPACE;
    private static final String PROPERTY_VALUE = "DC/OS";
    private static final String GOOD_PROPERTY_KEY = "hey";
    private static final String WHITESPACE_PROPERTY_KEY = "            ";
    private static final String SLASH_PROPERTY_KEY = "hey/hi";

    private Persister persister;
    private StateStore store;

    @Before
    public void beforeEach() throws Exception {
        persister = MemPersister.newBuilder().build();
        store = new StateStore(persister);
    }

    // persister paths

    @Test
    public void testRootPathMapping() throws Exception {
        Collection<Protos.TaskInfo> tasks = createTasks(TestConstants.TASK_NAME);
        store.storeTasks(tasks);
        store.storeStatus(TestConstants.TASK_NAME, TASK_STATUS);
        GoalStateOverride.Status status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.PENDING);
        store.storeGoalOverrideStatus(TestConstants.TASK_NAME, status);
        store.storeProperty(GOOD_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));

        // Check that data is at root path:
        assertNotEquals(0, persister.get("Tasks/" + TestConstants.TASK_NAME + "/TaskInfo"));
        assertNotEquals(0, persister.get("Tasks/" + TestConstants.TASK_NAME + "/TaskStatus"));
        assertEquals("PAUSED", new String(persister.get("Tasks/" + TestConstants.TASK_NAME + "/Metadata/goal-state-override"), StandardCharsets.UTF_8));
        assertEquals("PENDING", new String(persister.get("Tasks/" + TestConstants.TASK_NAME + "/Metadata/override-status"), StandardCharsets.UTF_8));
        assertEquals(PROPERTY_VALUE, new String(persister.get("Properties/" + GOOD_PROPERTY_KEY), StandardCharsets.UTF_8));

        // Check that data is NOT in namespaced path:
        checkPathNotFound(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/TaskInfo");
        checkPathNotFound(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/TaskStatus");
        checkPathNotFound(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/Metadata/goal-state-override");
        checkPathNotFound(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/Metadata/override-status");
        checkPathNotFound(NAMESPACE_PATH + "/Properties/" + GOOD_PROPERTY_KEY);

        // Check that data is accessible as expected:
        assertEquals(tasks.iterator().next(), store.fetchTask(TestConstants.TASK_NAME).get());
        assertEquals(TASK_STATUS, store.fetchStatus(TestConstants.TASK_NAME).get());
        assertEquals(status, store.fetchGoalOverrideStatus(TestConstants.TASK_NAME));
        assertEquals(PROPERTY_VALUE, new String(store.fetchProperty(GOOD_PROPERTY_KEY), StandardCharsets.UTF_8));
    }

    @Test
    public void testNamespacedPathMapping() throws Exception {
        store = new StateStore(persister, Optional.of(NAMESPACE));
        Collection<Protos.TaskInfo> tasks = createTasks(TestConstants.TASK_NAME);
        store.storeTasks(tasks);
        store.storeStatus(TestConstants.TASK_NAME, TASK_STATUS);
        GoalStateOverride.Status status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.PENDING);
        store.storeGoalOverrideStatus(TestConstants.TASK_NAME, status);
        store.storeProperty(GOOD_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));

        // Check that data is in namespaced path:
        assertNotEquals(0, persister.get(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/TaskInfo"));
        assertNotEquals(0, persister.get(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/TaskStatus"));
        assertEquals("PAUSED", new String(persister.get(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/Metadata/goal-state-override"), StandardCharsets.UTF_8));
        assertEquals("PENDING", new String(persister.get(NAMESPACE_PATH + "/Tasks/" + TestConstants.TASK_NAME + "/Metadata/override-status"), StandardCharsets.UTF_8));
        assertEquals(PROPERTY_VALUE, new String(persister.get(NAMESPACE_PATH + "/Properties/" + GOOD_PROPERTY_KEY), StandardCharsets.UTF_8));

        // Check that data is NOT at root path:
        checkPathNotFound("Tasks/" + TestConstants.TASK_NAME + "/TaskInfo");
        checkPathNotFound("Tasks/" + TestConstants.TASK_NAME + "/TaskStatus");
        checkPathNotFound("Tasks/" + TestConstants.TASK_NAME + "/Metadata/goal-state-override");
        checkPathNotFound("Tasks/" + TestConstants.TASK_NAME + "/Metadata/override-status");
        checkPathNotFound("Properties/" + GOOD_PROPERTY_KEY);

        // Check that data is accessible as expected:
        assertEquals(tasks.iterator().next(), store.fetchTask(TestConstants.TASK_NAME).get());
        assertEquals(TASK_STATUS, store.fetchStatus(TestConstants.TASK_NAME).get());
        assertEquals(status, store.fetchGoalOverrideStatus(TestConstants.TASK_NAME));
        assertEquals(PROPERTY_VALUE, new String(store.fetchProperty(GOOD_PROPERTY_KEY), StandardCharsets.UTF_8));
    }

    // task

    @Test
    public void testStoreFetchTask() throws Exception {
        Protos.TaskInfo testTask = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(testTask));
        assertEquals(testTask, store.fetchTask(TestConstants.TASK_NAME).get());
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks();
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());
    }

    @Test
    public void testFetchMissingTask() throws Exception {
        assertFalse(store.fetchTask(TestConstants.TASK_NAME).isPresent());
    }

    @Test
    public void testFetchEmptyTasks() throws Exception {
        assertTrue(store.fetchTasks().isEmpty());
    }

    @Test
    public void testRepeatedStoreTask() throws Exception {
        Collection<Protos.TaskInfo> tasks = createTasks(TestConstants.TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TestConstants.TASK_NAME).get());

        tasks = createTasks(TestConstants.TASK_NAME);
        store.storeTasks(tasks);
        assertEquals(tasks.iterator().next(), store.fetchTask(TestConstants.TASK_NAME).get());

        Collection<String> taskNames = store.fetchTaskNames();
        assertEquals(1, taskNames.size());
        assertEquals(tasks, store.fetchTasks());
    }

    @Test
    public void testStoreClearTask() throws Exception {
        store.storeTasks(createTasks(TestConstants.TASK_NAME));
        store.clearTask(TestConstants.TASK_NAME);
    }

    @Test
    public void testStoreClearFetchTask() throws Exception {
        store.storeTasks(createTasks(TestConstants.TASK_NAME));
        store.clearTask(TestConstants.TASK_NAME);
        assertFalse(store.fetchTask(TestConstants.TASK_NAME).isPresent());
    }

    @Test
    public void testStoreClearAllData() throws Exception {
        store.storeTasks(createTasks(TestConstants.TASK_NAME));
        store.storeProperty(GOOD_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));
        assertEquals(5, PersisterUtils.getAllKeys(persister).size());

        PersisterUtils.clearAllData(persister);

        // Verify nothing is left under the root.
        assertTrue(PersisterUtils.getAllKeys(persister).isEmpty());
    }

    @Test
    public void testClearMissingTask() throws Exception {
        store.clearTask(TestConstants.TASK_NAME);
    }

    @Test
    public void testFetchEmptyTaskNames() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
    }

    @Test
    public void testFetchTaskNames() throws Exception {
        String testTaskNamePrefix = "test-executor";
        String testTaskName0 = testTaskNamePrefix + "-0";
        String testTaskName1 = testTaskNamePrefix + "-1";

        store.storeTasks(createTasks(testTaskName0, testTaskName1));
        assertEquals(Arrays.asList(testTaskName0, testTaskName1), store.fetchTaskNames());
    }

    @Test
    public void testMultipleTasks() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoA = StateStoreUtilsTest.createTask("a");
        store.storeTasks(Arrays.asList(taskInfoA));

        assertEquals(taskInfoA, store.fetchTask("a").get());
        assertEquals(Arrays.asList("a"), store.fetchTaskNames());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoB = StateStoreUtilsTest.createTask("b");
        store.storeTasks(Arrays.asList(taskInfoB));

        assertEquals(taskInfoB, store.fetchTask("b").get());
        assertEquals(Arrays.asList("a", "b"), store.fetchTaskNames());
        assertEquals(2, store.fetchTasks().size());
        assertTrue(store.fetchStatuses().isEmpty());

        store.clearTask("a");

        assertEquals(taskInfoB, store.fetchTask("b").get());
        assertEquals(Arrays.asList("b"), store.fetchTaskNames());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoB, store.fetchTasks().iterator().next());
        assertTrue(store.fetchStatuses().isEmpty());

        store.clearTask("b");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    // status

    @Test
    public void testStoreFetchStatusExactMatch() throws Exception {
        Protos.TaskInfo task = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(TestConstants.TASK_NAME, status);
        assertEquals(status, store.fetchStatus(TestConstants.TASK_NAME).get());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses();
        assertEquals(1, statuses.size());
        assertEquals(status, statuses.iterator().next());
    }

    @Test
    public void testFetchMissingStatus() throws Exception {
        assertTrue(!store.fetchStatus(TestConstants.TASK_NAME).isPresent());
    }

    @Test
    public void testFetchEmptyStatuses() throws Exception {
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testRepeatedStoreStatus() throws Exception {
        Protos.TaskInfo task = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(task));

        // taskstatus id must exactly match taskinfo id:
        Protos.TaskStatus status = TASK_STATUS.toBuilder().setTaskId(task.getTaskId()).build();
        store.storeStatus(TestConstants.TASK_NAME, status);
        assertEquals(status, store.fetchStatus(TestConstants.TASK_NAME).get());

        store.storeStatus(TestConstants.TASK_NAME, status);
        assertEquals(status, store.fetchStatus(TestConstants.TASK_NAME).get());

        assertEquals(Arrays.asList(TestConstants.TASK_NAME), store.fetchTaskNames());
        Collection<Protos.TaskStatus> statuses = store.fetchStatuses();
        assertEquals(1, statuses.size());
        assertEquals(status, statuses.iterator().next());
    }

    @Test
    public void testMultipleStatuses() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        // must have TaskInfos first:
        Protos.TaskInfo taskA = StateStoreUtilsTest.createTask("a");
        Protos.TaskInfo taskB = StateStoreUtilsTest.createTask("b");
        store.storeTasks(Arrays.asList(taskA, taskB));

        assertEquals(Arrays.asList("a", "b"), store.fetchTaskNames());
        assertTrue(store.fetchStatuses().isEmpty());
        assertEquals(2, store.fetchTasks().size());

        store.storeStatus(taskA.getName(), TASK_STATUS);

        assertEquals(TASK_STATUS, store.fetchStatus("a").get());
        assertEquals(Arrays.asList("a", "b"), store.fetchTaskNames());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(TASK_STATUS, store.fetchStatuses().iterator().next());
        assertEquals(2, store.fetchTasks().size());

        store.storeStatus(taskB.getName(), TASK_STATUS);

        assertEquals(TASK_STATUS, store.fetchStatus("b").get());
        assertEquals(Arrays.asList("a", "b"), store.fetchTaskNames());
        assertEquals(2, store.fetchStatuses().size());
        assertEquals(2, store.fetchTasks().size());

        store.clearTask("a");

        assertEquals(TASK_STATUS, store.fetchStatus("b").get());
        assertEquals(Arrays.asList("b"), store.fetchTaskNames());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(TASK_STATUS, store.fetchStatuses().iterator().next());
        assertEquals(1, store.fetchTasks().size());

        store.clearTask("b");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testStoreStatusSucceedsOnUUIDChangeWithTaskInfoUpdate() throws Exception {
        Protos.TaskInfo task = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(task));
        store.storeStatus(TestConstants.TASK_NAME, TASK_STATUS);
        assertEquals(TASK_STATUS, store.fetchStatus(TestConstants.TASK_NAME).get());

        // change the taskinfo id:
        Protos.TaskInfo taskNewId = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(taskNewId));
        store.storeStatus(TestConstants.TASK_NAME, TASK_STATUS);
        assertEquals(TASK_STATUS, store.fetchStatus(TestConstants.TASK_NAME).get());
    }

    @Test
    public void testStoreFetchTaskAndStatus() throws Exception {
        Protos.TaskInfo testTask = StateStoreUtilsTest.createTask(TestConstants.TASK_NAME);
        store.storeTasks(Arrays.asList(testTask));
        Collection<Protos.TaskInfo> outTasks = store.fetchTasks();
        assertEquals(1, outTasks.size());
        assertEquals(testTask, outTasks.iterator().next());

        store.storeStatus(TestConstants.TASK_NAME, TASK_STATUS);
        assertEquals(TASK_STATUS, store.fetchStatus(TestConstants.TASK_NAME).get());
    }

    @Test
    public void testStoreInfoThenStatus() throws Exception {
        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());

        Protos.TaskInfo taskInfoA = StateStoreUtilsTest.createTask("a");
        store.storeTasks(Arrays.asList(taskInfoA));

        assertEquals(taskInfoA, store.fetchTask("a").get());
        assertEquals(Arrays.asList("a"), store.fetchTaskNames());
        assertTrue(store.fetchStatuses().isEmpty());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());

        store.storeStatus(taskInfoA.getName(), TASK_STATUS);

        assertEquals(TASK_STATUS, store.fetchStatus("a").get());
        assertEquals(Arrays.asList("a"), store.fetchTaskNames());
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(TASK_STATUS, store.fetchStatuses().iterator().next());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(taskInfoA, store.fetchTasks().iterator().next());

        store.clearTask("a");

        assertTrue(store.fetchTaskNames().isEmpty());
        assertTrue(store.fetchTasks().isEmpty());
        assertTrue(store.fetchStatuses().isEmpty());
    }

    @Test
    public void testPropertiesStoreFetchListClear() {
        store.storeProperty(GOOD_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));

        final byte[] bytes = store.fetchProperty(GOOD_PROPERTY_KEY);
        assertEquals(PROPERTY_VALUE, new String(bytes, StandardCharsets.UTF_8));

        final Collection<String> keys = store.fetchPropertyKeys();
        assertEquals(1, keys.size());
        assertEquals(GOOD_PROPERTY_KEY, keys.iterator().next());

        store.clearProperty(GOOD_PROPERTY_KEY);
        assertTrue(store.fetchPropertyKeys().isEmpty());
    }

    @Test
    public void testPropertiesListEmpty() {
        assertTrue(store.fetchPropertyKeys().isEmpty());
    }

    @Test
    public void testPropertiesClearEmpty() {
        store.clearProperty(GOOD_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreWhitespace() {
        store.storeProperty(
                WHITESPACE_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesFetchWhitespace() {
        store.fetchProperty(WHITESPACE_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesClearWhitespace() {
        store.clearProperty(WHITESPACE_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreSlash() {
        store.storeProperty(SLASH_PROPERTY_KEY, PROPERTY_VALUE.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesFetchSlash() {
        store.fetchProperty(SLASH_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesClearSlash() {
        store.clearProperty(SLASH_PROPERTY_KEY);
    }

    @Test(expected = StateStoreException.class)
    public void testPropertiesStoreNullValue() {
        store.storeProperty(GOOD_PROPERTY_KEY, null);
    }

    @Test
    public void testFetchStoreFetchOverride() {
        String taskName = "hello";
        assertEquals(GoalStateOverride.Status.INACTIVE, store.fetchGoalOverrideStatus(taskName));

        GoalStateOverride.Status status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.PENDING);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.IN_PROGRESS);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        status = GoalStateOverride.PAUSED.newStatus(GoalStateOverride.Progress.COMPLETE);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        status = GoalStateOverride.NONE.newStatus(GoalStateOverride.Progress.PENDING);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        status = GoalStateOverride.NONE.newStatus(GoalStateOverride.Progress.IN_PROGRESS);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        status = GoalStateOverride.NONE.newStatus(GoalStateOverride.Progress.COMPLETE);
        store.storeGoalOverrideStatus(taskName, status);
        assertEquals(status, store.fetchGoalOverrideStatus(taskName));

        store.storeGoalOverrideStatus(taskName, GoalStateOverride.Status.INACTIVE);
        assertEquals(GoalStateOverride.Status.INACTIVE, store.fetchGoalOverrideStatus(taskName));
    }

    @Test
    public void testMissingTaskStatus() {
        store.storeTasks(Arrays.asList(TestConstants.TASK_INFO));
        assertEquals(0, store.fetchStatuses().size());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(TestConstants.TASK_INFO, store.fetchTasks().stream().findAny().get());

        store = new StateStore(persister);
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(TestConstants.TASK_ID, store.fetchTasks().stream().findAny().get().getTaskId());

        Protos.TaskStatus taskStatus = store.fetchStatuses().stream().findAny().get();
        assertEquals(TestConstants.TASK_ID, taskStatus.getTaskId());
        assertEquals(Protos.TaskState.TASK_FAILED, taskStatus.getState());
    }

    @Test
    public void testMismatchedTaskIds() {
        // Generate a different UUID:
        Protos.TaskID taskID = CommonIdUtils.toTaskId(TestConstants.SERVICE_NAME, TestConstants.TASK_NAME);
        Protos.TaskInfo taskInfo = Protos.TaskInfo.newBuilder(TestConstants.TASK_INFO)
                .setTaskId(taskID)
                .build();

        // Need the multiple storeTasks calls to trick the StateStore into doing the wrong thing
        store.storeTasks(Arrays.asList(TestConstants.TASK_INFO));
        store.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);
        store.storeTasks(Arrays.asList(taskInfo));
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(1, store.fetchTasks().size());
        assertNotEquals(TestConstants.TASK_ID, store.fetchTasks().stream().findAny().get().getTaskId());
        assertEquals(TestConstants.TASK_ID, store.fetchStatuses().stream().findAny().get().getTaskId());

        store = new StateStore(persister);
        assertEquals(1, store.fetchStatuses().size());
        assertEquals(1, store.fetchTasks().size());
        assertEquals(TestConstants.TASK_ID, store.fetchTasks().stream().findAny().get().getTaskId());

        Protos.TaskStatus taskStatus = store.fetchStatuses().stream().findAny().get();
        assertEquals(TestConstants.TASK_ID, taskStatus.getTaskId());
        assertEquals(Protos.TaskState.TASK_FAILED, taskStatus.getState());

        taskInfo = taskInfo.toBuilder().setTaskId(TestConstants.TASK_ID).build();
        assertEquals(taskInfo, store.fetchTasks().stream().findAny().get());
    }

    @Test(expected = StateStoreException.class)
    public void testStoreUnknownStatus() {
        // Store initial status as RUNNING
        store.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);

        // Create status with unknown TaskID but the same task name
        Protos.TaskStatus status = TestConstants.TASK_STATUS.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .build();

        store.storeStatus(TestConstants.TASK_NAME, status);
    }

    @Test
    public void testStoreNewStagingStatus() {
        // Store initial status as RUNNING
        store.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);

        // Store new task with same name as STAGING
        Protos.TaskStatus status = TestConstants.TASK_STATUS.toBuilder()
                .setTaskId(Protos.TaskID.newBuilder().setValue(UUID.randomUUID().toString()))
                .setState(Protos.TaskState.TASK_STAGING)
                .build();

        store.storeStatus(TestConstants.TASK_NAME, status);
    }

    @Test
    public void testDeleteDataIfNamespaced_notNamespaced() {
        store.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);
        store.deleteAllDataIfNamespaced();

        // no change because it's not namespaced:
        assertTrue(store.fetchStatus(TestConstants.TASK_NAME).isPresent());
    }

    @Test
    public void testDeleteDataIfNamespaced_isNamespaced() {
        StateStore store2 = new StateStore(persister, Optional.of(NAMESPACE + "-two"));
        store2.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);

        store = new StateStore(persister, Optional.of(NAMESPACE));
        store.storeStatus(TestConstants.TASK_NAME, TestConstants.TASK_STATUS);
        store.deleteAllDataIfNamespaced();

        // this namespace is deleted:
        assertFalse(store.fetchStatus(TestConstants.TASK_NAME).isPresent());

        // the other namespace is not deleted:
        assertTrue(store2.fetchStatus(TestConstants.TASK_NAME).isPresent());
    }

    private static Collection<Protos.TaskInfo> createTasks(String... taskNames) {
        List<Protos.TaskInfo> taskInfos = new ArrayList<>();
        for (String taskName : taskNames) {
            taskInfos.add(StateStoreUtilsTest.createTask(taskName));
        }
        return taskInfos;
    }

    private void checkPathNotFound(String path) {
        try {
            persister.get(path);
        } catch (PersisterException e) {
            assertEquals(StorageError.Reason.NOT_FOUND, e.getReason());
        }
    }
}
