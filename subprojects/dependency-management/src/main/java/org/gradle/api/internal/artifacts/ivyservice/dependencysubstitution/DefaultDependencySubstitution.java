/*
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.DefaultComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;

import static org.gradle.api.artifacts.result.ComponentSelectionCause.REQUESTED;
import static org.gradle.api.artifacts.result.ComponentSelectionCause.SELECTED_BY_RULE;

public class DefaultDependencySubstitution implements DependencySubstitutionInternal {
    private final ComponentSelector requested;
    private ComponentSelectionDescriptorInternal selectionDescription;
    private ComponentSelector target;

    public DefaultDependencySubstitution(ComponentSelector requested, String reason) {
        this.requested = requested;
        this.target = requested;
        if (reason != null) {
            this.selectionDescription = VersionSelectionReasons.REQUESTED.withReason(reason);
        } else {
            this.selectionDescription = VersionSelectionReasons.REQUESTED;
        }
    }

    @Override
    public ComponentSelector getRequested() {
        return requested;
    }

    @Override
    public void useTarget(Object notation) {
        useTarget(notation, VersionSelectionReasons.SELECTED_BY_RULE);
    }

    @Override
    public void useTarget(Object notation, String reason) {
        useTarget(notation, new DefaultComponentSelectionDescriptor(SELECTED_BY_RULE, reason));
    }

    @Override
    public void useTarget(Object notation, ComponentSelectionDescriptor selectionDescription) {
        this.target = ComponentSelectorParsers.parser().parseNotation(notation);
        this.selectionDescription = (ComponentSelectionDescriptorInternal) selectionDescription;
        validateTarget(target);
    }

    @Override
    public ComponentSelectionDescriptorInternal getSelectionDescription() {
        return selectionDescription;
    }

    @Override
    public ComponentSelector getTarget() {
        return target;
    }

    @Override
    public boolean isUpdated() {
        return selectionDescription.getCause() != REQUESTED;
    }

    public static void validateTarget(ComponentSelector componentSelector) {
        if (componentSelector instanceof UnversionedModuleComponentSelector) {
            throw new InvalidUserDataException("Must specify version for target of dependency substitution");
        }
    }
}
