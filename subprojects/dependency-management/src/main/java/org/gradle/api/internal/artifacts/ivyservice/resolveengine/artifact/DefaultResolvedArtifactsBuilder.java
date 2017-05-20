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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact;

import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ResolutionStrategy;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.graph.DependencyGraphNode;
import org.gradle.internal.component.local.model.LocalConfigurationMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.model.ConfigurationMetadata;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Maps.newLinkedHashMap;

/**
 * Collects all artifacts and their build dependencies.
 */
public class DefaultResolvedArtifactsBuilder implements DependencyArtifactsVisitor {
    private final boolean buildProjectDependencies;
    private final ResolutionStrategy.SortOrder sortOrder;
    private final Map<Long, Set<Integer>> sortedNodeIds = newLinkedHashMap();
    private final List<ArtifactSet> artifactSetsById = new ArrayList<ArtifactSet>();

    public DefaultResolvedArtifactsBuilder(boolean buildProjectDependencies, ResolutionStrategy.SortOrder sortOrder) {
        this.buildProjectDependencies = buildProjectDependencies;
        this.sortOrder = sortOrder;
    }

    @Override
    public void startArtifacts(DependencyGraphNode root) {
    }

    @Override
    public void visitNode(DependencyGraphNode node) {
        sortedNodeIds.put(node.getNodeId(), Sets.<Integer>newLinkedHashSet());
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, LocalFileDependencyMetadata fileDependency, ArtifactSet artifacts) {
        collectArtifactsFor(from, artifacts);
    }

    @Override
    public void visitArtifacts(DependencyGraphNode from, DependencyGraphNode to, ArtifactSet artifacts) {
        // Don't collect build dependencies if not required
        if (!buildProjectDependencies) {
            artifacts = new NoBuildDependenciesArtifactSet(artifacts);
        } else {
            ConfigurationMetadata configurationMetadata = to.getMetadata();
            if (configurationMetadata instanceof LocalConfigurationMetadata) {
                if (from.getOwner().getComponentId() instanceof ProjectComponentIdentifier) {
                    // This is here to attempt to leave out build dependencies that would cause a cycle in the task graph for the current build, so that the cross-build cycle detection kicks in. It's not fully correct
                    ProjectComponentIdentifier incomingId = (ProjectComponentIdentifier) from.getOwner().getComponentId();
                    if (!incomingId.getBuild().isCurrentBuild()) {
                        artifacts = new NoBuildDependenciesArtifactSet(artifacts);
                    }
                }
            }
        }
        collectArtifactsFor(to, artifacts);
    }

    private void collectArtifactsFor(DependencyGraphNode node, ArtifactSet artifacts) {
        // Collect artifact sets in a list, using the id of the set as its index in the list
        assert artifactSetsById.size() >= artifacts.getId();
        if (artifactSetsById.size() == artifacts.getId()) {
            artifactSetsById.add(artifacts);
        }
        sortedNodeIds.get(node.getNodeId()).add(artifacts.getId());
    }

    @Override
    public void finishArtifacts() {
    }

    public VisitedArtifactsResults complete() {
        return new DefaultVisitedArtifactResults(sortOrder, sortedNodeIds, artifactSetsById);
    }
}
