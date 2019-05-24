/*
 * Copyright (c) 2019 "Neo4j,"
 * Neo4j Sweden AB [https://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.data.neo4j.repository.support;

import static lombok.AccessLevel.*;
import static org.springframework.data.neo4j.core.cypher.Cypher.*;
import static org.springframework.data.neo4j.repository.query.CypherAdapterUtils.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.LongSupplier;

import org.neo4j.driver.Record;
import org.neo4j.driver.types.TypeSystem;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.cypher.Functions;
import org.springframework.data.neo4j.core.cypher.Statement;
import org.springframework.data.neo4j.core.cypher.StatementBuilder;
import org.springframework.data.neo4j.core.cypher.StatementBuilder.BuildableMatch;
import org.springframework.data.neo4j.core.cypher.renderer.CypherRenderer;
import org.springframework.data.neo4j.core.cypher.renderer.Renderer;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.data.repository.support.PageableExecutionUtils;

/**
 * A fragment for repositories providing "Query by example" functionality.
 *
 * @param <T>
 * @author Michael J. Simons
 * @since 1.0
 */
@Slf4j
@RequiredArgsConstructor(access = PACKAGE)
class SimpleQueryByExampleExecutor<T> implements QueryByExampleExecutor<T> {

	private static final Renderer renderer = CypherRenderer.create();

	private final Neo4jClient neo4jClient;

	private final Neo4jMappingContext mappingContext;

	private final SchemaBasedStatementBuilder statementBuilder;

	@Override
	public <S extends T> Optional<S> findOne(Example<S> example) {

		Predicate predicate = Predicate.create(mappingContext, example);
		Statement statement = predicate.f(statementBuilder::prepareMatchOf)
			.returning(asterisk())
			.build();

		return createExecutableQuery(example.getProbeType(), statement, predicate.getParameters()).getSingleResult();
	}

	@Override
	public <S extends T> Iterable<S> findAll(Example<S> example) {

		Predicate predicate = Predicate.create(mappingContext, example);
		Statement statement = predicate.f(statementBuilder::prepareMatchOf)
			.returning(asterisk())
			.build();

		return createExecutableQuery(example.getProbeType(), statement, predicate.getParameters()).getResults();
	}

	@Override
	public <S extends T> Iterable<S> findAll(Example<S> example, Sort sort) {

		Predicate predicate = Predicate.create(mappingContext, example);
		Statement statement = predicate.f(statementBuilder::prepareMatchOf)
			.returning(asterisk())
			.orderBy(toSortItems(predicate.getNodeDescription(), sort)).build();

		return createExecutableQuery(example.getProbeType(), statement, predicate.getParameters()).getResults();
	}

	@Override
	public <S extends T> long count(Example<S> example) {

		Predicate predicate = Predicate.create(mappingContext, example);
		Statement statement = predicate.f(statementBuilder::prepareMatchOf)
			.returning(Functions.count(asterisk()))
			.build();

		PreparedQuery<Long> preparedQuery = PreparedQuery.queryFor(Long.class)
			.withCypherQuery(renderer.render(statement))
			.withParameters(predicate.getParameters())
			.build();
		return ExecutableQuery.create(this.neo4jClient, preparedQuery)
			.getRequiredSingleResult();
	}

	@Override
	public <S extends T> boolean exists(Example<S> example) {
		return findAll(example).iterator().hasNext();
	}

	@Override
	public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {

		Predicate predicate = Predicate.create(mappingContext, example);
		StatementBuilder.OngoingMatchAndReturn returning = predicate
			.f(statementBuilder::prepareMatchOf)
			.returning(asterisk());

		BuildableMatch returningWithPaging = addPagingParameter(predicate.getNodeDescription(), pageable, returning);

		Statement statement = returningWithPaging.build();

		List<S> page = createExecutableQuery(example.getProbeType(), statement, predicate.getParameters()).getResults();
		LongSupplier totalCountSupplier = () -> this.count(example);
		return PageableExecutionUtils.getPage(page, pageable, totalCountSupplier);
	}

	private <S extends T> ExecutableQuery<S> createExecutableQuery(Class<S> resultType, Statement statement,
		Map<String, Object> parameters) {

		BiFunction<TypeSystem, Record, ?> mappingFunctionToUse
			= this.mappingContext.getMappingFunctionFor(resultType).orElse(null);

		PreparedQuery<S> preparedQuery = PreparedQuery.queryFor(resultType)
			.withCypherQuery(renderer.render(statement))
			.withParameters(parameters)
			.usingMappingFunction(mappingFunctionToUse)
			.build();
		return ExecutableQuery.create(this.neo4jClient, preparedQuery);
	}
}