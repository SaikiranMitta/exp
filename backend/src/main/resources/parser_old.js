/* eslint-disable no-undef */
/* eslint-disable object-shorthand */
/* eslint-disable no-unused-vars */
/* eslint-disable func-names */

var module = (function (parser) {
    /**
     * Replaces projections from a query's select statement with the values provided
     * Currently will only work for main query and not sub-queries
     * @param {Object} ast - The ast object
     */
    replaceProjections = function (ast, replacementObject) {
        var astToReplace = Object.assign({}, ast);
        // Replace root select projection with replacementObject
        astToReplace.value.selectItems.value = replacementObject;
        astToReplace.value.orderBy = null
        return astToReplace;
    };
    var orgs = []

    function getInExpressionListPredicate(identifier, values) {
        return {
            "type": "InExpressionListPredicate",
            "hasNot": null,
            "left": {
                "type": "Identifier",
                "value": identifier
            },
            "right": {
                "type": "ExpressionList",
                "value": values.map(item => { return { "type": "String", "value": "'" + item + "'" } })
            }
        }
    }

    function getAndExpresstion(lhs, rhs) {
        if (lhs && rhs) {
            return {
                "type": "AndExpression",
                "operator": "and",
                "left": lhs,
                "right": rhs
            }
        } else if (lhs && !rhs) {
            return lhs
        } else if (rhs && !lhs) {
            return rhs
        }
    }
    function collect_organizations(ast, org_list) {
        if (!ast) return ast
        if (ast && ast.type == 'Select') {
            org_list = org_list.concat(collect_organizations(ast.where, []))
        } else if (ast.type == 'AndExpression') {
            org_list = org_list.concat(collect_organizations(ast.left, []))
            org_list = org_list.concat(collect_organizations(ast.right, []))
        } else if (ast.type == 'InSubQueryPredicate') {
            org_list = org_list.concat(collect_organizations(ast.right, []))
        } else if (ast.type == 'ComparisonBooleanPrimary' && ast.left.value == 'organization') {
            org_list = [ast.right.value.substring(1, ast.right.value.length - 1)]
        } else if (ast.type == 'InExpressionListPredicate' && ast.left.value == 'organization') {
            org_list = ast.right.value.map(item => item.value.substring(1, item.value.length - 1))
        }
        return org_list
    }

    function recursivelyAddOrganizations(ast, organizations) {
        if (!ast) return ast
        if (ast && ast.type == 'Select') {
            ast.where = recursivelyAddOrganizations(ast.where, organizations)
            var rhs = getInExpressionListPredicate("organization", organizations)
            var lhs = ast.where
            ast.where = getAndExpresstion(lhs, rhs)
            return ast;
        } else if (ast.type == 'AndExpression') {
            ast.right = recursivelyAddOrganizations(ast.right, organizations)
            ast.left = recursivelyAddOrganizations(ast.left, organizations)
        } else if (ast.type == 'InSubQueryPredicate') {
            ast.right = recursivelyAddOrganizations(ast.right, organizations)
        }
        return ast
    }

    function getStatsQueryAst(ast) {
        var wrapper_ast = {
            "nodeType": "Main",
            "value": {
                "type": "Select",
                "distinctOpt": null,
                "highPriorityOpt": null,
                "maxStateMentTimeOpt": null,
                "straightJoinOpt": null,
                "sqlSmallResultOpt": null,
                "sqlBigResultOpt": null,
                "sqlBufferResultOpt": null,
                "sqlCacheOpt": null,
                "sqlCalcFoundRowsOpt": null,
                "selectItems": {
                    "type": "SelectExpr",
                    "value": [
                        {
                            "type": "FunctionCall",
                            "name": "count",
                            "params": [
                                "*"
                            ],
                            "alias": "count",
                            "hasAs": true
                        }
                    ]
                },
                "from": {
                    "type": "TableRefrences",
                    "value": [
                        {
                            "type": "TableRefrence",
                            "value": {
                                "type": "SubQuery",
                                "value": ast.value,
                                "alias": null,
                                "hasAs": null
                            }
                        }
                    ]
                },
                "partition": null,
                "where": null,
                "groupBy": null,
                "having": null,
                "orderBy": null,
                "limit": null,
                "procedure": null,
                "updateLockMode": null
            }
        }

        return wrapper_ast
    }


    function getCountQueryFunct(ast) {
        return getStatsQueryAst(ast);
    }

    function getUpperBoundAST(identifier, value = 0) {
        if (value == 0) {
            return {
                "type": "ComparisonBooleanPrimary",
                "left": {
                    "type": "Identifier",
                    "value": identifier
                },
                "operator": "<",
                "right": {
                    "type": "FunctionCall",
                    "name": "now",
                    "params": [
                        null
                    ]
                }
            }
        } else {
            return {
                "type": "ComparisonBooleanPrimary",
                "left": {
                    "type": "Identifier",
                    "value": "timestamp"
                },
                "operator": "<=",
                "right": {
                    "type": "FunctionCall",
                    "name": "from_iso8601_timestamp",
                    "params": [
                        {
                            "type": "String",
                            "value": "'"+value+"'"
                        }
                    ]
                }
            }
        }

    }

    function getLowerBoundAST(identifier, value = 0) {
        return {
            "type": "ComparisonBooleanPrimary",
            "left": {
                "type": "Identifier",
                "value": identifier
            },
            "operator": ">",
            "right": {
                "type": "FunctionCall",
                "name": "date_add",
                "params": [
                    {
                        "type": "String",
                        "value": "'Day'"
                    },
                    {
                        "type": "Number",
                        "value": value
                    },
                    {
                        "type": "FunctionCall",
                        "name": "now",
                        "params": [
                            null
                        ]
                    }
                ]
            }
        }
    }

    function getBetweenPredicateAST(identifier, upperBoundDate, lowerBoundDate) {
        return {
            "type": "BetweenPredicate",
            "hasNot": null,
            "left": {
                "type": "Identifier",
                "value": "timestamp"
            },
            "right": {
                "left": {
                    "type": "FunctionCall",
                    "name": "date_add",
                    "params": [
                        {
                            "type": "String",
                            "value": "'Day'"
                        },
                        {
                            "type": "Number",
                            "value": lowerBoundDate
                        },
                        {
                            "type": "FunctionCall",
                            "name": "now",
                            "params": [
                                null
                            ]
                        }
                    ]
                },
                "right": {
                    "type": "FunctionCall",
                    "name": "from_iso8601_timestamp",
                    "params": [
                        {
                            "type": "String",
                            "value": "'" + upperBoundDate + "'"
                        }
                    ]
                }
            }
        }
    }

    function updateASTreeBy(tree, upperBoundDate, lowerBoundDate) {
        status = checkStatus(tree, {
            'has_upper_bound': false,
            'has_lower_bound': false,
            'has_both_bounds': false
        }, upperBoundDate, lowerBoundDate)
        var rhs = null;
        if (status["has_both_bounds"])
            return tree;
        else if (!status["has_both_bounds"] && !status["has_upper_bound"] && !status["has_lower_bound"])
            rhs = getBetweenPredicateAST("timestamp", upperBoundDate, lowerBoundDate)
        else if (!status["has_upper_bound"])
            rhs = getUpperBoundAST("timestamp", upperBoundDate)
        else if (!status["has_lower_bound"])
            rhs = getLowerBoundAST("timestamp", lowerBoundDate)
        var lhs = tree
        return getAndExpresstion(lhs, rhs)
    }


    function updateStatus(ast, status) {
        if (ast.type == 'ComparisonBooleanPrimary') {
            if (ast.operator.includes(">"))
                status['has_lower_bound'] = true
            else if (ast.operator.includes("<"))
                status['has_upper_bound'] = true
            else if (ast.operator == "=")
                status['has_both_bounds'] = true
        }
        else if (ast.type == 'BetweenPredicate')
            status['has_both_bounds'] = true
        return status;
    }

    function checkStatus(ast, status, upperBoundDate, lowerBoundDate) {
        if (!ast) return status
        if (ast && ast.type == 'AndExpression') {
            status = checkStatus(ast.left, status, upperBoundDate, lowerBoundDate)
            status = checkStatus(ast.right, status, upperBoundDate, lowerBoundDate)
        }
        else if (ast && (ast.type == 'ComparisonBooleanPrimary' || ast.type == 'BetweenPredicate') && ast.left.value == 'timestamp')
            status = updateStatus(ast, status)
        else if (ast && ast.type == 'Select') {
            ast.where = updateASTreeBy(ast.where, upperBoundDate, lowerBoundDate)
        } else if (ast && ast.type == 'OrExpression') {
            ast.left = updateASTreeBy(ast.left, upperBoundDate, lowerBoundDate)
            ast.right = updateASTreeBy(ast.right, upperBoundDate, lowerBoundDate)
        }
        //  for in sub query
        else if (ast && (ast.type == 'InSubQueryPredicate' || ast.type == 'SubQuery')) {
            ast.where = updateASTreeBy(ast.right, upperBoundDate, lowerBoundDate)
        }
        return status;
    }
    function getCategoryCountsQueryFunct(ast) {
        var updated_ast = getStatsQueryAst(ast)
        updated_ast.value.selectItems.value.push({
            "type": "Identifier",
            "value": "type",
            "alias": null,
            "hasAs": null
        })
        updated_ast.value.groupBy = {
            "type": "GroupBy",
            "value": [
                {
                    "type": "GroupByOrderByItem",
                    "value": {
                        "type": "Identifier",
                        "value": "type"
                    },
                    "sortOpt": null
                }
            ],
            "rollUp": null
        }
        return updated_ast;
    }


    function getDateComparisonQuery(ast) {

        if (ast.type == "ComparisonBooleanPrimary") {

            return {
                "type": "ComparisonBooleanPrimary",
                "left": {
                    "type": "Identifier",
                    "value": "date"
                },
                "operator": ast.operator,
                "right": {
                    "type": "FunctionCall",
                    "name": "to_iso8601",
                    "params": [{
                        "type": "FunctionCall",
                        "name": "date",
                        "params": [
                            ast.right
                        ]
                    }]
                }
            }
        }
    }
    function getDateBetweenQuery(ast) {
        if (ast.type == "BetweenPredicate") {
            return {
                "type": "BetweenPredicate",
                "hasNot": null,
                "left": {
                    "type": "Identifier",
                    "value": "date"
                },
                "right": {
                    "left": {
                        "type": "FunctionCall",
                        "name": "to_iso8601",
                        "params": [{
                            "type": "FunctionCall",
                            "name": "date",
                            "params": [
                                ast.right.left
                            ]
                        }]
                    },
                    "right": {
                        "type": "FunctionCall",
                        "name": "to_iso8601",
                        "params": [{
                            "type": "FunctionCall",
                            "name": "date",
                            "params": [
                                ast.right.right
                            ]
                        }]
                    }
                }
            }
        }
    }

    function getDateNode(ast) {
        var node = null
        if (ast.type == 'ComparisonBooleanPrimary') {
            node = getDateComparisonQuery(ast)
        }
        else if (ast.type == 'BetweenPredicate') {
            node = getDateBetweenQuery(ast)
        }

        if (node != null) {
            return ast = getAndExpresstion(ast, node)
        }
        return ast
    }

    function addDateQuery(ast) {

        if (!ast) return ast

        if (ast && ast.type == 'AndExpression') {
            ast.left = addDateQuery(ast.left)
            ast.right = addDateQuery(ast.right)
        }
        else if (ast && (ast.type == 'ComparisonBooleanPrimary' || ast.type == 'BetweenPredicate') && ast.left.value == 'timestamp') {

            ast = getDateNode(ast)

        } else if (ast && ast.type == 'Select') {
            ast.where = addDateQuery(ast.where)
        } else if (ast && ast.type == 'OrExpression') {
            ast.left = addDateQuery(ast.left)
            ast.right = checkDate(ast.right)
        }
        else if (ast && (ast.type == 'InSubQueryPredicate')) {
            ast.where = addDateQuery(ast.right)
        }

        return ast;
    }
    function queryReturnsField(ast, fieldName) {
        function check(field) {
            return ((field.value == "*") || (field.value == fieldName));
        }
        return ast.value.selectItems.value.some(check);
    }

    /**
      * Check for order by clause in ast if not present add order by timestamp desc
      * @param {Object} ast - The AST of the query
    */
    function checkAndAddOrderClauseQuery(ast){

      if(!ast) return ast

      if(ast && ast.type=="Select" && ast.orderBy==null){

          ast.orderBy = {
            "type": "OrderBy",
            "value": [
              {
                "type": "GroupByOrderByItem",
                "value": {
                  "type": "Identifier",
                  "value": "timestamp"
                },
                "sortOpt": "desc"
              }
            ],
            "rollUp": null
          }
      }
     return ast
    }

    return {
        getAST: function (query) {
            if (query) {
                return parser.parse(query);
            }
            return null;
        },

        queryReturnsTimestamp: function (ast) {
            return queryReturnsField(ast, 'timestamp');
        },

        queryReturnsCategory: function (ast) {
            return queryReturnsField(ast, 'type');
        },

        getCategoryCountsQuery: function (ast) {
            return getCategoryCountsQueryFunct(ast);
        },

        getCountQuery: function (ast) {
            return getCountQueryFunct(ast);
        },

        getOrganizations: function (ast) {
            return collect_organizations(ast.value, "")
        },

        addOrganizations: function (ast, organizations) {
            organizations = organizations.split(',')
            return {
                "nodeType": "Main",
                "value": recursivelyAddOrganizations(ast.value, organizations)
            };
        },

        getQuery: function (ast) {
            // TODO: Convert an AST string to query
            return parser.stringify(ast);
        },

        addTimestampBounds: function (ast, lowerBoundInDays, upperBoundDate) {
            checkStatus(ast.value, {}, upperBoundDate, lowerBoundInDays)
            var updatedQuery = parser.stringify(ast)
            return updatedQuery
        },
        addLogDate: function (ast) {
            ast.value = addDateQuery(ast.value)
            var updatedQuery = parser.stringify(ast)
            return updatedQuery
        },
         checkAndAddOrderClause: function (ast) {
            ast.value = checkAndAddOrderClauseQuery(ast.value)
            var updatedQuery = parser.stringify(ast)
            return updatedQuery
         },

        /**
         * Create the AST required for the histogram by specifying the number of bins, bin field and weight
         * @param {Object} ast - The AST of the query
         * @param {Number} numberOfBins - The number of binf for the histogram
         * @param {String} binField - The field that the bin will be created on
         * @param {Number} weight - The weight of the histogram
         */
        getHistogram: function (ast, numberOfBins, binField, weight) {
            // Create a projection object
            var projectionObject = {
                type: "FunctionCall",
                name: "numeric_histogram",
                params: [
                    { type: "Number", value: "0" },
                    {
                        type: "FunctionCall",
                        name: "to_unixtime",
                        params: [{ type: "Identifier", value: null }]
                    },
                    { type: "Number", value: "1" }
                ],
                alias: "histogram",
                hasAs: true
            };

            // Assign the numberOfBing
            projectionObject.params[0].value = numberOfBins;

            // Assign the binField
            projectionObject.params[1].params[0].value = binField;

            // Optional: Replace the weight
            if (weight) {
                projectionObject.params[2].value = weight;
            }

            // Replace the root select projection with the histogram select projection
            var mappedAST = replaceProjections(ast, [projectionObject]);

            return mappedAST;
        },

    };
})(sqlParser);