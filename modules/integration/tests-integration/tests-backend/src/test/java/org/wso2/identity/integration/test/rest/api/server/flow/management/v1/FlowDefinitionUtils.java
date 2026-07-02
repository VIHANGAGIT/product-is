/*
 * Copyright (c) 2026, WSO2 LLC. (http://www.wso2.com).
 *
 * WSO2 LLC. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.identity.integration.test.rest.api.server.flow.management.v1;

import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.Action;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.Component;
import org.wso2.identity.integration.test.rest.api.server.flow.management.v1.model.Executor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilities to locate and mutate parts of a flow definition (the flow management model) in tests, so flow
 * variants can be derived programmatically from a single JSON resource instead of duplicating whole definitions.
 */
public final class FlowDefinitionUtils {

    public static final String USER_RESOLVE_EXECUTOR = "UserResolveExecutor";
    public static final String NOTIFY_USER_EXISTENCE = "notifyUserExistence";
    public static final String NOTIFY_USER_ACCOUNT_STATUS = "notifyUserAccountStatus";
    private static final String COMPONENT_CONFIG_IDENTIFIER = "identifier";
    private static final String COMPONENT_TYPE_INPUT = "INPUT";

    private FlowDefinitionUtils() {

    }

    /**
     * Recursively find the first component whose action executor has the given name.
     *
     * @param components   Components to search (searched depth-first).
     * @param executorName Executor name to match.
     * @return The component carrying the executor, or {@code null} if not found.
     */
    public static Component findComponentByExecutor(List<Component> components, String executorName) {

        if (components == null) {
            return null;
        }
        for (Component component : components) {
            Action action = component.getAction();
            if (action != null && action.getExecutor() != null
                    && executorName.equals(action.getExecutor().getName())) {
                return component;
            }
            Component nested = findComponentByExecutor(component.getComponents(), executorName);
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    /**
     * Recursively find the meta map of the executor with the given name.
     *
     * @param components   Components to search.
     * @param executorName Executor name to match.
     * @return The executor's meta as a map, or {@code null} if the executor is absent or carries no map meta.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> findExecutorMeta(List<Component> components, String executorName) {

        Component component = findComponentByExecutor(components, executorName);
        if (component == null) {
            return null;
        }
        Object meta = component.getAction().getExecutor().getMeta();
        return meta instanceof Map ? (Map<String, Object>) meta : null;
    }

    /**
     * Recursively find the id of the component (the action id submitted to the flow execution API) whose
     * executor has the given name.
     *
     * @param components   Components to search.
     * @param executorName Executor name to match.
     * @return The component id, or {@code null} if not found.
     */
    public static String findActionIdByExecutor(List<Component> components, String executorName) {

        Component component = findComponentByExecutor(components, executorName);
        return component != null ? component.getId() : null;
    }

    /**
     * Set (or remove) the enumeration-control flags on the {@code UserResolveExecutor} meta. A {@code null}
     * value removes the flag from the meta, exercising the absent-flag default of the executor.
     *
     * @param components              Components of the step carrying the {@code UserResolveExecutor}.
     * @param notifyUserExistence     Value for {@code notifyUserExistence}, or {@code null} to remove it.
     * @param notifyUserAccountStatus Value for {@code notifyUserAccountStatus}, or {@code null} to remove it.
     */
    public static void setUserResolveNotifyFlags(List<Component> components, String notifyUserExistence,
                                                 String notifyUserAccountStatus) {

        Component component = findComponentByExecutor(components, USER_RESOLVE_EXECUTOR);
        if (component == null) {
            throw new IllegalArgumentException(USER_RESOLVE_EXECUTOR + " not found in the flow definition.");
        }
        Executor executor = component.getAction().getExecutor();
        @SuppressWarnings("unchecked")
        Map<String, Object> meta = executor.getMeta() instanceof Map
                ? (Map<String, Object>) executor.getMeta()
                : new HashMap<>();
        setOrRemove(meta, NOTIFY_USER_EXISTENCE, notifyUserExistence);
        setOrRemove(meta, NOTIFY_USER_ACCOUNT_STATUS, notifyUserAccountStatus);
        executor.setMeta(meta);
    }

    /**
     * Rebind the first INPUT component's identifier (the input field name submitted to the flow execution API)
     * within the given components, e.g. from the username claim to the generic {@code userIdentifier} used with
     * multi-attribute login.
     *
     * @param components Components of the step carrying the identifier input.
     * @param identifier New identifier for the input field.
     */
    public static void setInputIdentifier(List<Component> components, String identifier) {

        Component input = findFirstInputComponent(components);
        if (input == null || !(input.getConfig() instanceof Map)) {
            throw new IllegalArgumentException("No INPUT component with a config map found in the flow definition.");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> config = (Map<String, Object>) input.getConfig();
        config.put(COMPONENT_CONFIG_IDENTIFIER, identifier);
    }

    private static Component findFirstInputComponent(List<Component> components) {

        if (components == null) {
            return null;
        }
        for (Component component : components) {
            if (COMPONENT_TYPE_INPUT.equals(component.getType())) {
                return component;
            }
            Component nested = findFirstInputComponent(component.getComponents());
            if (nested != null) {
                return nested;
            }
        }
        return null;
    }

    private static void setOrRemove(Map<String, Object> meta, String key, String value) {

        if (value == null) {
            meta.remove(key);
        } else {
            meta.put(key, value);
        }
    }
}
