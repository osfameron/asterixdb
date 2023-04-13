/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.asterix.translator.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.asterix.common.config.DatasetConfig.IndexType;
import org.apache.asterix.common.exceptions.CompilationException;
import org.apache.asterix.common.exceptions.ErrorCode;
import org.apache.asterix.lang.common.expression.TypeExpression;
import org.apache.asterix.lang.common.expression.TypeReferenceExpression;
import org.apache.asterix.lang.common.statement.CreateViewStatement;
import org.apache.asterix.metadata.entities.BuiltinTypeMap;
import org.apache.asterix.metadata.entities.Index;
import org.apache.asterix.metadata.utils.KeyFieldTypeUtil;
import org.apache.asterix.om.typecomputer.impl.TypeComputeUtils;
import org.apache.asterix.om.types.ARecordType;
import org.apache.asterix.om.types.ATypeTag;
import org.apache.asterix.om.types.IAType;
import org.apache.asterix.om.utils.RecordUtil;
import org.apache.hyracks.algebricks.common.exceptions.AlgebricksException;
import org.apache.hyracks.api.exceptions.SourceLocation;
import org.apache.hyracks.util.LogRedactionUtil;

/**
 * A util that can verify if a filter field, a list of partitioning expressions,
 * or a list of key fields are valid in a record type.
 */
public class ValidateUtil {

    private static final String PRIMARY = "primary";

    private ValidateUtil() {
    }

