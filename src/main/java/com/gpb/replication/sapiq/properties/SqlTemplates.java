package com.gpb.replication.sapiq.properties;

import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component("sqlTemplates")
public class SqlTemplates {

    /*
     * SAP IQ подключается к конкретной БД.
     * DB_NAME() без параметра возвращает имя текущей базы.
     */
    String databaseSql = """
            SELECT DB_ID() AS oid, DB_NAME() AS datname
            FROM iq_dummy;
            """;

    /*
     * В SAP IQ schema фактически соответствует owner/user.
     * Берём только владельцев, у которых есть пользовательские таблицы/представления.
     */
    String schemaSql = """
            SELECT DISTINCT
                u.user_id AS oid,
                TRIM(u.user_name) AS schema_name
            FROM SYS.SYSUSER u
            JOIN SYS.SYSTAB t ON t.creator = u.user_id
            WHERE t.table_type IN (1, 2, 21)
              AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
            ORDER BY TRIM(u.user_name);
            """;

    /*
     * table_type:
     *   1  = BASE
     *   2  = MAT VIEW
     *   21 = VIEW
     *
     * table_structure возвращается как LONG VARCHAR с JSON.
     * В Java его можно парсить так же, как JSONB из PostgreSQL.
     */
    String tableSql = """
            WITH base_tables AS (
                SELECT
                    t.table_id,
                    t.object_id,
                    TRIM(u.user_name) AS schema_name,
                    TRIM(t.table_name) AS table_name,
                    t.table_type,
                    t.table_type_str
                FROM SYS.SYSTAB t
                JOIN SYS.SYSUSER u ON u.user_id = t.creator
                WHERE t.table_type IN (1, 2, 21)
                  AND TRIM(u.user_name) NOT IN ('SYS', 'dbo', 'DBA')
            ),

            column_json AS (
                SELECT
                    c.table_id,
                    '[' ||
                    LIST(
                        '{' ||
                            '"ordinalPosition":' || CAST(c.column_id AS VARCHAR(20)) || ',' ||
                            '"fqn":"' ||
                                REPLACE(REPLACE(DB_NAME(), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '.' ||
                                REPLACE(REPLACE(bt.schema_name, '\\\\', '\\\\\\\\'), '"', '\\\\"') || '.' ||
                                REPLACE(REPLACE(bt.table_name, '\\\\', '\\\\\\\\'), '"', '\\\\"') || '.' ||
                                REPLACE(REPLACE(TRIM(c.column_name), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '",' ||
                            '"name":"' ||
                                REPLACE(REPLACE(TRIM(c.column_name), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '",' ||
                            '"dataType":"' ||
                                REPLACE(UPPER(TRIM(d.domain_name)), ' ', '_') || '",' ||
                            '"dataTypeDisplay":"' ||
                                REPLACE(REPLACE(TRIM(c.base_type_str), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '",' ||
                            '"dataLength":' ||
                                CASE
                                    WHEN d.domain_name IN ('char', 'varchar', 'long varchar', 'binary', 'varbinary', 'long binary')
                                        THEN CAST(c.width AS VARCHAR(20))
                                    WHEN d.domain_name IN ('numeric', 'decimal')
                                        THEN CAST(c.width AS VARCHAR(20))
                                    ELSE 'null'
                                END || ',' ||
                            '"constraint":' ||
                                CASE
                                    WHEN c.nulls = 'N' THEN '"NOT_NULL"'
                                    ELSE 'null'
                                END || ',' ||
                            '"description":' ||
                                CASE
                                    WHEN r.remarks IS NULL THEN 'null'
                                    ELSE '"' || REPLACE(REPLACE(TRIM(CAST(r.remarks AS LONG VARCHAR)), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '"'
                                END ||
                        '}',
                        ',' ORDER BY c.column_id
                    )
                    || ']' AS columns_json
                FROM SYS.SYSTABCOL c
                JOIN base_tables bt ON bt.table_id = c.table_id
                JOIN SYS.SYSDOMAIN d ON d.domain_id = c.domain_id
                LEFT JOIN SYS.SYSREMARK r ON r.object_id = c.object_id
                GROUP BY c.table_id
            ),

            index_constraints_json AS (
                SELECT
                    i.table_id,
                    '[' ||
                    LIST(
                        '{' ||
                            '"columns":[' ||
                                (
                                    SELECT LIST(
                                        '"' || REPLACE(REPLACE(TRIM(c2.column_name), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '"',
                                        ',' ORDER BY ic2.sequence
                                    )
                                    FROM SYS.SYSIDXCOL ic2
                                    JOIN SYS.SYSTABCOL c2
                                      ON c2.table_id = ic2.table_id
                                     AND c2.column_id = ic2.column_id
                                    WHERE ic2.table_id = i.table_id
                                      AND ic2.index_id = i.index_id
                                ) ||
                            '],' ||
                            '"constraintType":"' ||
                                CASE
                                    WHEN i.index_category = 1 THEN 'PRIMARY_KEY'
                                    WHEN i.index_category = 2 THEN 'FOREIGN_KEY'
                                    WHEN i.index_category = 3 AND i."unique" IN (1, 2, 5) THEN 'UNIQUE'
                                    ELSE 'OTHER'
                                END || '"' ||
                        '}',
                        ',' ORDER BY i.index_id
                    )
                    || ']' AS constraints_json
                FROM SYS.SYSIDX i
                JOIN base_tables bt ON bt.table_id = i.table_id
                WHERE i.index_category IN (1, 2, 3)
                  AND (
                        i.index_category IN (1, 2)
                        OR i."unique" IN (1, 2, 5)
                      )
                GROUP BY i.table_id
            ),

            check_constraints_json AS (
                SELECT
                    bt.table_id,
                    '[' ||
                    LIST(
                        '{' ||
                            '"columns":[],' ||
                            '"constraintType":"CHECK",' ||
                            '"definition":' ||
                                CASE
                                    WHEN chk.check_defn IS NULL THEN 'null'
                                    ELSE '"' || REPLACE(REPLACE(TRIM(CAST(chk.check_defn AS LONG VARCHAR)), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '"'
                                END ||
                        '}',
                        ',' ORDER BY sc.constraint_id
                    )
                    || ']' AS checks_json
                FROM base_tables bt
                JOIN SYS.SYSCONSTRAINT sc
                  ON sc.table_object_id = bt.object_id
                 AND sc.constraint_type IN ('C', 'T')
                LEFT JOIN SYS.SYSCHECK chk
                  ON chk.check_id = sc.constraint_id
                GROUP BY bt.table_id
            )

            SELECT
                bt.object_id AS oid,
                bt.schema_name,
                bt.table_name,
                CAST(r.remarks AS LONG VARCHAR) AS description,
                '{' ||
                    '"tableType":"' ||
                        CASE
                            WHEN bt.table_type = 1 THEN 'REGULAR'
                            WHEN bt.table_type = 2 THEN 'MATERIALIZED_VIEW'
                            WHEN bt.table_type = 21 THEN 'VIEW'
                            ELSE 'OTHER'
                        END || '",' ||

                    '"viewDefinition":' ||
                        CASE
                            WHEN bt.table_type IN (2, 21) AND src.source IS NOT NULL
                                THEN '"' || REPLACE(REPLACE(TRIM(CAST(src.source AS LONG VARCHAR)), '\\\\', '\\\\\\\\'), '"', '\\\\"') || '"'
                            ELSE 'null'
                        END || ',' ||

                    '"columns":' ||
                        COALESCE(cj.columns_json, '[]') || ',' ||

                    '"tableConstraints":' ||
                        CASE
                            WHEN icj.constraints_json IS NULL AND ccj.checks_json IS NULL THEN '[]'
                            WHEN icj.constraints_json IS NOT NULL AND ccj.checks_json IS NULL THEN icj.constraints_json
                            WHEN icj.constraints_json IS NULL AND ccj.checks_json IS NOT NULL THEN ccj.checks_json
                            ELSE
                                '[' ||
                                SUBSTRING(icj.constraints_json, 2, LENGTH(icj.constraints_json) - 2) ||
                                ',' ||
                                SUBSTRING(ccj.checks_json, 2, LENGTH(ccj.checks_json) - 2) ||
                                ']'
                        END ||
                '}' AS table_structure
            FROM base_tables bt
            LEFT JOIN SYS.SYSREMARK r ON r.object_id = bt.object_id
            LEFT JOIN SYS.SYSSOURCE src ON src.object_id = bt.object_id
            LEFT JOIN column_json cj ON cj.table_id = bt.table_id
            LEFT JOIN index_constraints_json icj ON icj.table_id = bt.table_id
            LEFT JOIN check_constraints_json ccj ON ccj.table_id = bt.table_id
            ORDER BY bt.schema_name, bt.table_name;
            """;
}