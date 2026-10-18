package tsh.d94.app.server.apService.cira.cql;

import java.util.List;

/**
 * CQL AST 노드 sealed 계층.
 */
public sealed interface CqlNode
	permits CqlNode.ComparisonNode, CqlNode.InNode, CqlNode.LogicalNode, CqlNode.NotNode {

	/**
	 * field op value  (=, !=, <, <=, >, >=, ~, !~)
	 */
	record ComparisonNode(String field, String operator, String value) implements CqlNode {
	}

	/**
	 * field IN (v1, v2, ...) 또는 field NOT IN (...)
	 */
	record InNode(String field, List<String> values, boolean negated) implements CqlNode {
	}

	/**
	 * left AND/OR right
	 */
	record LogicalNode(String operator, CqlNode left, CqlNode right) implements CqlNode {
	}

	/**
	 * NOT expr
	 */
	record NotNode(CqlNode expr) implements CqlNode {
	}
}