    /**
     * Validates the field that will be used as filter for the components of an LSM index.
     *
     * @param recordType
     *            the record type
     * @param metaType
     *            the meta record type
     * @param filterSourceIndicator
     *            indicates where the filter attribute comes from, 0 for record, 1 for meta record.
     *            since this method is called only when a filter field presents, filterSourceIndicator will not be null
     *
     * @param filterField
     *            the full name of the field
     * @param sourceLoc
     * @throws AlgebricksException
     *             if field is not found in record.
     *             if field type can't be a filter type.
     *             if field type is nullable.
     */
    public static void validateFilterField(ARecordType recordType, ARecordType metaType, Integer filterSourceIndicator,
            List<String> filterField, SourceLocation sourceLoc) throws AlgebricksException {
        ARecordType itemType = filterSourceIndicator == 0 ? recordType : metaType;
        IAType fieldType = itemType.getSubFieldType(filterField);
        if (fieldType == null) {
            throw new CompilationException(ErrorCode.COMPILATION_FIELD_NOT_FOUND, sourceLoc,
                    LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(filterField)));
        }
        switch (fieldType.getTypeTag()) {
            case TINYINT:
            case SMALLINT:
            case INTEGER:
            case BIGINT:
            case FLOAT:
            case DOUBLE:
            case STRING:
            case BINARY:
            case DATE:
            case TIME:
            case DATETIME:
            case UUID:
            case YEARMONTHDURATION:
            case DAYTIMEDURATION:
                break;
            case UNION:
                throw new CompilationException(ErrorCode.COMPILATION_FILTER_CANNOT_BE_NULLABLE,
                        LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(filterField)));
            default:
                throw new CompilationException(ErrorCode.COMPILATION_ILLEGAL_FILTER_TYPE,
                        fieldType.getTypeTag().name());
        }
    }

    /**
     * Validates the partitioning expression that will be used to partition a dataset and returns expression type.
     *
     * @param recType
     *            the record type
     * @param metaRecType
     *            the meta record type
     * @param partitioningExprs
     *            a list of partitioning expressions that will be validated
     * @param keySourceIndicators
     *            the key sources (record vs. meta)
     * @param autogenerated
     *            true if auto generated, false otherwise
     * @param sourceLoc
     * @return a list of partitioning expressions types
     * @throws AlgebricksException
     *             if composite key is autogenerated.
     *             if autogenerated and of a type that can't be autogenerated.
     *             if a field could not be found in its record type.
     *             if partitioning key is nullable.
     *             if the field type can't be a primary key.
     */
    public static List<IAType> validatePartitioningExpressions(ARecordType recType, ARecordType metaRecType,
            List<List<String>> partitioningExprs, List<Integer> keySourceIndicators, boolean autogenerated,
            SourceLocation sourceLoc) throws AlgebricksException {
        return validatePartitioningExpressionsImpl(recType, metaRecType, partitioningExprs, keySourceIndicators,
                autogenerated, true, sourceLoc, null);
    }

    public static List<IAType> validatePartitioningExpressions(ARecordType recType, ARecordType metaRecType,
            List<List<String>> partitioningExprs, List<Integer> keySourceIndicators, boolean autogenerated,
            SourceLocation sourceLoc, List<TypeExpression> partitioningExprTypes) throws AlgebricksException {
        return validatePartitioningExpressionsImpl(recType, metaRecType, partitioningExprs, keySourceIndicators,
                autogenerated, true, sourceLoc, partitioningExprTypes);
    }

    private static List<IAType> validatePartitioningExpressionsImpl(ARecordType recType, ARecordType metaRecType,
            List<List<String>> partitioningExprs, List<Integer> keySourceIndicators, boolean autogenerated,
            boolean forPrimaryKey, SourceLocation sourceLoc, List<TypeExpression> partitioningExprTypes)
            throws AlgebricksException {
        String keyKindDisplayName = forPrimaryKey ? PRIMARY : "";
        List<IAType> computedPartitioningExprTypes = new ArrayList<>(partitioningExprs.size());
        if (autogenerated) {
            if (partitioningExprs.size() > 1) {
                throw new CompilationException(ErrorCode.COMPILATION_CANNOT_AUTOGENERATE_COMPOSITE_KEY, sourceLoc,
                        keyKindDisplayName);
            }
            List<String> fieldName = partitioningExprs.get(0);
            IAType fieldType = recType.getSubFieldType(fieldName);
            if (fieldType == null) {
                if (partitioningExprTypes != null && partitioningExprTypes.size() > 0) {
                    String typeName =
                            ((TypeReferenceExpression) partitioningExprTypes.get(0)).getIdent().second.getValue();
                    fieldType = BuiltinTypeMap.getBuiltinType(typeName);
                } else {
                    String unTypeField = fieldName.get(0) == null ? "" : fieldName.get(0);
                    throw new CompilationException(ErrorCode.COMPILATION_FIELD_NOT_FOUND, sourceLoc,
                            LogRedactionUtil.userData(unTypeField));
                }
            }
            computedPartitioningExprTypes.add(fieldType);
            ATypeTag pkTypeTag = fieldType.getTypeTag();
            if (pkTypeTag != ATypeTag.UUID) {
                throw new CompilationException(ErrorCode.COMPILATION_ILLEGAL_AUTOGENERATED_TYPE, sourceLoc,
                        keyKindDisplayName, pkTypeTag.name(), ATypeTag.UUID.name());
            }
        } else {
            if (partitioningExprTypes == null) {
                computedPartitioningExprTypes =
                        KeyFieldTypeUtil.getKeyTypes(recType, metaRecType, partitioningExprs, keySourceIndicators);
            }
            for (int i = 0; i < partitioningExprs.size(); i++) {
                List<String> partitioningExpr = partitioningExprs.get(i);
                IAType fieldType;
                if (partitioningExprTypes != null) {
                    String typeName =
                            ((TypeReferenceExpression) partitioningExprTypes.get(i)).getIdent().second.getValue();
                    fieldType = BuiltinTypeMap.getBuiltinType(typeName);
                    computedPartitioningExprTypes.add(fieldType);
                } else {
                    fieldType = computedPartitioningExprTypes.get(i);
                    if (fieldType == null) {
                        throw new CompilationException(ErrorCode.COMPILATION_FIELD_NOT_FOUND, sourceLoc,
                                LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(partitioningExpr)));
                    }
                    if (forPrimaryKey) {
                        boolean nullable = KeyFieldTypeUtil.chooseSource(keySourceIndicators, i, recType, metaRecType)
                                .isSubFieldNullable(partitioningExpr);
                        if (nullable) {
                            // key field is nullable
                            throw new CompilationException(ErrorCode.COMPILATION_KEY_CANNOT_BE_NULLABLE, sourceLoc,
                                    keyKindDisplayName,
                                    LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(partitioningExpr)));
                        }
                    } else {
                        fieldType = TypeComputeUtils.getActualType(fieldType);
                    }
                }
                switch (fieldType.getTypeTag()) {
                    case TINYINT:
                    case SMALLINT:
                    case INTEGER:
                    case BIGINT:
                    case FLOAT:
                    case DOUBLE:
                    case STRING:
                    case BINARY:
                    case DATE:
                    case TIME:
                    case UUID:
                    case DATETIME:
                    case YEARMONTHDURATION:
                    case DAYTIMEDURATION:
                        break;
                    case UNION:
                        throw new CompilationException(ErrorCode.COMPILATION_KEY_CANNOT_BE_NULLABLE, sourceLoc,
                                keyKindDisplayName,
                                LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(partitioningExpr)));
                    default:
                        throw new CompilationException(ErrorCode.COMPILATION_ILLEGAL_KEY_TYPE, sourceLoc,
                                fieldType.getTypeTag(), keyKindDisplayName);
                }
            }
        }
        return computedPartitioningExprTypes;
    }

    /**
     * Validates the key fields that will be used as keys of an index.
     *
     * @param indexType
     *            the type of the index that its key fields is being validated
     * @param fieldType
     *            a key field type
     * @param displayFieldName
     *            a field name to use for error reporting
     * @param sourceLoc
     *            the source location
     * @throws AlgebricksException
     */
    public static void validateIndexFieldType(IndexType indexType, IAType fieldType, List<String> displayFieldName,
            SourceLocation sourceLoc) throws AlgebricksException {
        switch (indexType) {
            case ARRAY:
            case BTREE:
                switch (fieldType.getTypeTag()) {
                    case TINYINT:
                    case SMALLINT:
                    case INTEGER:
                    case BIGINT:
                    case FLOAT:
                    case DOUBLE:
                    case STRING:
                    case BINARY:
                    case DATE:
                    case TIME:
                    case DATETIME:
                    case UUID:
                    case YEARMONTHDURATION:
                    case DAYTIMEDURATION:
                        break;
                    default:
                        throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                                "The field '"
                                        + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                        + "' which is of type " + fieldType.getTypeTag()
                                        + " cannot be indexed using the BTree index.");
                }
                break;
            case RTREE:
                switch (fieldType.getTypeTag()) {
                    case POINT:
                    case LINE:
                    case RECTANGLE:
                    case CIRCLE:
                    case POLYGON:
                    case GEOMETRY:
                        break;
                    default:
                        throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                                "The field '"
                                        + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                        + "' which is of type " + fieldType.getTypeTag()
                                        + " cannot be indexed using the RTree index.");
                }
                break;
            case LENGTH_PARTITIONED_NGRAM_INVIX:
                if (fieldType.getTypeTag() != ATypeTag.STRING) {
                    throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                            "The field '" + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                    + "' which is of type " + fieldType.getTypeTag()
                                    + " cannot be indexed using the Length Partitioned N-Gram index.");
                }
                break;
            case LENGTH_PARTITIONED_WORD_INVIX:
                switch (fieldType.getTypeTag()) {
                    case STRING:
                    case MULTISET:
                    case ARRAY:
                        break;
                    default:
                        throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                                "The field '"
                                        + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                        + "' which is of type " + fieldType.getTypeTag()
                                        + " cannot be indexed using the Length Partitioned Keyword index.");
                }
                break;
            case SINGLE_PARTITION_NGRAM_INVIX:
                if (fieldType.getTypeTag() != ATypeTag.STRING) {
                    throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                            "The field '" + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                    + "' which is of type " + fieldType.getTypeTag()
                                    + " cannot be indexed using the N-Gram index.");
                }
                break;
            case SINGLE_PARTITION_WORD_INVIX:
                switch (fieldType.getTypeTag()) {
                    case STRING:
                    case MULTISET:
                    case ARRAY:
                        break;
                    default:
                        throw new CompilationException(ErrorCode.COMPILATION_ERROR, sourceLoc,
                                "The field '"
                                        + LogRedactionUtil.userData(RecordUtil.toFullyQualifiedName(displayFieldName))
                                        + "' which is of type " + fieldType.getTypeTag()
                                        + " cannot be indexed using the Keyword index.");
                }
                break;
            default:
                throw new CompilationException(ErrorCode.COMPILATION_UNKNOWN_INDEX_TYPE, sourceLoc,
                        String.valueOf(indexType));
        }
    }

    /**
     * Validates the key fields that will be used as either primary or foreign keys of a view.
     */
    public static List<String> validateViewKeyFields(CreateViewStatement.KeyDecl keyDecl, ARecordType itemType,
            boolean isForeignKey, SourceLocation sourceLoc) throws AlgebricksException {
        List<Integer> sourceIndicators = keyDecl.getSourceIndicators();
        List<List<String>> fields = keyDecl.getFields();
        int n = fields.size();
        List<String> keyFields = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (sourceIndicators.get(i) != Index.RECORD_INDICATOR) {
                throw new CompilationException(isForeignKey ? ErrorCode.INVALID_FOREIGN_KEY_DEFINITION
                        : ErrorCode.INVALID_PRIMARY_KEY_DEFINITION, sourceLoc);
            }
            List<String> nestedField = fields.get(i);
            if (nestedField.size() != 1) {
                throw new CompilationException(isForeignKey ? ErrorCode.INVALID_FOREIGN_KEY_DEFINITION
                        : ErrorCode.INVALID_PRIMARY_KEY_DEFINITION, sourceLoc);
            }
            keyFields.add(nestedField.get(0));
        }

        validatePartitioningExpressionsImpl(itemType, null,
                keyFields.stream().map(Collections::singletonList).collect(Collectors.toList()),
                Collections.nCopies(keyFields.size(), Index.RECORD_INDICATOR), false, !isForeignKey, sourceLoc, null);

        return keyFields;
    }
}
