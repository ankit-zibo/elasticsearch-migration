/**
 * Copyright (C) 2019 Quandoo GmbH (account.oss@quandoo.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.quandoo.lib.elasticsearchmigration.service.impl;

import com.quandoo.lib.elasticsearchmigration.model.input.AliasesMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.BaseMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.ChecksumedMigrationFile;
import com.quandoo.lib.elasticsearchmigration.model.input.CreateIndexMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.CreateOrUpdateIndexTemplateMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.CreateIngestPipelineMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.DeleteDocumentMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.DeleteIndexMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.DeleteIndexTemplateMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.DeleteIngestPipelineMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.IndexDocumentMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.ReindexMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.UpdateDocumentMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.UpdateIndexSettingsMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.input.UpdateMappingMigrationFileEntry;
import com.quandoo.lib.elasticsearchmigration.model.migration.AliasesMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.CreateIndexMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.CreateOrUpdateIndexTemplateMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.CreateIngestPipelineMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.DeleteDocumentMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.DeleteIndexMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.DeleteIndexTemplateMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.DeleteIngestPipelineMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.IndexDocumentMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.Migration;
import com.quandoo.lib.elasticsearchmigration.model.migration.MigrationMeta;
import com.quandoo.lib.elasticsearchmigration.model.migration.MigrationSet;
import com.quandoo.lib.elasticsearchmigration.model.migration.MigrationSetEntry;
import com.quandoo.lib.elasticsearchmigration.model.migration.OpType;
import com.quandoo.lib.elasticsearchmigration.model.migration.ReindexMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.UpdateDocumentMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.UpdateIndexSettingsMigration;
import com.quandoo.lib.elasticsearchmigration.model.migration.UpdateMappingMigration;
import com.quandoo.lib.elasticsearchmigration.service.MigrationSetProvider;
import com.quandoo.lib.elasticsearchmigration.service.Parser;
import com.quandoo.lib.elasticsearchmigration.util.VersionComparator;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Emir Dizdarevic
 * @since 1.0.0
 */
public class YamlDirectoryMigrationSetProvider implements MigrationSetProvider {

    private static final Pattern MIGRATION_FILE_PATH_PATTERN = Pattern.compile("^.*V([0-9]{1,3}(?:_[0-9]+)*)__([a-zA-Z0-9][a-zA-Z0-9_-]*)\\.yaml$");

    private final Parser yamlParser;

    public YamlDirectoryMigrationSetProvider() {
        this.yamlParser = new YamlParser();
    }

    @Override
    public MigrationSet getMigrationSet(final String basePackage) {
        checkNotNull(basePackage, "basePackage must not be null");

        final Set<String> resources = new Reflections(basePackage, new ResourcesScanner()).getResources(MIGRATION_FILE_PATH_PATTERN);
        final List<String> sortedResources = new ArrayList<>(resources);
        sortedResources.sort(new VersionComparator(MIGRATION_FILE_PATH_PATTERN, 1, "_", e -> e));

        final List<MigrationSetEntry> migrationSetEntries = new LinkedList<>();
        for (String resource : sortedResources) {
            final String resourceName = resource.lastIndexOf("/") != -1 ? resource.substring(resource.lastIndexOf("/") + 1) : resource;
            final Matcher matcher = MIGRATION_FILE_PATH_PATTERN.matcher(resourceName);
            matcher.matches();

            final ChecksumedMigrationFile checksumedMigrationFile = yamlParser.parse(resource);
            migrationSetEntries.add(
                    new MigrationSetEntry(
                            checksumedMigrationFile.getMigrationFile().getMigrations().stream().map(this::convertToMigration).collect(Collectors.toList()),
                            new MigrationMeta(
                                    checksumedMigrationFile.getSha256Checksum(),
                                    matcher.group(1).replaceAll("_", "."),
                                    matcher.group(2)
                            )
                    )
            );
        }

        return new MigrationSet(migrationSetEntries);
    }

