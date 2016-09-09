/*
 * Copyright 2016, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package com.yahoo.elide.core.filter;

import com.yahoo.elide.core.exceptions.InvalidPredicateException;
import com.yahoo.elide.core.filter.Predicate.PathElement;
import com.yahoo.elide.core.filter.expression.AndFilterExpression;
import com.yahoo.elide.core.filter.expression.FilterExpression;
import com.yahoo.elide.core.filter.expression.NotFilterExpression;
import com.yahoo.elide.core.filter.expression.OrFilterExpression;
import com.yahoo.elide.core.filter.expression.Visitor;

import java.util.Set;

/**
 * FilterOperation that creates Hibernate query language fragments.
 */
public class HQLFilterOperation implements FilterOperation<String> {
    @Override
    public String apply(Predicate predicate) {
        StringBuilder fieldPathBuilder = new StringBuilder();
        for (PathElement pathElement: predicate.getPath()) {
            if (fieldPathBuilder.length() != 0) {
                fieldPathBuilder.append('.');
            }
            fieldPathBuilder.append(pathElement.getFieldName());
        }
        String fieldPath = fieldPathBuilder.toString();
        switch (predicate.getOperator()) {
            case IN:
                if (predicate.getValues().isEmpty()) {
                    return "(false)";
                }
                return String.format("%s IN (:%s)", fieldPath, fieldPath.replace('.', '_'));
            case NOT:
                if (predicate.getValues().isEmpty()) {
                    return "(true)";
                }
                return String.format("%s NOT IN (:%s)", fieldPath, fieldPath.replace('.', '_'));
            case PREFIX:
                return String.format("%s LIKE CONCAT(:%s, '%%')", fieldPath, fieldPath.replace('.', '_'));
            case POSTFIX:
                return String.format("%s LIKE CONCAT('%%', :%s)", fieldPath, fieldPath.replace('.', '_'));
            case INFIX:
                return String.format("%s LIKE CONCAT('%%', :%s, '%%')", fieldPath, fieldPath.replace('.', '_'));
            case ISNULL:
                return String.format("%s IS NULL", fieldPath);
            case NOTNULL:
                return String.format("%s IS NOT NULL", fieldPath);
            case LT:
                return String.format("%s < :%s", fieldPath, fieldPath.replace('.', '_'));
            case LE:
                return String.format("%s <= :%s", fieldPath, fieldPath.replace('.', '_'));
            case GT:
                return String.format("%s > :%s", fieldPath, fieldPath.replace('.', '_'));
            case GE:
                return String.format("%s >= :%s", fieldPath, fieldPath.replace('.', '_'));
            case TRUE:
                return String.format("(true)");
            case FALSE:
                return String.format("(false)");

            default:
                throw new InvalidPredicateException("Operator not implemented: " + predicate.getOperator());
        }
    }

    @Override
    public String applyAll(Set<Predicate> predicates) {
        StringBuilder filterString = new StringBuilder();

        for (Predicate predicate : predicates) {
            if (filterString.length() == 0) {
                filterString.append("WHERE ");
            } else {
                filterString.append(" AND ");
            }

            filterString.append(apply(predicate));
        }

        return filterString.toString();
    }

    public String apply(FilterExpression filterExpression) {
        HQLQueryVisitor visitor = new HQLQueryVisitor();
        return "WHERE " + filterExpression.accept(visitor);

    }

    /**
     * Filter expression visitor which builds an HQL query.
     */
    public class HQLQueryVisitor implements Visitor<String> {

        private String query;

        @Override
        public String visitPredicate(Predicate predicate) {
            query = apply(predicate);
            return query;
        }

        @Override
        public String visitAndExpression(AndFilterExpression expression) {
            String left = expression.getLeft().accept(this);
            String right = expression.getRight().accept(this);
            query = "(" + left + " AND " + right + ")";
            return query;
        }

        @Override
        public String visitOrExpression(OrFilterExpression expression) {
            String left = expression.getLeft().accept(this);
            String right = expression.getRight().accept(this);
            query = "(" + left + " OR " + right + ")";
            return query;
        }

        @Override
        public String visitNotExpression(NotFilterExpression expression) {
            String negated = expression.getNegated().accept(this);
            query = "NOT (" + negated + ")";
            return query;
        }
    }
}