    private Migration convertToMigration(BaseMigrationFileEntry baseMigrationFileEntry) {
        switch (baseMigrationFileEntry.getType()) {
            case CREATE_INDEX:
                final CreateIndexMigrationFileEntry createIndexMigrationFileEntry = (CreateIndexMigrationFileEntry) baseMigrationFileEntry;
                return new CreateIndexMigration(createIndexMigrationFileEntry.getIndex(), createIndexMigrationFileEntry.getDefinition());
            case DELETE_INDEX:
                final DeleteIndexMigrationFileEntry deleteIndexMigrationFileEntry = (DeleteIndexMigrationFileEntry) baseMigrationFileEntry;
                return new DeleteIndexMigration(deleteIndexMigrationFileEntry.getIndex());
            case CREATE_OR_UPDATE_INDEX_TEMPLATE:
                final CreateOrUpdateIndexTemplateMigrationFileEntry createOrUpdateIndexTemplateMigrationFileEntry = (CreateOrUpdateIndexTemplateMigrationFileEntry) baseMigrationFileEntry;
                return new CreateOrUpdateIndexTemplateMigration(createOrUpdateIndexTemplateMigrationFileEntry.getTemplate(), createOrUpdateIndexTemplateMigrationFileEntry.getDefinition());
            case DELETE_INDEX_TEMPLATE:
                final DeleteIndexTemplateMigrationFileEntry deleteIndexTemplateMigrationFileEntry = (DeleteIndexTemplateMigrationFileEntry) baseMigrationFileEntry;
                return new DeleteIndexTemplateMigration(deleteIndexTemplateMigrationFileEntry.getTemplate());
            case UPDATE_MAPPING:
                final UpdateMappingMigrationFileEntry updateMappingMigrationFileEntry = (UpdateMappingMigrationFileEntry) baseMigrationFileEntry;
                return new UpdateMappingMigration(updateMappingMigrationFileEntry.getIndices(), updateMappingMigrationFileEntry.getDefinition());
            case INDEX_DOCUMENT:
                final IndexDocumentMigrationFileEntry indexDocumentMigrationFileEntry = (IndexDocumentMigrationFileEntry) baseMigrationFileEntry;
                return new IndexDocumentMigration(
                        indexDocumentMigrationFileEntry.getIndex(),
                        indexDocumentMigrationFileEntry.getId(),
                        indexDocumentMigrationFileEntry.getOpType().map(e -> OpType.valueOf(e.name())),
                        indexDocumentMigrationFileEntry.getDefinition()
                );
            case DELETE_DOCUMENT:
                final DeleteDocumentMigrationFileEntry deleteDocumentMigrationFileEntry = (DeleteDocumentMigrationFileEntry) baseMigrationFileEntry;
                return new DeleteDocumentMigration(
                        deleteDocumentMigrationFileEntry.getIndex(),
                        deleteDocumentMigrationFileEntry.getId()
                );
            case UPDATE_DOCUMENT:
                final UpdateDocumentMigrationFileEntry updateDocumentMigrationFileEntry = (UpdateDocumentMigrationFileEntry) baseMigrationFileEntry;
                return new UpdateDocumentMigration(
                        updateDocumentMigrationFileEntry.getIndex(),
                        updateDocumentMigrationFileEntry.getId(),
                        updateDocumentMigrationFileEntry.getDefinition()
                );
            case ALIASES:
                final AliasesMigrationFileEntry aliasesMigrationFileEntry = (AliasesMigrationFileEntry) baseMigrationFileEntry;
                return new AliasesMigration(
                        aliasesMigrationFileEntry.getDefinition()
                );
            case CREATE_INGEST_PIPELINE:
                final CreateIngestPipelineMigrationFileEntry createIngestPipelineMigrationFileEntry = (CreateIngestPipelineMigrationFileEntry) baseMigrationFileEntry;
                return new CreateIngestPipelineMigration(
                        createIngestPipelineMigrationFileEntry.getId(),
                        createIngestPipelineMigrationFileEntry.getDefinition()
                );
            case DELETE_INGEST_PIPELINE:
                final DeleteIngestPipelineMigrationFileEntry deleteIngestPipelineMigrationFileEntry = (DeleteIngestPipelineMigrationFileEntry) baseMigrationFileEntry;
                return new DeleteIngestPipelineMigration(
                        deleteIngestPipelineMigrationFileEntry.getId()
                );
            case REINDEX:
                final ReindexMigrationFileEntry reindexMigrationFileEntry = (ReindexMigrationFileEntry) baseMigrationFileEntry;
                return new ReindexMigration(
                        reindexMigrationFileEntry.getDefinition()
                );
            case UPDATE_INDEX_SETTINGS:
                final UpdateIndexSettingsMigrationFileEntry updateIndexSettingsMigrationFileEntry = (UpdateIndexSettingsMigrationFileEntry) baseMigrationFileEntry;
                return new UpdateIndexSettingsMigration(
                        updateIndexSettingsMigrationFileEntry.getIndex(),
                        updateIndexSettingsMigrationFileEntry.getDefinition()
                );
            default:
                throw new IllegalStateException("Unknown migration type " + baseMigrationFileEntry.getType());
        }
    }
}
